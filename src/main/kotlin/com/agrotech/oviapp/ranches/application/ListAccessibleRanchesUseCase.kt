package com.agrotech.oviapp.ranches.application

import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.ranches.domain.RanchActorPort
import com.agrotech.oviapp.ranches.domain.RanchPolicy
import com.agrotech.oviapp.ranches.domain.RanchProfile
import com.agrotech.oviapp.ranches.domain.RanchRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListAccessibleRanchesUseCase(
    private val actorPort: RanchActorPort,
    private val ranchPort: RanchRepositoryPort,
) {
    @Transactional(readOnly = true)
    fun execute(principalIdentifier: String): List<RanchProfile> {
        val actor = RanchPolicy.requireActiveActor(actorPort.findByPrincipalIdentifier(principalIdentifier))
        return when (actor.role) {
            Role.OWNER, Role.WORKER_BRANCH -> listOfNotNull(actor.ranchId?.let { ranchPort.findById(it) })
            Role.VET_TECHNICIAN, Role.ASSOCIATION_SUPERUSER -> {
                val byAssociation = actor.associationId?.let { ranchPort.findByAssociationId(it) }.orEmpty()
                val direct = actor.ranchId?.let { ranchPort.findById(it) }
                (byAssociation + listOfNotNull(direct))
                    .distinctBy { it.id }
                    .sortedBy { it.name.lowercase() }
            }
        }
    }
}
