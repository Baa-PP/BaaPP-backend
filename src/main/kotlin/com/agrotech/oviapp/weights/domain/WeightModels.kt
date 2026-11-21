package com.agrotech.oviapp.weights.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.Animal
import com.agrotech.oviapp.domain.model.Sex
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class WeightMilestone {
    BIRTH,
    WEANING,
    DAY_120,
    DAY_150,
    EVALUATION,
}

enum class WeaningDestination {
    LAMB_FATTENING,
    EWE_REPLACEMENT,
    SOLD,
}

data class WeightMeasurement(
    val id: UUID,
    val animalId: UUID,
    val ranchId: UUID,
    val milestone: WeightMilestone,
    val weightKg: BigDecimal,
    val scrotalCircumferenceCm: BigDecimal? = null,
    val date: LocalDate,
) {
    init {
        if (weightKg <= BigDecimal.ZERO || weightKg > MAX_WEIGHT_KG) {
            throw DomainValidationException(
                code = "INVALID_WEIGHT_KG",
                message = "El peso debe ser mayor a 0 y menor o igual a $MAX_WEIGHT_KG kg.",
            )
        }
        if (!hasAtMostTwoDecimals(weightKg)) {
            throw DomainValidationException(
                code = "INVALID_WEIGHT_PRECISION",
                message = "El peso admite como máximo $MAX_DECIMAL_PLACES decimales.",
            )
        }
        if (scrotalCircumferenceCm != null) {
            if (scrotalCircumferenceCm <= BigDecimal.ZERO || scrotalCircumferenceCm > MAX_SCROTAL_CM) {
                throw DomainValidationException(
                    code = "INVALID_SCROTAL_CIRCUMFERENCE",
                    message = "La circunferencia escrotal debe ser mayor a 0 y menor o igual a $MAX_SCROTAL_CM cm.",
                )
            }
            if (!hasAtMostTwoDecimals(scrotalCircumferenceCm)) {
                throw DomainValidationException(
                    code = "INVALID_SCROTAL_PRECISION",
                    message = "La circunferencia escrotal admite como máximo $MAX_DECIMAL_PLACES decimales.",
                )
            }
        }
    }

    companion object {
        private const val MAX_DECIMAL_PLACES = 2
        private val MAX_WEIGHT_KG = BigDecimal("999.99")
        private val MAX_SCROTAL_CM = BigDecimal("99.99")

        private fun hasAtMostTwoDecimals(value: BigDecimal): Boolean =
            value.stripTrailingZeros().scale() <= MAX_DECIMAL_PLACES
    }
}

data class WeightGainProjection(
    val weightId: UUID,
    val animalId: UUID,
    val ranchId: UUID,
    val folioInterno: String,
    val milestone: WeightMilestone,
    val date: LocalDate,
    val ageDays: Int,
    val weightKg: BigDecimal,
    val previousWeightKg: BigDecimal?,
    val daysSincePrevious: Int?,
    val gainKg: BigDecimal?,
    val dailyGainGrams: Long?,
)

object WeightPolicy {
    fun validateForAnimal(
        animalSex: Sex,
        birthDate: LocalDate,
        weightDate: LocalDate,
        scrotalCircumferenceCm: BigDecimal?,
    ) {
        if (animalSex == Sex.FEMALE && scrotalCircumferenceCm != null) {
            throw DomainValidationException(
                code = "SCROTAL_FEMALE_NOT_ALLOWED",
                message = "La circunferencia escrotal solo puede registrarse en ejemplares machos (MALE).",
            )
        }
        if (weightDate.isBefore(birthDate)) {
            throw DomainValidationException(
                code = "WEIGHT_DATE_BEFORE_BIRTH",
                message = "La fecha del pesaje no puede ser anterior a la fecha de nacimiento del ejemplar.",
            )
        }
    }

    fun validateWeaningDestination(animalSex: Sex, destination: WeaningDestination) {
        if (animalSex == Sex.MALE && destination == WeaningDestination.EWE_REPLACEMENT) {
            throw DomainValidationException(
                code = "INVALID_WEANING_DESTINATION",
                message = "Un ejemplar macho (MALE) no puede destinarse a recría de hembra (EWE_REPLACEMENT).",
            )
        }
    }
}

interface AnimalWeightingPort {
    fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): Animal?
    fun findByIdAndRanchIdForUpdate(animalId: UUID, ranchId: UUID): Animal?
    fun save(animal: Animal): Animal
}

interface WeightRepositoryPort {
    fun existsById(weightId: UUID): Boolean
    /** Returns accepted weaning measurements, newest date first, then highest ID. */
    fun findWeaningForAnimal(animalId: UUID, ranchId: UUID): List<WeightMeasurement>
    fun create(measurement: WeightMeasurement): WeightMeasurement
    fun findHistoryByAnimalAndRanch(animalId: UUID, ranchId: UUID): List<WeightGainProjection>
}
