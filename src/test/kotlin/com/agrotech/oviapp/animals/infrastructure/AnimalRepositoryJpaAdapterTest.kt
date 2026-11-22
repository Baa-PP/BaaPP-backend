package com.agrotech.oviapp.animals.infrastructure

import com.agrotech.oviapp.animals.domain.AnimalRecord
import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.animals.domain.ListAnimalsQuery
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.infrastructure.persistence.entity.AnimalEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.PesajeEntity
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadUseCase
import com.agrotech.oviapp.media.domain.PresignedDownloadResult
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AnimalRepositoryJpaAdapterTest {

    private val repository = mock(AnimalInventoryJpaRepository::class.java)
    private val entityManager = mock(EntityManager::class.java)
    private val adapter = AnimalRepositoryJpaAdapter(repository, entityManager)

    @Test
    fun `create uses EntityManager persist and flush without merge and translates 23505 violations to 409`() {
        val cases = listOf(
            "animales_pkey" to "ANIMAL_ID_ALREADY_EXISTS",
            "animales_rancho_id_folio_interno_key" to "FOLIO_ALREADY_EXISTS",
            "uq_animales_rancho_chip" to "SINIIGA_CHIP_ALREADY_EXISTS",
        )

        cases.forEach { (constraintName, expectedCode) ->
            reset(entityManager)
            val record = sampleRecord()
            val violation = ConstraintViolationException(
                "duplicate key",
                SQLException("duplicate key", "23505"),
                constraintName,
            )
            given(repository.existsById(record.id)).willReturn(false)
            willThrow(violation).given(entityManager).flush()

            val ex = assertThrows(BusinessConflictException::class.java) {
                adapter.create(record)
            }
            assertEquals(expectedCode, ex.code)
            verify(entityManager).persist(any(AnimalEntity::class.java))
            verify(entityManager).flush()
            verify(repository, never()).saveAndFlush(any(AnimalEntity::class.java))
        }

        // Also handles PersistenceException wrapping Hibernate ConstraintViolationException
        reset(entityManager)
        val wrappedRecord = sampleRecord()
        val wrappedViolation = PersistenceException(
            "persist failed",
            ConstraintViolationException("duplicate key", SQLException("duplicate key", "23505"), "animales_pkey"),
        )
        given(repository.existsById(wrappedRecord.id)).willReturn(false)
        willThrow(wrappedViolation).given(entityManager).flush()

        val wrappedEx = assertThrows(BusinessConflictException::class.java) {
            adapter.create(wrappedRecord)
        }
        assertEquals("ANIMAL_ID_ALREADY_EXISTS", wrappedEx.code)
    }

    @Test
    fun `create rethrows unmapped persistence exceptions`() {
        val record = sampleRecord()
        val fkViolation = ConstraintViolationException(
            "fk violation",
            SQLException("fk violation", "23503"),
            "animales_padre_id_fkey",
        )
        given(repository.existsById(record.id)).willReturn(false)
        willThrow(fkViolation).given(entityManager).flush()

        assertThrows(ConstraintViolationException::class.java) {
            adapter.create(record)
        }
    }

    @Test
    fun `update flushes via saveAndFlush and translates folio and chip unique violations to 409`() {
        listOf(
            "animales_rancho_id_folio_interno_key" to "FOLIO_ALREADY_EXISTS",
            "uq_animales_rancho_chip" to "SINIIGA_CHIP_ALREADY_EXISTS",
        ).forEach { (constraintName, expectedCode) ->
            val record = sampleRecord()
            val existingEntity = sampleEntity(record.id, record.ranchId)
            val violation = ConstraintViolationException(
                "duplicate key",
                SQLException("duplicate key", "23505"),
                constraintName,
            )
            given(repository.findByIdAndRanchoIdForUpdate(record.id, record.ranchId)).willReturn(existingEntity)
            given(repository.saveAndFlush(existingEntity))
                .willThrow(DataIntegrityViolationException("duplicate key", violation))

            val ex = assertThrows(BusinessConflictException::class.java) {
                adapter.update(record)
            }
            assertEquals(expectedCode, ex.code)
            verify(repository).saveAndFlush(existingEntity)
        }
    }

    @Test
    fun `searchSummaries maps v_animal_resumen projection and escapes LIKE wildcards`() {
        val ranchId = UUID.randomUUID()
        val animalId = UUID.randomUUID()
        val updatedAt = Instant.parse("2026-10-12T15:30:00Z")
        val updatedAfter = Instant.parse("2026-10-01T00:00:00Z")

        val row = mock(AnimalResumenViewRow::class.java)
        given(row.getAnimalId()).willReturn(animalId)
        given(row.getRanchId()).willReturn(ranchId)
        given(row.getFolioInterno()).willReturn("OV-100%")
        given(row.getChipSiniiga()).willReturn("4841234567")
        given(row.getRegistroUno()).willReturn("UNO-100")
        given(row.getSexo()).willReturn("FEMALE")
        given(row.getEtapa()).willReturn("ADULT")
        given(row.getEstadoReproductivo()).willReturn("PREGNANT")
        given(row.getRaza()).willReturn("Dorper")
        given(row.getColor()).willReturn("Blanco")
        given(row.getFechaNacimiento()).willReturn(LocalDate.of(2024, 5, 1))
        given(row.getEdadDias()).willReturn(520)
        given(row.getFechaDestete()).willReturn(LocalDate.of(2024, 7, 15))
        given(row.getMotivoAlta()).willReturn("BIRTH")
        given(row.getCriador()).willReturn("Criadero Sur")
        given(row.getPropietario()).willReturn("Rancho Propietario")
        given(row.getFotoS3Key()).willReturn("$ranchId/animals/a.webp")
        given(row.getUltimoPesoKg()).willReturn(BigDecimal("58.40"))
        given(row.getFechaUltimoPeso()).willReturn(LocalDate.of(2026, 9, 15))
        given(row.getPartos()).willReturn(2)
        given(row.getCargada()).willReturn(true)
        given(row.getEstado()).willReturn("ACTIVE")
        given(row.getFechaMuerte()).willReturn(null)
        given(row.getCausaMuerte()).willReturn(null)
        given(row.getPadreId()).willReturn(null)
        given(row.getMadreId()).willReturn(null)
        given(row.getActualizadoEn()).willReturn(updatedAt)

        val pageable = PageRequest.of(0, 20)
        given(
            repository.searchResumenView(
                ranchId = ranchId,
                searchPattern = "%OV-100!%%",
                sexo = "FEMALE",
                etapaVida = "ADULT",
                estadoReproductivo = "PREGNANT",
                razaPattern = "%Dorp!_er%",
                estado = "ACTIVE",
                updatedAfter = "2026-10-01T00:00:00Z",
                pageable = pageable,
            ),
        ).willReturn(PageImpl(listOf(row), pageable, 1))

        val page = adapter.searchSummaries(
            ListAnimalsQuery(
                ranchId = ranchId,
                search = "OV-100%",
                sex = Sex.FEMALE,
                lifeStage = LifeStage.ADULT,
                reproductiveStatus = ReproductiveStatus.PREGNANT,
                breed = "Dorp_er",
                status = AnimalStatus.ACTIVE,
                updatedAfter = updatedAfter,
                page = 0,
                size = 20,
            ),
        )

        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals(animalId, item.id)
        assertEquals("Criadero Sur", item.criador)
        assertEquals("Rancho Propietario", item.propietario)
        assertEquals(BigDecimal("58.40"), item.latestWeightKg)
        assertEquals(updatedAt, item.updatedAt)
        assertEquals(2, item.birthCount)
        assertEquals(true, item.pregnant)
    }

    @Test
    fun `findDetailByIdAndRanchId maps full detail projection`() {
        val ranchId = UUID.randomUUID()
        val animalId = UUID.randomUUID()
        val birthEventId = UUID.randomUUID()
        val updatedAt = Instant.parse("2026-10-12T15:30:00Z")

        val row = mock(AnimalDetailViewRow::class.java)
        given(row.getAnimalId()).willReturn(animalId)
        given(row.getRanchId()).willReturn(ranchId)
        given(row.getFolioInterno()).willReturn("OV-300")
        given(row.getChipSiniiga()).willReturn("4841234567")
        given(row.getRegistroUno()).willReturn("UNO-300")
        given(row.getAdn()).willReturn("ADN-300")
        given(row.getSexo()).willReturn("FEMALE")
        given(row.getEtapa()).willReturn("ADULT")
        given(row.getEstadoReproductivo()).willReturn("EMPTY")
        given(row.getRaza()).willReturn("Pelibuey")
        given(row.getColor()).willReturn("Canelo")
        given(row.getFechaNacimiento()).willReturn(LocalDate.of(2024, 1, 10))
        given(row.getEdadDias()).willReturn(600)
        given(row.getFechaDestete()).willReturn(LocalDate.of(2024, 3, 25))
        given(row.getMotivoAlta()).willReturn("PURCHASE")
        given(row.getCriador()).willReturn("Criador Original")
        given(row.getPropietario()).willReturn("Propietario Actual")
        given(row.getFotoS3Key()).willReturn("$ranchId/animals/300.webp")
        given(row.getUltimoPesoKg()).willReturn(BigDecimal("52.00"))
        given(row.getFechaUltimoPeso()).willReturn(LocalDate.of(2026, 9, 1))
        given(row.getPartos()).willReturn(1)
        given(row.getCargada()).willReturn(false)
        given(row.getEstado()).willReturn("DECEASED")
        given(row.getFechaMuerte()).willReturn(LocalDate.of(2026, 10, 1))
        given(row.getCausaMuerte()).willReturn("Vejez")
        given(row.getEtapaAlMorir()).willReturn("ADULT")
        given(row.getTipoParto()).willReturn("TWIN")
        given(row.getMetodoConcepcion()).willReturn("IA")
        given(row.getPadreId()).willReturn(null)
        given(row.getMadreId()).willReturn(null)
        given(row.getFolioPadreExterno()).willReturn("EXT-P1")
        given(row.getFolioMadreExterno()).willReturn("EXT-M1")
        given(row.getPartoId()).willReturn(birthEventId)
        given(row.getActualizadoEn()).willReturn(updatedAt)

        given(repository.findDetailView(animalId, ranchId)).willReturn(row)

        val detail = adapter.findDetailByIdAndRanchId(animalId, ranchId)
        assertNotNull(detail)
        assertEquals("ADN-300", detail?.adn)
        assertEquals("Criador Original", detail?.criador)
        assertEquals("Propietario Actual", detail?.propietario)
        assertEquals(ParturitionType.TWIN, detail?.parturitionType)
        assertEquals(ConceptionMethod.IA, detail?.conceptionMethod)
        assertEquals(LifeStage.ADULT, detail?.lifeStageAtDeath)
        assertEquals(birthEventId, detail?.birthEventId)
        assertEquals(updatedAt, detail?.updatedAt)
    }

    @Test
    fun `photo and weight adapters delegate cleanly to media use case and pesajes repository`() {
        val ranchId = UUID.randomUUID()
        val animalId = UUID.randomUUID()
        val key = "$ranchId/animals/pic.webp"

        val downloadUseCase = mock(GeneratePresignedDownloadUseCase::class.java)
        given(downloadUseCase.execute(GeneratePresignedDownloadCommand(ranchId, key)))
            .willReturn(PresignedDownloadResult("https://s3.signed/$key", key, 300L))

        val photoAdapter = AnimalPhotoUrlMediaAdapter(downloadUseCase)
        assertEquals("https://s3.signed/$key", photoAdapter.generatePresignedDownloadUrl(ranchId, key))

        val pesajeRepo = mock(AnimalPesajeJpaRepository::class.java)
        val pesajeId = UUID.randomUUID()
        val pesaje = PesajeEntity(
            id = pesajeId,
            animalId = animalId,
            ranchoId = ranchId,
            hito = "WEANING",
            pesoKg = BigDecimal("20.50"),
            circunferenciaEscrotalCm = BigDecimal("18.00"),
            fecha = LocalDate.of(2026, 7, 15),
        )
        given(pesajeRepo.findAllByAnimalIdAndRanchoIdOrderByFechaAscIdAsc(animalId, ranchId))
            .willReturn(listOf(pesaje))

        val weightAdapter = AnimalWeightReadJpaAdapter(pesajeRepo)
        val weights = weightAdapter.findAllByAnimalAndRanchOrderedByDateAsc(animalId, ranchId)
        assertEquals(1, weights.size)
        assertEquals(pesajeId, weights.first().id)
        assertEquals("WEANING", weights.first().milestone)
        assertEquals(BigDecimal("20.50"), weights.first().weightKg)
    }

    @Test
    fun `hasDependentRecordsAsSire and hasDependentRecordsAsDam delegate to ranch-scoped repository checks`() {
        val ranchId = UUID.randomUUID()
        val sireId = UUID.randomUUID()
        val damId = UUID.randomUUID()

        given(repository.hasDependentRecordsAsSire(ranchId, sireId)).willReturn(true)
        given(repository.hasDependentRecordsAsDam(ranchId, damId)).willReturn(false)

        assertEquals(true, adapter.hasDependentRecordsAsSire(ranchId, sireId))
        assertEquals(false, adapter.hasDependentRecordsAsDam(ranchId, damId))
        verify(repository).hasDependentRecordsAsSire(ranchId, sireId)
        verify(repository).hasDependentRecordsAsDam(ranchId, damId)
    }

    private fun sampleRecord() = AnimalRecord(
        id = UUID.randomUUID(),
        ranchId = UUID.randomUUID(),
        folioInterno = "OV-10",
        siniigaChip = "4841234567",
        registroUno = null,
        adn = null,
        breed = "Dorper",
        color = "Blanco",
        sex = Sex.FEMALE,
        birthDate = LocalDate.of(2026, 1, 10),
        weaningDate = null,
        entryReason = EntryReason.BIRTH,
        criador = null,
        parturitionType = ParturitionType.SIMPLE,
        conceptionMethod = ConceptionMethod.MN,
        sireId = null,
        damId = null,
        externalSireFolio = null,
        externalDamFolio = null,
        lifeStage = LifeStage.LAMB_LACTATING,
        reproductiveStatus = ReproductiveStatus.EMPTY,
        status = AnimalStatus.ACTIVE,
        mortalityDate = null,
        mortalityCause = null,
        lifeStageAtDeath = null,
        photoS3Key = null,
        birthEventId = null,
        updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun sampleEntity(id: UUID, ranchId: UUID) = AnimalEntity(
        id = id,
        ranchoId = ranchId,
        folioInterno = "OV-10",
        raza = "Dorper",
        color = "Blanco",
        sexo = "FEMALE",
        fechaNacimiento = LocalDate.of(2026, 1, 10),
        etapaVida = "LAMB_LACTATING",
        estadoReproductivo = "EMPTY",
        estado = "ACTIVE",
    )
}
