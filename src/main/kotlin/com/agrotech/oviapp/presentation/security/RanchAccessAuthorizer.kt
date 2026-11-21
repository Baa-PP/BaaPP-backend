package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RanchAccessAuthorizer(
    private val ranchMembershipPort: RanchMembershipPort,
) {
    fun requireAuthorizedRanchId(authentication: Authentication?): UUID {
        val rawRanchId = RanchContextInterceptor.currentRanchId()
            ?: throw DomainValidationException("MISSING_RANCH_CONTEXT", "Encabezado X-Active-Ranch-Id requerido.")

        val ranchId = runCatching { UUID.fromString(rawRanchId) }.getOrElse {
            throw DomainValidationException("INVALID_RANCH_CONTEXT", "Encabezado X-Active-Ranch-Id inválido.")
        }

        val principalName = authentication
            ?.takeIf { it.isAuthenticated }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: throw ForbiddenAccessException("RANCH_ACCESS_DENIED", "El usuario autenticado no tiene acceso al rancho activo.")

        if (!ranchMembershipPort.hasAccess(principalName, ranchId)) {
            throw ForbiddenAccessException("RANCH_ACCESS_DENIED", "El usuario autenticado no tiene acceso al rancho activo.")
        }

        return ranchId
    }
}
