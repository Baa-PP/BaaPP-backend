package com.agrotech.oviapp.weights.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.domain.service.ZootechnicalCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class WeightPolicyAndCalculatorTest {

    @Test
    fun `ZootechnicalCalculator computes daily weight gain in kg and grams`() {
        val gdpKg = ZootechnicalCalculator.dailyWeightGainKg(
            currentWeightKg = BigDecimal("19.50"),
            previousWeightKg = BigDecimal("4.50"),
            daysElapsed = 60,
        )
        val gdpGrams = ZootechnicalCalculator.dailyWeightGainGrams(
            currentWeightKg = BigDecimal("19.50"),
            previousWeightKg = BigDecimal("4.50"),
            daysElapsed = 60,
        )

        assertEquals(BigDecimal("0.2500"), gdpKg)
        assertEquals(250L, gdpGrams)
    }

    @Test
    fun `ZootechnicalCalculator returns null GDP when elapsed days is zero or negative`() {
        assertNull(
            ZootechnicalCalculator.dailyWeightGainKg(
                currentWeightKg = BigDecimal("10.00"),
                previousWeightKg = BigDecimal("4.00"),
                daysElapsed = 0,
            ),
        )
        assertNull(
            ZootechnicalCalculator.dailyWeightGainGrams(
                currentWeightKg = BigDecimal("10.00"),
                previousWeightKg = BigDecimal("4.00"),
                daysElapsed = -2,
            ),
        )
    }

    @Test
    fun `rejects scrotal circumference when animal sex is FEMALE`() {
        val birthDate = LocalDate.of(2026, 8, 1)
        assertThrows(DomainValidationException::class.java) {
            WeightPolicy.validateForAnimal(
                animalSex = Sex.FEMALE,
                birthDate = birthDate,
                weightDate = LocalDate.of(2026, 10, 15),
                scrotalCircumferenceCm = BigDecimal("22.50"),
            )
        }
    }

    @Test
    fun `allows scrotal circumference when animal sex is MALE and date is on or after birth`() {
        val birthDate = LocalDate.of(2026, 8, 1)
        WeightPolicy.validateForAnimal(
            animalSex = Sex.MALE,
            birthDate = birthDate,
            weightDate = LocalDate.of(2026, 10, 15),
            scrotalCircumferenceCm = BigDecimal("24.00"),
        )
    }

    @Test
    fun `rejects weight date before animal birth date and non-positive weight`() {
        val birthDate = LocalDate.of(2026, 8, 10)
        assertThrows(DomainValidationException::class.java) {
            WeightPolicy.validateForAnimal(
                animalSex = Sex.MALE,
                birthDate = birthDate,
                weightDate = LocalDate.of(2026, 8, 9),
                scrotalCircumferenceCm = null,
            )
        }

        assertThrows(DomainValidationException::class.java) {
            WeightMeasurement(
                id = UUID.randomUUID(),
                animalId = UUID.randomUUID(),
                ranchId = UUID.randomUUID(),
                milestone = WeightMilestone.BIRTH,
                weightKg = BigDecimal.ZERO,
                scrotalCircumferenceCm = null,
                date = birthDate,
            )
        }
    }

    @Test
    fun `rejects more than two decimal places on weight and scrotal circumference while keeping exact two decimals`() {
        val valid = WeightMeasurement(
            id = UUID.randomUUID(),
            animalId = UUID.randomUUID(),
            ranchId = UUID.randomUUID(),
            milestone = WeightMilestone.DAY_120,
            weightKg = BigDecimal("12.34"),
            scrotalCircumferenceCm = BigDecimal("21.30"),
            date = LocalDate.of(2026, 10, 1),
        )
        assertEquals(BigDecimal("12.34"), valid.weightKg)

        assertThrows(DomainValidationException::class.java) {
            WeightMeasurement(
                id = UUID.randomUUID(),
                animalId = UUID.randomUUID(),
                ranchId = UUID.randomUUID(),
                milestone = WeightMilestone.DAY_120,
                weightKg = BigDecimal("12.345"),
                scrotalCircumferenceCm = null,
                date = LocalDate.of(2026, 10, 1),
            )
        }

        assertThrows(DomainValidationException::class.java) {
            WeightMeasurement(
                id = UUID.randomUUID(),
                animalId = UUID.randomUUID(),
                ranchId = UUID.randomUUID(),
                milestone = WeightMilestone.DAY_120,
                weightKg = BigDecimal("12.34"),
                scrotalCircumferenceCm = BigDecimal("21.345"),
                date = LocalDate.of(2026, 10, 1),
            )
        }
    }

    @Test
    fun `rejects EWE_REPLACEMENT weaning destination for MALE lambs and allows valid combinations`() {
        assertThrows(DomainValidationException::class.java) {
            WeightPolicy.validateWeaningDestination(Sex.MALE, WeaningDestination.EWE_REPLACEMENT)
        }

        WeightPolicy.validateWeaningDestination(Sex.FEMALE, WeaningDestination.EWE_REPLACEMENT)
        WeightPolicy.validateWeaningDestination(Sex.MALE, WeaningDestination.LAMB_FATTENING)
        WeightPolicy.validateWeaningDestination(Sex.MALE, WeaningDestination.SOLD)
    }
}
