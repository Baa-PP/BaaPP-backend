package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

internal data class VerifiedRanchContext(
    val principalIdentifier: String,
    val ranchId: UUID,
    val access: RanchAccess,
)

@Component
class RanchContextInterceptor(
    private val ranchMembershipPort: RanchMembershipPort? = null,
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(RanchContextInterceptor::class.java)

    companion object {
        const val HEADER_ACTIVE_RANCH = "X-Active-Ranch-Id"
        private val activeRanchHolder = ThreadLocal<String?>()
        private val verifiedContextHolder = ThreadLocal<VerifiedRanchContext?>()

        fun currentRanchId(): String? = activeRanchHolder.get()

        internal fun currentVerifiedContext(): VerifiedRanchContext? = verifiedContextHolder.get()

        internal fun parseRanchId(rawRanchId: String): UUID =
            runCatching { UUID.fromString(rawRanchId) }.getOrElse {
                throw DomainValidationException(
                    code = "INVALID_RANCH_CONTEXT",
                    message = "Encabezado X-Active-Ranch-Id inválido.",
                )
            }

        internal fun missingRanchContextException(): DomainValidationException =
            DomainValidationException(
                code = "MISSING_RANCH_CONTEXT",
                message = "Encabezado X-Active-Ranch-Id requerido.",
            )

        internal fun ranchAccessDeniedException(): ForbiddenAccessException =
            ForbiddenAccessException(
                code = "RANCH_ACCESS_DENIED",
                message = "El usuario autenticado no tiene acceso al rancho activo.",
            )

        internal fun insufficientPermissionException(permission: RanchPermission): ForbiddenAccessException =
            ForbiddenAccessException(
                code = "INSUFFICIENT_PERMISSION",
                message = "No cuenta con el permiso '${permission.jsonKey}' para realizar esta operación en el rancho activo.",
            )

        internal fun extractAuthenticatedPrincipal(authentication: Authentication?): String? =
            authentication
                ?.takeUnless { it is AnonymousAuthenticationToken }
                ?.takeIf { it.isAuthenticated }
                ?.name
                ?.takeIf { it.isNotBlank() }

        private fun clearContext() {
            activeRanchHolder.remove()
            verifiedContextHolder.remove()
        }
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        clearContext()
        val rawRanchId = request.getHeader(HEADER_ACTIVE_RANCH)
        val principalIdentifier = resolveAuthenticatedPrincipal(request)
        val requiredPermission = resolveRequiredPermission(handler)

        if (principalIdentifier == null) {
            activeRanchHolder.set(rawRanchId)
            return true
        }

        if (rawRanchId == null) {
            if (requiredPermission != null) {
                throw missingRanchContextException()
            }
            return true
        }

        val ranchId = parseRanchId(rawRanchId)
        val membershipPort = ranchMembershipPort ?: run {
            log.error("RanchMembershipPort bean is not configured; denying access to ranchId={}", ranchId)
            throw ranchAccessDeniedException()
        }
        val access = membershipPort.resolveAccess(principalIdentifier, ranchId)
            ?: throw ranchAccessDeniedException()

        if (requiredPermission != null && !access.hasPermission(requiredPermission)) {
            throw insufficientPermissionException(requiredPermission)
        }

        activeRanchHolder.set(rawRanchId)
        verifiedContextHolder.set(VerifiedRanchContext(principalIdentifier, ranchId, access))
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        clearContext()
    }

    private fun resolveAuthenticatedPrincipal(request: HttpServletRequest): String? =
        extractAuthenticatedPrincipal(SecurityContextHolder.getContext().authentication)
            ?: extractAuthenticatedPrincipal(request.userPrincipal as? Authentication)

    private fun resolveRequiredPermission(handler: Any): RanchPermission? {
        val handlerMethod = handler as? HandlerMethod ?: return null
        val annotation = AnnotatedElementUtils.findMergedAnnotation(
            handlerMethod.method,
            RequireRanchPermission::class.java,
        ) ?: AnnotatedElementUtils.findMergedAnnotation(
            handlerMethod.beanType,
            RequireRanchPermission::class.java,
        )
        return annotation?.value
    }
}
