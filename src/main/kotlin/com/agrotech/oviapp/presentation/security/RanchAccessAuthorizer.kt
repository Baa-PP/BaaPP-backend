package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.UUID

data class AuthorizedRanchContext(
    val ranchId: UUID,
    val principalName: String,
    val access: RanchAccess,
)

@Component
class RanchAccessAuthorizer(
    private val ranchMembershipPort: RanchMembershipPort,
) {
    fun requireAuthorizedRanchId(
        authentication: Authentication?,
        requiredPermission: RanchPermission? = null,
    ): UUID = requireAuthorizedAccess(authentication, requiredPermission).ranchId

    fun requireAuthorizedAccess(
        authentication: Authentication?,
        requiredPermission: RanchPermission? = null,
    ): AuthorizedRanchContext {
        val rawRanchId = RanchContextInterceptor.currentRanchId()
            ?: throw RanchContextInterceptor.missingRanchContextException()

        val ranchId = RanchContextInterceptor.parseRanchId(rawRanchId)

        val principalName = RanchContextInterceptor.extractAuthenticatedPrincipal(authentication)
            ?: throw RanchContextInterceptor.ranchAccessDeniedException()

        val verified = RanchContextInterceptor.currentVerifiedContext()
        val access = if (verified != null && verified.principalIdentifier == principalName && verified.ranchId == ranchId) {
            verified.access
        } else {
            ranchMembershipPort.resolveAccess(principalName, ranchId)
                ?: throw RanchContextInterceptor.ranchAccessDeniedException()
        }

        if (requiredPermission != null && !access.hasPermission(requiredPermission)) {
            throw RanchContextInterceptor.insufficientPermissionException(requiredPermission)
        }

        return AuthorizedRanchContext(
            ranchId = ranchId,
            principalName = principalName,
            access = access,
        )
    }
}
