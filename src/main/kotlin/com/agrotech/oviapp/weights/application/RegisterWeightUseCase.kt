package com.agrotech.oviapp.weights.application

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.weights.domain.AnimalWeightingPort
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.domain.WeightPolicy
import com.agrotech.oviapp.weights.domain.WeightRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class RegisterWeightCommand(
    val id: UUID? = null,
    val ranchId: UUID,
    val animalId: UUID,
    val milestone: WeightMilestone,
    val weightKg: BigDecimal,
    val scrotalCircumferenceCm: BigDecimal? = null,
    val date: LocalDate,
)

@Service
class RegisterWeightUseCase(
    private val animalPort: AnimalWeightingPort,
    private val weightPort: WeightRepositoryPort,
) {
    @Transactional
    fun execute(command: RegisterWeightCommand): WeightMeasurement {
        val animal = animalPort.findByIdAndRanchId(command.animalId, command.ranchId)
            ?: throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo.")

        if (animal.status == AnimalStatus.DECEASED) {
            throw BusinessConflictException("ANIMAL_DECEASED", "No se pueden registrar pesajes a un ejemplar fallecido.")
        }

        WeightPolicy.validateForAnimal(
            animalSex = animal.sex,
            birthDate = animal.birthDate,
            weightDate = command.date,
            scrotalCircumferenceCm = command.scrotalCircumferenceCm,
        )

        val measurement = WeightMeasurement(
            id = command.id ?: UUID.randomUUID(),
            animalId = animal.id,
            ranchId = command.ranchId,
            milestone = command.milestone,
            weightKg = command.weightKg,
            scrotalCircumferenceCm = command.scrotalCircumferenceCm,
            date = command.date,
        )
        return weightPort.save(measurement)
    }
}
