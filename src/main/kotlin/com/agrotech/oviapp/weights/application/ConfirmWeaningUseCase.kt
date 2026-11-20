package com.agrotech.oviapp.weights.application

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.weights.domain.AnimalWeightingPort
import com.agrotech.oviapp.weights.domain.WeaningDestination
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.domain.WeightPolicy
import com.agrotech.oviapp.weights.domain.WeightRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ConfirmWeaningCommand(
    val ranchId: UUID,
    val animalId: UUID,
    val weaningDate: LocalDate,
    val weaningWeightKg: BigDecimal,
    val destination: WeaningDestination,
    val scrotalCircumferenceCm: BigDecimal? = null,
    val weightId: UUID? = null,
)

data class WeaningResult(
    val animalId: UUID,
    val weaningDate: LocalDate,
    val destination: WeaningDestination,
    val lifeStage: LifeStage,
    val animalStatus: AnimalStatus,
    val weaningWeight: WeightMeasurement,
    val motherTransitionedToEmpty: Boolean,
)

@Service
class ConfirmWeaningUseCase(
    private val animalPort: AnimalWeightingPort,
    private val weightPort: WeightRepositoryPort,
) {
    @Transactional
    fun execute(command: ConfirmWeaningCommand): WeaningResult {
        if (command.weightId != null && weightPort.existsById(command.weightId)) {
            throw BusinessConflictException(
                code = "WEIGHT_ID_ALREADY_EXISTS",
                message = "Ya existe un pesaje registrado con el identificador proporcionado.",
            )
        }

        // Serialize concurrent weaning requests per animal row
        val lamb = animalPort.findByIdAndRanchIdForUpdate(command.animalId, command.ranchId)
            ?: throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "Cría no encontrada en el rancho activo.")

        if (lamb.status != AnimalStatus.ACTIVE) {
            throw BusinessConflictException("ANIMAL_NOT_ACTIVE", "Solo se puede destetar un ejemplar en estado ACTIVE.")
        }
        if (lamb.weaningDate != null || weightPort.existsWeaningForAnimal(lamb.id, command.ranchId)) {
            throw BusinessConflictException("ANIMAL_ALREADY_WEANED", "El ejemplar ya cuenta con destete registrado.")
        }

        WeightPolicy.validateForAnimal(
            animalSex = lamb.sex,
            birthDate = lamb.birthDate,
            weightDate = command.weaningDate,
            scrotalCircumferenceCm = command.scrotalCircumferenceCm,
        )
        WeightPolicy.validateWeaningDestination(lamb.sex, command.destination)

        val weaningMeasurement = weightPort.create(
            WeightMeasurement(
                id = command.weightId ?: UUID.randomUUID(),
                animalId = lamb.id,
                ranchId = command.ranchId,
                milestone = WeightMilestone.WEANING,
                weightKg = command.weaningWeightKg,
                scrotalCircumferenceCm = command.scrotalCircumferenceCm,
                date = command.weaningDate,
            ),
        )

        val (updatedStage, updatedStatus) = when (command.destination) {
            WeaningDestination.LAMB_FATTENING -> LifeStage.LAMB_FATTENING to AnimalStatus.ACTIVE
            WeaningDestination.EWE_REPLACEMENT -> LifeStage.EWE_REPLACEMENT to AnimalStatus.ACTIVE
            WeaningDestination.SOLD -> lamb.lifeStage to AnimalStatus.SOLD
        }

        val updatedLamb = animalPort.save(
            lamb.copy(
                weaningDate = command.weaningDate,
                lifeStage = updatedStage,
                status = updatedStatus,
            ),
        )

        val motherTransitioned = lamb.damId?.let { damId ->
            val mother = animalPort.findByIdAndRanchIdForUpdate(damId, command.ranchId)
            if (mother != null && mother.reproductiveStatus == ReproductiveStatus.LACTATING) {
                animalPort.save(mother.copy(reproductiveStatus = ReproductiveStatus.EMPTY))
                true
            } else {
                false
            }
        } ?: false

        return WeaningResult(
            animalId = updatedLamb.id,
            weaningDate = command.weaningDate,
            destination = command.destination,
            lifeStage = updatedLamb.lifeStage,
            animalStatus = updatedLamb.status,
            weaningWeight = weaningMeasurement,
            motherTransitionedToEmpty = motherTransitioned,
        )
    }
}
