package com.agrotech.oviapp.weights.infrastructure

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.infrastructure.persistence.entity.PesajeEntity
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.sql.SQLException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class WeightRepositoryJpaAdapterTest {

    private val repository = mock(PesajeJpaRepository::class.java)
    private val adapter = WeightRepositoryJpaAdapter(repository)

    @Test
    fun `create flushes insert and translates pesajes_pkey violation to BusinessConflictException`() {
        val measurement = sampleMeasurement()
        val constraintViolation = ConstraintViolationException(
            "duplicate key",
            SQLException("duplicate key", "23505"),
            "pesajes_pkey",
        )
        given(repository.existsById(measurement.id)).willReturn(false)
        given(repository.saveAndFlush(any(PesajeEntity::class.java)))
            .willThrow(DataIntegrityViolationException("duplicate key", constraintViolation))

        val ex = assertThrows(BusinessConflictException::class.java) {
            adapter.create(measurement)
        }

        assertEquals("WEIGHT_ID_ALREADY_EXISTS", ex.code)
        verify(repository).saveAndFlush(any(PesajeEntity::class.java))
    }

    @Test
    fun `create rethrows non-primary-key DataIntegrityViolationException`() {
        val measurement = sampleMeasurement()
        val fkViolation = ConstraintViolationException(
            "fk violation",
            SQLException("fk violation", "23503"),
            "pesajes_animal_id_fkey",
        )
        given(repository.existsById(measurement.id)).willReturn(false)
        given(repository.saveAndFlush(any(PesajeEntity::class.java)))
            .willThrow(DataIntegrityViolationException("fk violation", fkViolation))

        assertThrows(DataIntegrityViolationException::class.java) {
            adapter.create(measurement)
        }
    }

    @Test
    fun `touchUpdatedAt delegates atomic ranch-scoped timestamp update to repository`() {
        val animalRepo = mock(AnimalWeightingJpaRepository::class.java)
        val animalAdapter = AnimalWeightingJpaAdapter(animalRepo)
        val animalId = UUID.randomUUID()
        val ranchId = UUID.randomUUID()

        animalAdapter.touchUpdatedAt(animalId, ranchId)

        val invocation = mockingDetails(animalRepo).invocations.single()
        assertEquals("touchUpdatedAt", invocation.method.name)
        assertEquals(animalId, invocation.arguments[0])
        assertEquals(ranchId, invocation.arguments[1])
        assertTrue(invocation.arguments[2] is OffsetDateTime)
    }

    private fun sampleMeasurement() = WeightMeasurement(
        id = UUID.randomUUID(),
        animalId = UUID.randomUUID(),
        ranchId = UUID.randomUUID(),
        milestone = WeightMilestone.WEANING,
        weightKg = BigDecimal("20.00"),
        date = LocalDate.of(2026, 9, 14),
    )
}
