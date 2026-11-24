package com.agrotech.oviapp.sync.infrastructure

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.infrastructure.persistence.entity.AnimalEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.MutacionProcesadaEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.PartoEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.PesajeEntity
import com.agrotech.oviapp.sync.domain.DuplicateMutationException
import com.agrotech.oviapp.sync.domain.SyncBirthLambRecord
import com.agrotech.oviapp.sync.domain.SyncBirthRecord
import com.agrotech.oviapp.weights.domain.WeaningDestination
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class SyncPersistenceAdaptersTest {

    @Test
    fun `ProcessedMutationJpaAdapter persists directly and translates mutaciones_procesadas_pkey conflict`() {
        val repo = mock(MutacionProcesadaJpaRepository::class.java)
        val em = mock(EntityManager::class.java)
        val adapter = ProcessedMutationJpaAdapter(repo, em)
        val mutationId = UUID.randomUUID()
        val ranchId = UUID.randomUUID()

        val pkViolation = ConstraintViolationException(
            "duplicate key",
            SQLException("duplicate key", "23505"),
            "mutaciones_procesadas_pkey",
        )
        willThrow(pkViolation).given(em).flush()

        val ex = assertThrows(DuplicateMutationException::class.java) {
            adapter.recordProcessed(mutationId, ranchId, Instant.now())
        }
        assertEquals(mutationId, ex.mutationId)
        verify(em).persist(any(MutacionProcesadaEntity::class.java))
        verify(em).flush()
        verify(repo, never()).save(any())
    }

    @Test
    fun `SyncBirthPersistenceAdapter persists birth, lambs, birth weight, and transitions dam to LACTATING`() {
        val partoRepo = mock(SyncPartoJpaRepository::class.java)
        val usuarioRepo = mock(SyncUsuarioJpaRepository::class.java)
        val animalRepo = mock(SyncAnimalLookupJpaRepository::class.java)
        val em = mock(EntityManager::class.java)
        val adapter = SyncBirthPersistenceAdapter(partoRepo, usuarioRepo, animalRepo, em)

        val ranchId = UUID.randomUUID()
        val damId = UUID.randomUUID()
        val lambId = UUID.randomUUID()
        val weightId = UUID.randomUUID()
        val damEntity = AnimalEntity(
            id = damId,
            ranchoId = ranchId,
            folioInterno = "MADRE-1",
            raza = "Dorper",
            color = "Blanco",
            sexo = "FEMALE",
            fechaNacimiento = LocalDate.of(2024, 1, 1),
            etapaVida = "ADULT",
            estadoReproductivo = "PREGNANT",
            estado = "ACTIVE",
        )
        given(animalRepo.findByIdAndRanchoId(damId, ranchId)).willReturn(damEntity)

        adapter.createBirth(
            SyncBirthRecord(
                id = UUID.randomUUID(),
                ranchId = ranchId,
                damId = damId,
                sireId = null,
                externalSireFolio = null,
                birthDate = LocalDate.of(2026, 10, 12),
                liveBornCount = 1,
                stillbornCount = 0,
                parturitionType = ParturitionType.SIMPLE,
                conceptionMethod = ConceptionMethod.MN,
                evidencePhotoS3Key = "$ranchId/births/b1.webp",
                registeredByUserId = UUID.randomUUID(),
                transitionDamToLactating = true,
                lambs = listOf(
                    SyncBirthLambRecord(
                        id = lambId,
                        folioInterno = "CRIA-1",
                        siniigaChip = null,
                        registroUno = null,
                        adn = null,
                        breed = "Dorper",
                        color = "Blanco",
                        sex = Sex.FEMALE,
                        criador = null,
                        lifeStage = LifeStage.LAMB_LACTATING,
                        reproductiveStatus = ReproductiveStatus.EMPTY,
                        birthWeightKg = BigDecimal("3.40"),
                        weightId = weightId,
                        photoS3Key = null,
                    ),
                ),
                updatedAt = Instant.parse("2026-10-12T10:00:00Z"),
            ),
        )

        verify(em, times(1)).persist(any(PartoEntity::class.java))
        verify(em, times(1)).persist(any(AnimalEntity::class.java))
        verify(em, times(1)).persist(any(PesajeEntity::class.java))
        verify(em, times(1)).flush()
        assertEquals(ReproductiveStatus.LACTATING.name, damEntity.estadoReproductivo)
    }

    @Test
    fun `SyncBirthPersistenceAdapter translates folio unique violation to BusinessConflictException`() {
        val partoRepo = mock(SyncPartoJpaRepository::class.java)
        val usuarioRepo = mock(SyncUsuarioJpaRepository::class.java)
        val animalRepo = mock(SyncAnimalLookupJpaRepository::class.java)
        val em = mock(EntityManager::class.java)
        val adapter = SyncBirthPersistenceAdapter(partoRepo, usuarioRepo, animalRepo, em)

        val violation = ConstraintViolationException(
            "duplicate folio",
            SQLException("duplicate folio", "23505"),
            "animales_rancho_id_folio_interno_key",
        )
        willThrow(violation).given(em).flush()

        val ex = assertThrows(BusinessConflictException::class.java) {
            adapter.createBirth(
                SyncBirthRecord(
                    id = UUID.randomUUID(),
                    ranchId = UUID.randomUUID(),
                    damId = UUID.randomUUID(),
                    sireId = null,
                    externalSireFolio = null,
                    birthDate = LocalDate.of(2026, 10, 12),
                    liveBornCount = 0,
                    stillbornCount = 1,
                    parturitionType = ParturitionType.SIMPLE,
                    conceptionMethod = ConceptionMethod.MN,
                    evidencePhotoS3Key = "r/births/b1.webp",
                    registeredByUserId = UUID.randomUUID(),
                    transitionDamToLactating = false,
                    lambs = emptyList(),
                    updatedAt = Instant.now(),
                ),
            )
        }
        assertEquals("FOLIO_ALREADY_EXISTS", ex.code)
    }

    @Test
    fun `SyncPayloadParserAdapter parses camelCase and snake_case payloads and rejects malformed JSON`() {
        val parser = SyncPayloadParserAdapter(jacksonObjectMapper().registerModule(JavaTimeModule()))
        val animalId = UUID.randomUUID()

        val snakeWeaning = parser.parseWeaningCreate(
            """
            {
              "animal_id": "$animalId",
              "fecha_destete": "2026-10-15",
              "peso_destete_kg": 18.50,
              "destino": "EWE_REPLACEMENT"
            }
            """.trimIndent(),
        )
        assertEquals(animalId, snakeWeaning.animalId)
        assertEquals(WeaningDestination.EWE_REPLACEMENT, snakeWeaning.destination)
        assertEquals(BigDecimal("18.50"), snakeWeaning.weaningWeightKg)

        val snakeWeight = parser.parseWeightCreate(
            """
            {
              "animal_id": "$animalId",
              "hito": "DAY_120",
              "peso_kg": 25.00,
              "fecha": "2026-10-14"
            }
            """.trimIndent(),
        )
        assertEquals(WeightMilestone.DAY_120, snakeWeight.milestone)

        val ex = assertThrows(DomainValidationException::class.java) {
            parser.parseAnimalCreate("{not-json")
        }
        assertEquals("INVALID_SYNC_PAYLOAD", ex.code)
    }

    @Test
    fun `SyncPayloadParserAdapter rejects JSON null payload as INVALID_SYNC_PAYLOAD`() {
        val parser = SyncPayloadParserAdapter(jacksonObjectMapper().registerModule(JavaTimeModule()))

        val ex = assertThrows(DomainValidationException::class.java) {
            parser.parseAnimalCreate("null")
        }
        assertEquals("INVALID_SYNC_PAYLOAD", ex.code)
    }
}
