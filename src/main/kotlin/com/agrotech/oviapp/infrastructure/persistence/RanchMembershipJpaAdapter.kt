package com.agrotech.oviapp.infrastructure.persistence

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

    override fun hasAccess(principalIdentifier: String, ranchId: UUID): Boolean {
        val user = findUser(principalIdentifier) ?: return false
        if (!user.activo) return false
        val role = runCatching { Role.valueOf(user.rol) }.getOrNull() ?: return false

        return when (role) {
            Role.OWNER, Role.WORKER_BRANCH -> user.ranchoId == ranchId
            Role.VET_TECHNICIAN, Role.ASSOCIATION_SUPERUSER -> {
                user.ranchoId == ranchId ||
                    (user.asociacionId != null &&
                        asociacionRanchoRepository.existsByIdAsociacionIdAndIdRanchoId(user.asociacionId!!, ranchId))
            }
        }
    }

    private fun findUser(principalIdentifier: String): UsuarioEntity? =
        usuarioRepository.findByUsuario(principalIdentifier)
            ?: runCatching { UUID.fromString(principalIdentifier) }
                .getOrNull()
                ?.let { id -> usuarioRepository.findById(id).orElse(null) }
}
