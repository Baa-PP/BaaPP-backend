package com.agrotech.oviapp.ranches.application

import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.ranches.domain.RanchActorPort
import com.agrotech.oviapp.ranches.domain.RanchPolicy
import com.agrotech.oviapp.ranches.domain.RanchProfile
import com.agrotech.oviapp.ranches.domain.RanchRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class UpdateRanchCommand(
    val principalIdentifier: String,
    val ranchId: UUID,
    val name: String,
    val address: String,
    val owner: String,
    val weaningTargetDays: Int,
)

@Service
class UpdateRanchProfileUseCase(
    private val actorPort: RanchActorPort,
    private val ranchPort: RanchRepositoryPort,
) {
    @Transactional
    fun execute(command: UpdateRanchCommand): RanchProfile {
        val actor = RanchPolicy.requireActiveActor(actorPort.findByPrincipalIdentifier(command.principalIdentifier))
        val isAffiliated = actor.associationId?.let {
            ranchPort.isAffiliatedWithAssociation(it, command.ranchId)
        } ?: false
        RanchPolicy.requireCanEditRanch(actor, command.ranchId, isAffiliated)
        RanchPolicy.validateWeaningTargetDays(command.weaningTargetDays)

        val existing = ranchPort.findById(command.ranchId)
            ?: throw ResourceNotFoundException("RANCH_NOT_FOUND", "Rancho no encontrado.")

        return ranchPort.save(
            existing.copy(
                name = command.name.trim(),
                address = command.address.trim(),
                owner = command.owner.trim(),
                weaningTargetDays = command.weaningTargetDays,
            ),
        )
    }
}
