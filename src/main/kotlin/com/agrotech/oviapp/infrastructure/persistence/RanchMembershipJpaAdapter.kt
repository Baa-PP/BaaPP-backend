package com.agrotech.oviapp.infrastructure.persistence

import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.infrastructure.persistence.entity.AsociacionRanchoEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.AsociacionRanchoId
import com.agrotech.oviapp.infrastructure.persistence.entity.UsuarioEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface UsuarioJpaRepository : JpaRepository<UsuarioEntity, UUID> {
    fun findByUsuario(usuario: String): UsuarioEntity?
}

interface AsociacionRanchoJpaRepository : JpaRepository<AsociacionRanchoEntity, AsociacionRanchoId> {
    fun existsByIdAsociacionIdAndIdRanchoId(asociacionId: UUID, ranchoId: UUID): Boolean
}

@Component
class RanchMembershipJpaAdapter(
    private val usuarioRepository: UsuarioJpaRepository,
    private val asociacionRanchoRepository: AsociacionRanchoJpaRepository,
) : RanchMembershipPort {

    override fun hasAccess(principalIdentifier: String, ranchId: UUID): Boolean =
        resolveAccess(principalIdentifier, ranchId) != null

    override fun resolveAccess(principalIdentifier: String, ranchId: UUID): RanchAccess? {
        if (principalIdentifier.isBlank()) return null
        val user = findUser(principalIdentifier) ?: return null
        if (!user.activo) return null
        val role = runCatching { Role.valueOf(user.rol) }.getOrNull() ?: return null

        val allowed = when (role) {
            Role.OWNER, Role.WORKER_BRANCH -> user.ranchoId == ranchId
            Role.VET_TECHNICIAN, Role.ASSOCIATION_SUPERUSER -> {
                val asociacionId = user.asociacionId
                asociacionId != null &&
                    asociacionRanchoRepository.existsByIdAsociacionIdAndIdRanchoId(asociacionId, ranchId)
            }
        }

        return if (allowed) RanchAccess(role = role, permissions = user.permisos.toMap()) else null
    }

    private fun findUser(principalIdentifier: String): UsuarioEntity? =
        usuarioRepository.findByUsuario(principalIdentifier)
            ?: runCatching { UUID.fromString(principalIdentifier) }
                .getOrNull()
                ?.let { id -> usuarioRepository.findById(id).orElse(null) }
}
