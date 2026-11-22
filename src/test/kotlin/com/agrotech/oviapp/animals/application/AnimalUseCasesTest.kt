package com.agrotech.oviapp.animals.application

import com.agrotech.oviapp.animals.domain.AnimalDetail
import com.agrotech.oviapp.animals.domain.AnimalPage
import com.agrotech.oviapp.animals.domain.AnimalPhotoUrlPort
import com.agrotech.oviapp.animals.domain.AnimalRecord
import com.agrotech.oviapp.animals.domain.AnimalRepositoryPort
import com.agrotech.oviapp.animals.domain.AnimalSummary
import com.agrotech.oviapp.animals.domain.AnimalWeightReadPort
import com.agrotech.oviapp.animals.domain.AnimalWeightRecord
import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.animals.domain.ListAnimalsQuery
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AnimalUseCasesTest {

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val sireId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val damId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val foreignSireId = UUID.fromString("44444444-4444-4444-4444-444444444444")

    @Test
    fun `listAnimals forwards filters, updatedAfter, and pagination and rejects out-of-range page size`() {
        val repo = InMemoryAnimalRepository()
        val updatedCursor = Instant.parse("2026-10-01T12:00:00Z")
        val matching = sampleSummary(
            id = UUID.randomUUID(),
            ranchId = ranchA,
            folioInterno = "OV-101",
            siniigaChip = "4841234567",
            breed = "Dorper",
            sex = Sex.FEMALE,
            lifeStage = LifeStage.ADULT,
            reproductiveStatus = ReproductiveStatus.PREGNANT,
            status = AnimalStatus.ACTIVE,
            updatedAt = Instant.parse("2026-10-02T08:00:00Z"),
        )
        val unchangedAtCursor = matching.copy(
            id = UUID.randomUUID(),
            folioInterno = "OV-102",
            updatedAt = updatedCursor,
        )
        repo.summaries.addAll(listOf(matching, unchangedAtCursor))

        val useCase = ListAnimalsUseCase(repo)
        val result = useCase.execute(
            ListAnimalsQuery(
                ranchId = ranchA,
                search = "OV-1",
                sex = Sex.FEMALE,
                lifeStage = LifeStage.ADULT,
                reproductiveStatus = ReproductiveStatus.PREGNANT,
                breed = "dorper",
                status = AnimalStatus.ACTIVE,
                updatedAfter = updatedCursor,
                page = 0,
                size = 20,
            ),
        )

        assertEquals(1, result.items.size)
        assertEquals("OV-101", result.items.first().folioInterno)
        assertEquals(1L, result.totalElements)
        assertEquals(1, result.totalPages)

        assertEquals(
            "INVALID_PAGINATION",
            assertThrows(DomainValidationException::class.java) {
                useCase.execute(ListAnimalsQuery(ranchId = ranchA, page = 0, size = 101))
            }.code,
        )
    }

    @Test
    fun `getAnimalDetail returns criador vs propietario, ordered weights, and presigned photoUrl or null`() {
        val repo = InMemoryAnimalRepository()
        val photoPort = FakeAnimalPhotoUrlPort()
        val animalWithPhotoId = UUID.randomUUID()
        val animalWithoutPhotoId = UUID.randomUUID()
        val animalWithBlankPhotoId = UUID.randomUUID()
        val photoKey = "$ranchA/animals/$animalWithPhotoId.webp"

        val weights = listOf(
            AnimalWeightRecord(UUID.randomUUID(), "BIRTH", BigDecimal("4.20"), null, LocalDate.of(2026, 2, 1)),
            AnimalWeightRecord(UUID.randomUUID(), "WEANING", BigDecimal("21.50"), null, LocalDate.of(2026, 4, 18)),
        )
        val weightPort = FakeAnimalWeightReadPort(mapOf((animalWithPhotoId to ranchA) to weights))

        repo.details[animalWithPhotoId to ranchA] = sampleDetail(
            id = animalWithPhotoId,
            ranchId = ranchA,
            criador = "Rancho El Origen",
            propietario = "Lucas Ganadero",
            photoS3Key = photoKey,
            latestWeightKg = BigDecimal("21.50"),
            latestWeightDate = LocalDate.of(2026, 4, 18),
        )
        repo.details[animalWithoutPhotoId to ranchA] = sampleDetail(
            id = animalWithoutPhotoId,
            ranchId = ranchA,
            criador = null,
            propietario = "Lucas Ganadero",
            photoS3Key = null,
            latestWeightKg = null,
            latestWeightDate = null,
        )
        repo.details[animalWithBlankPhotoId to ranchA] = sampleDetail(
            id = animalWithBlankPhotoId,
            ranchId = ranchA,
            criador = null,
            propietario = "Lucas Ganadero",
            photoS3Key = "   ",
            latestWeightKg = null,
            latestWeightDate = null,
        )

        val useCase = GetAnimalDetailUseCase(repo, weightPort, photoPort)

        val withPhoto = useCase.execute(ranchA, animalWithPhotoId)
        assertEquals("Rancho El Origen", withPhoto.criador)
        assertEquals("Lucas Ganadero", withPhoto.propietario)
        assertEquals(2, withPhoto.weights.size)
        assertEquals("BIRTH", withPhoto.weights[0].milestone)
        assertEquals("WEANING", withPhoto.weights[1].milestone)
        assertEquals(BigDecimal("21.50"), withPhoto.latestWeightKg)
        assertEquals("https://signed.example.test/$photoKey", withPhoto.photoUrl)
        assertEquals(1, photoPort.calls)

        val withoutPhoto = useCase.execute(ranchA, animalWithoutPhotoId)
        assertNull(withoutPhoto.criador)
        assertEquals("Lucas Ganadero", withoutPhoto.propietario)
        assertNull(withoutPhoto.photoUrl)
        assertEquals(1, photoPort.calls)

        val withBlankPhoto = useCase.execute(ranchA, animalWithBlankPhotoId)
        assertNull(withBlankPhoto.photoUrl)
        assertEquals(1, photoPort.calls)

        assertEquals(
            "ANIMAL_NOT_FOUND",
            assertThrows(ResourceNotFoundException::class.java) {
                useCase.execute(ranchB, animalWithPhotoId)
            }.code,
        )
    }

    @Test
    fun `createAnimal enforces uniqueness per ranch, validates parents in same ranch, and accepts valid data`() {
        val repo = InMemoryAnimalRepository()
        repo.seed(sampleRecord(id = sireId, ranchId = ranchA, folioInterno = "RAM-01", sex = Sex.MALE, reproductiveStatus = ReproductiveStatus.STUD_RAM))
        repo.seed(sampleRecord(id = damId, ranchId = ranchA, folioInterno = "EWE-01", sex = Sex.FEMALE, reproductiveStatus = ReproductiveStatus.LACTATING))
        repo.seed(sampleRecord(id = foreignSireId, ranchId = ranchB, folioInterno = "RAM-B1", sex = Sex.MALE, reproductiveStatus = ReproductiveStatus.STUD_RAM))

        val useCase = CreateAnimalUseCase(repo)
        val clientId = UUID.randomUUID()

        val created = useCase.execute(
            CreateAnimalCommand(
                id = clientId,
                ranchId = ranchA,
                folioInterno = " OV-500 ",
                siniigaChip = "4841234567",
                registroUno = "UNO-500",
                adn = "ADN-500",
                breed = "Dorper",
                color = "Blanco",
                sex = Sex.FEMALE,
                birthDate = LocalDate.of(2026, 3, 1),
                entryReason = EntryReason.BIRTH,
                criador = "Criadero Norte",
                sireId = sireId,
                damId = damId,
                lifeStage = LifeStage.LAMB_LACTATING,
                reproductiveStatus = ReproductiveStatus.EMPTY,
                status = AnimalStatus.ACTIVE,
                photoS3Key = "$ranchA/animals/$clientId.webp",
            ),
        )
        assertEquals(clientId, created.id)
        assertEquals("OV-500", created.folioInterno)
        assertEquals("4841234567", created.siniigaChip)

        // Duplicate client ID -> 409
        assertEquals(
            "ANIMAL_ID_ALREADY_EXISTS",
            assertThrows(BusinessConflictException::class.java) {
                useCase.execute(baseCreateCommand(id = clientId, folioInterno = "OV-501"))
            }.code,
        )

        // Duplicate folio in same ranch -> 409
        assertEquals(
            "FOLIO_ALREADY_EXISTS",
            assertThrows(BusinessConflictException::class.java) {
                useCase.execute(baseCreateCommand(folioInterno = "OV-500"))
            }.code,
        )

        // Duplicate SINIIGA chip in same ranch -> 409
        assertEquals(
            "SINIIGA_CHIP_ALREADY_EXISTS",
            assertThrows(BusinessConflictException::class.java) {
                useCase.execute(baseCreateCommand(folioInterno = "OV-502", siniigaChip = "4841234567"))
            }.code,
        )

        // Foreign sire rejected
        assertEquals(
            "INVALID_SIRE",
            assertThrows(DomainValidationException::class.java) {
                useCase.execute(baseCreateCommand(folioInterno = "OV-503", sireId = foreignSireId))
            }.code,
        )

        // Wrong sex sire (FEMALE) rejected
        assertEquals(
            "INVALID_SIRE_SEX",
            assertThrows(DomainValidationException::class.java) {
                useCase.execute(baseCreateCommand(folioInterno = "OV-504", sireId = damId))
            }.code,
        )

        // Wrong sex dam (MALE) rejected
        assertEquals(
            "INVALID_DAM_SEX",
            assertThrows(DomainValidationException::class.java) {
                useCase.execute(baseCreateCommand(folioInterno = "OV-505", damId = sireId))
            }.code,
        )

        // Creating DECEASED animal defaults lifeStageAtDeath to lifeStage
        val createdDeceased = useCase.execute(
            baseCreateCommand(folioInterno = "OV-506").copy(
                status = AnimalStatus.DECEASED,
                mortalityDate = LocalDate.of(2026, 4, 1),
                mortalityCause = "Hipotermia",
            ),
        )
        assertEquals(AnimalStatus.DECEASED, createdDeceased.status)
        assertEquals(LifeStage.LAMB_LACTATING, createdDeceased.lifeStageAtDeath)
    }

    @Test
    fun `updateAnimal supports soft transitions to SOLD and DECEASED preserving history and rejects collisions or foreign ranch`() {
        val repo = InMemoryAnimalRepository()
        val birthEventId = UUID.randomUUID()
        val existing = sampleRecord(
            id = UUID.randomUUID(),
            ranchId = ranchA,
            folioInterno = "OV-200",
            siniigaChip = "4840000200",
            sex = Sex.FEMALE,
            lifeStage = LifeStage.EWE_REPLACEMENT,
            reproductiveStatus = ReproductiveStatus.EMPTY,
            birthEventId = birthEventId,
        )
        val otherInSameRanch = sampleRecord(
            id = UUID.randomUUID(),
            ranchId = ranchA,
            folioInterno = "OV-201",
            siniigaChip = "4840000201",
            sex = Sex.FEMALE,
        )
        repo.seed(existing)
        repo.seed(otherInSameRanch)

        val useCase = UpdateAnimalUseCase(repo)

        // Soft transition to SOLD keeping same folio & chip succeeds
        val sold = useCase.execute(
            UpdateAnimalCommand(
                animalId = existing.id,
                ranchId = ranchA,
                folioInterno = "OV-200",
                siniigaChip = "4840000200",
                breed = existing.breed,
                color = existing.color,
                sex = existing.sex,
                birthDate = existing.birthDate,
                lifeStage = LifeStage.ADULT,
                reproductiveStatus = ReproductiveStatus.EMPTY,
                status = AnimalStatus.SOLD,
            ),
        )
        assertEquals(AnimalStatus.SOLD, sold.status)
        assertEquals(birthEventId, sold.birthEventId)

        // Soft transition to DECEASED defaults lifeStageAtDeath to previous lifeStage (ADULT)
        val deceased = useCase.execute(
            UpdateAnimalCommand(
                animalId = existing.id,
                ranchId = ranchA,
                folioInterno = "OV-200",
                siniigaChip = "4840000200",
                breed = existing.breed,
                color = existing.color,
                sex = existing.sex,
                birthDate = existing.birthDate,
                lifeStage = LifeStage.ADULT,
                reproductiveStatus = ReproductiveStatus.EMPTY,
                status = AnimalStatus.DECEASED,
                mortalityDate = LocalDate.of(2026, 8, 10),
                mortalityCause = "Clostridiosis",
                lifeStageAtDeath = null,
            ),
        )
        assertEquals(AnimalStatus.DECEASED, deceased.status)
        assertEquals(LocalDate.of(2026, 8, 10), deceased.mortalityDate)
        assertEquals("Clostridiosis", deceased.mortalityCause)
        assertEquals(LifeStage.ADULT, deceased.lifeStageAtDeath)
        assertEquals(birthEventId, deceased.birthEventId)

        // Duplicate folio with another animal -> 409
        assertEquals(
            "FOLIO_ALREADY_EXISTS",
            assertThrows(BusinessConflictException::class.java) {
                useCase.execute(
                    UpdateAnimalCommand(
                        animalId = existing.id,
                        ranchId = ranchA,
                        folioInterno = "OV-201",
                        breed = existing.breed,
                        color = existing.color,
                        sex = existing.sex,
                        birthDate = existing.birthDate,
                        lifeStage = LifeStage.ADULT,
                        reproductiveStatus = ReproductiveStatus.EMPTY,
                        status = AnimalStatus.ACTIVE,
                    ),
                )
            }.code,
        )

        // Duplicate chip with another animal -> 409
        assertEquals(
            "SINIIGA_CHIP_ALREADY_EXISTS",
            assertThrows(BusinessConflictException::class.java) {
                useCase.execute(
                    UpdateAnimalCommand(
                        animalId = existing.id,
                        ranchId = ranchA,
                        folioInterno = "OV-200",
                        siniigaChip = "4840000201",
                        breed = existing.breed,
                        color = existing.color,
                        sex = existing.sex,
                        birthDate = existing.birthDate,
                        lifeStage = LifeStage.ADULT,
                        reproductiveStatus = ReproductiveStatus.EMPTY,
                        status = AnimalStatus.ACTIVE,
                    ),
                )
            }.code,
        )

        // Foreign parent on update rejected
        repo.seed(sampleRecord(id = foreignSireId, ranchId = ranchB, folioInterno = "RAM-B2", sex = Sex.MALE))
        assertEquals(
            "INVALID_SIRE",
            assertThrows(DomainValidationException::class.java) {
                useCase.execute(
                    UpdateAnimalCommand(
                        animalId = existing.id,
                        ranchId = ranchA,
                        folioInterno = "OV-200",
                        breed = existing.breed,
                        color = existing.color,
                        sex = existing.sex,
                        birthDate = existing.birthDate,
                        sireId = foreignSireId,
                        lifeStage = LifeStage.ADULT,
                        reproductiveStatus = ReproductiveStatus.EMPTY,
                        status = AnimalStatus.ACTIVE,
                    ),
                )
            }.code,
        )

        // Foreign ranch -> 404
        assertEquals(
            "ANIMAL_NOT_FOUND",
            assertThrows(ResourceNotFoundException::class.java) {
                useCase.execute(
                    UpdateAnimalCommand(
                        animalId = existing.id,
                        ranchId = ranchB,
                        folioInterno = "OV-200",
                        breed = existing.breed,
                        color = existing.color,
                        sex = existing.sex,
                        birthDate = existing.birthDate,
                        lifeStage = LifeStage.ADULT,
                        reproductiveStatus = ReproductiveStatus.EMPTY,
                        status = AnimalStatus.ACTIVE,
                    ),
                )
            }.code,
        )
    }

    private fun baseCreateCommand(
        id: UUID? = null,
        folioInterno: String = "OV-900",
        siniigaChip: String? = null,
        sireId: UUID? = null,
        damId: UUID? = null,
    ) = CreateAnimalCommand(
        id = id,
        ranchId = ranchA,
        folioInterno = folioInterno,
        siniigaChip = siniigaChip,
        breed = "Dorper",
        color = "Blanco",
        sex = Sex.FEMALE,
        birthDate = LocalDate.of(2026, 3, 1),
        sireId = sireId,
        damId = damId,
        lifeStage = LifeStage.LAMB_LACTATING,
        reproductiveStatus = ReproductiveStatus.EMPTY,
    )

    private fun sampleRecord(
        id: UUID,
        ranchId: UUID,
        folioInterno: String,
        siniigaChip: String? = null,
        sex: Sex = Sex.FEMALE,
        lifeStage: LifeStage = LifeStage.ADULT,
        reproductiveStatus: ReproductiveStatus = ReproductiveStatus.EMPTY,
        birthEventId: UUID? = null,
    ) = AnimalRecord(
        id = id,
        ranchId = ranchId,
        folioInterno = folioInterno,
        siniigaChip = siniigaChip,
        registroUno = null,
        adn = null,
        breed = "Dorper",
        color = "Blanco",
        sex = sex,
        birthDate = LocalDate.of(2025, 1, 10),
        weaningDate = null,
        entryReason = EntryReason.BIRTH,
        criador = null,
        parturitionType = ParturitionType.SIMPLE,
        conceptionMethod = ConceptionMethod.MN,
        sireId = null,
        damId = null,
        externalSireFolio = null,
        externalDamFolio = null,
        lifeStage = lifeStage,
        reproductiveStatus = reproductiveStatus,
        status = AnimalStatus.ACTIVE,
        mortalityDate = null,
        mortalityCause = null,
        lifeStageAtDeath = null,
        photoS3Key = null,
        birthEventId = birthEventId,
        updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
    )

    private fun sampleSummary(
        id: UUID,
        ranchId: UUID,
        folioInterno: String,
        siniigaChip: String?,
        breed: String,
        sex: Sex,
        lifeStage: LifeStage,
        reproductiveStatus: ReproductiveStatus,
        status: AnimalStatus,
        updatedAt: Instant,
    ) = AnimalSummary(
        id = id,
        ranchId = ranchId,
        folioInterno = folioInterno,
        siniigaChip = siniigaChip,
        registroUno = null,
        sex = sex,
        lifeStage = lifeStage,
        reproductiveStatus = reproductiveStatus,
        breed = breed,
        color = "Blanco",
        birthDate = LocalDate.of(2024, 5, 1),
        ageDays = 500,
        weaningDate = LocalDate.of(2024, 7, 15),
        entryReason = EntryReason.BIRTH,
        criador = "Rancho Origen",
        propietario = "Lucas Ganadero",
        photoS3Key = null,
        latestWeightKg = BigDecimal("54.00"),
        latestWeightDate = LocalDate.of(2026, 9, 1),
        birthCount = 1,
        pregnant = true,
        status = status,
        mortalityDate = null,
        mortalityCause = null,
        sireId = null,
        damId = null,
        updatedAt = updatedAt,
    )

    private fun sampleDetail(
        id: UUID,
        ranchId: UUID,
        criador: String?,
        propietario: String,
        photoS3Key: String?,
        latestWeightKg: BigDecimal?,
        latestWeightDate: LocalDate?,
    ) = AnimalDetail(
        id = id,
        ranchId = ranchId,
        folioInterno = "OV-777",
        siniigaChip = "4841234567",
        registroUno = "UNO-777",
        adn = "ADN-777",
        breed = "Katahdin",
        color = "Pinto",
        sex = Sex.FEMALE,
        birthDate = LocalDate.of(2025, 2, 1),
        ageDays = 450,
        weaningDate = LocalDate.of(2025, 4, 18),
        entryReason = EntryReason.PURCHASE,
        criador = criador,
        propietario = propietario,
        lifeStage = LifeStage.ADULT,
        reproductiveStatus = ReproductiveStatus.EMPTY,
        status = AnimalStatus.ACTIVE,
        parturitionType = ParturitionType.TWIN,
        conceptionMethod = ConceptionMethod.IA,
        pregnant = false,
        birthCount = 0,
        mortalityDate = null,
        mortalityCause = null,
        lifeStageAtDeath = null,
        sireId = null,
        damId = null,
        externalSireFolio = "EXT-S1",
        externalDamFolio = "EXT-D1",
        birthEventId = null,
        latestWeightKg = latestWeightKg,
        latestWeightDate = latestWeightDate,
        weights = emptyList(),
        photoS3Key = photoS3Key,
        photoUrl = null,
        updatedAt = Instant.parse("2026-10-05T10:15:30Z"),
    )

    private class FakeAnimalPhotoUrlPort : AnimalPhotoUrlPort {
        var calls = 0
        override fun generatePresignedDownloadUrl(ranchId: UUID, s3Key: String): String {
            calls++
            return "https://signed.example.test/$s3Key"
        }
    }

    private class FakeAnimalWeightReadPort(
        private val data: Map<Pair<UUID, UUID>, List<AnimalWeightRecord>>,
    ) : AnimalWeightReadPort {
        override fun findAllByAnimalAndRanchOrderedByDateAsc(animalId: UUID, ranchId: UUID): List<AnimalWeightRecord> =
            data[animalId to ranchId].orEmpty()
    }

    private class InMemoryAnimalRepository : AnimalRepositoryPort {
        val records = linkedMapOf<UUID, AnimalRecord>()
        val summaries = mutableListOf<AnimalSummary>()
        val details = mutableMapOf<Pair<UUID, UUID>, AnimalDetail>()

        fun seed(record: AnimalRecord) {
            records[record.id] = record
        }

        override fun searchSummaries(query: ListAnimalsQuery): AnimalPage {
            val filtered = summaries.filter { item ->
                item.ranchId == query.ranchId &&
                    (query.search.isNullOrBlank() ||
                        item.folioInterno.contains(query.search, ignoreCase = true) ||
                        (item.siniigaChip?.contains(query.search, ignoreCase = true) == true) ||
                        (item.registroUno?.contains(query.search, ignoreCase = true) == true)) &&
                    (query.sex == null || item.sex == query.sex) &&
                    (query.lifeStage == null || item.lifeStage == query.lifeStage) &&
                    (query.reproductiveStatus == null || item.reproductiveStatus == query.reproductiveStatus) &&
                    (query.breed.isNullOrBlank() || item.breed.contains(query.breed, ignoreCase = true)) &&
                    (query.status == null || item.status == query.status) &&
                    (query.updatedAfter == null || item.updatedAt.isAfter(query.updatedAfter))
            }
            val start = (query.page * query.size).coerceAtMost(filtered.size)
            val end = (start + query.size).coerceAtMost(filtered.size)
            val totalPages = if (filtered.isEmpty()) 0 else ((filtered.size + query.size - 1) / query.size)
            return AnimalPage(
                items = filtered.subList(start, end),
                page = query.page,
                size = query.size,
                totalElements = filtered.size.toLong(),
                totalPages = totalPages,
            )
        }

        override fun findDetailByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalDetail? =
            details[animalId to ranchId]

        override fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalRecord? =
            records[animalId]?.takeIf { it.ranchId == ranchId }

        override fun findByIdAndRanchIdForUpdate(animalId: UUID, ranchId: UUID): AnimalRecord? =
            findByIdAndRanchId(animalId, ranchId)

        override fun existsById(animalId: UUID): Boolean = records.containsKey(animalId)

        override fun existsByRanchIdAndFolioInterno(ranchId: UUID, folioInterno: String): Boolean =
            records.values.any { it.ranchId == ranchId && it.folioInterno == folioInterno }

        override fun existsByRanchIdAndFolioInternoAndIdNot(
            ranchId: UUID,
            folioInterno: String,
            excludeAnimalId: UUID,
        ): Boolean = records.values.any {
            it.ranchId == ranchId && it.folioInterno == folioInterno && it.id != excludeAnimalId
        }

        override fun existsByRanchIdAndSiniigaChip(ranchId: UUID, siniigaChip: String): Boolean =
            records.values.any { it.ranchId == ranchId && it.siniigaChip == siniigaChip }

        override fun existsByRanchIdAndSiniigaChipAndIdNot(
            ranchId: UUID,
            siniigaChip: String,
            excludeAnimalId: UUID,
        ): Boolean = records.values.any {
            it.ranchId == ranchId && it.siniigaChip == siniigaChip && it.id != excludeAnimalId
        }

        override fun create(animal: AnimalRecord): AnimalRecord {
            records[animal.id] = animal
            return animal
        }

        override fun update(animal: AnimalRecord): AnimalRecord {
            records[animal.id] = animal
            return animal
        }
    }
}
