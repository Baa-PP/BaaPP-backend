package com.agrotech.oviapp.sync.application

import com.agrotech.oviapp.animals.application.CreateAnimalUseCase
import com.agrotech.oviapp.animals.application.UpdateAnimalUseCase
import com.agrotech.oviapp.animals.domain.AnimalDetail
import com.agrotech.oviapp.animals.domain.AnimalPage
import com.agrotech.oviapp.animals.domain.AnimalRecord
import com.agrotech.oviapp.animals.domain.AnimalRepositoryPort
import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.animals.domain.ListAnimalsQuery
import com.agrotech.oviapp.domain.model.Animal
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.sync.domain.DuplicateMutationException
import com.agrotech.oviapp.sync.domain.ProcessedMutationPort
import com.agrotech.oviapp.sync.domain.SyncBatchCommand
import com.agrotech.oviapp.sync.domain.SyncBatchMutation
import com.agrotech.oviapp.sync.domain.SyncBirthPersistencePort
import com.agrotech.oviapp.sync.domain.SyncBirthRecord
import com.agrotech.oviapp.sync.domain.SyncEntityType
import com.agrotech.oviapp.sync.domain.SyncMutationStatus
import com.agrotech.oviapp.sync.domain.SyncOperation
import com.agrotech.oviapp.sync.domain.SyncTransactionPort
import com.agrotech.oviapp.sync.infrastructure.SyncPayloadParserAdapter
import com.agrotech.oviapp.weights.application.ConfirmWeaningUseCase
import com.agrotech.oviapp.weights.application.RegisterWeightUseCase
import com.agrotech.oviapp.weights.domain.AnimalWeightingPort
import com.agrotech.oviapp.weights.domain.WeightGainProjection
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.domain.WeightRepositoryPort
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class ProcessSyncBatchUseCaseTest {

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val userId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val fixedInstant = Instant.parse("2026-10-15T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private lateinit var store: InMemorySyncStore
    private lateinit var useCase: ProcessSyncBatchUseCase

    @BeforeEach
    fun setUp() {
        store = InMemorySyncStore(userId)
        val createAnimal = CreateAnimalUseCase(store.animalPort)
        val updateAnimal = UpdateAnimalUseCase(store.animalPort)
        val registerWeight = RegisterWeightUseCase(store.weightingPort, store.weightRepo)
        val confirmWeaning = ConfirmWeaningUseCase(store.weightingPort, store.weightRepo)
        useCase = ProcessSyncBatchUseCase(
            processedMutationPort = store,
            payloadParser = SyncPayloadParserAdapter(mapper),
            transactionPort = store,
            createAnimalUseCase = createAnimal,
            updateAnimalUseCase = updateAnimal,
            registerWeightUseCase = registerWeight,
            confirmWeaningUseCase = confirmWeaning,
            animalRepository = store.animalPort,
            weightRepository = store.weightRepo,
            birthPersistencePort = store,
            clock = fixedClock,
        )
    }

    @Test
    fun `processes ordered batch end to end across animal create, birth with lambs, weight, weaning, and disposal`() {
        val damId = UUID.randomUUID()
        val birthId = UUID.randomUUID()
        val lambId = UUID.randomUUID()
        val birthWeightId = UUID.randomUUID()
        val weaningWeightId = UUID.randomUUID()

        val damCreateJson = """
            {
              "id": "$damId",
              "folioInterno": "MADRE-01",
              "breed": "Dorper",
              "color": "Blanco",
              "sex": "FEMALE",
              "birthDate": "2024-03-01",
              "entryReason": "PURCHASE",
              "lifeStage": "ADULT",
              "reproductiveStatus": "PREGNANT"
            }
        """.trimIndent()

        val birthCreateJson = """
            {
              "id": "$birthId",
              "damId": "$damId",
              "birthDate": "2026-10-10",
              "liveBornCount": 1,
              "stillbornCount": 1,
              "conceptionMethod": "MN",
              "evidencePhotoS3Key": "$ranchId/births/parto-1.webp",
              "lambs": [
                {
                  "id": "$lambId",
                  "folioInterno": "CRIA-01",
                  "breed": "Dorper",
                  "color": "Blanco",
                  "sex": "MALE",
                  "birthWeightKg": 3.80,
                  "weightId": "$birthWeightId"
                }
              ]
            }
        """.trimIndent()

        val weightCreateJson = """
            {
              "id": "${UUID.randomUUID()}",
              "animalId": "$lambId",
              "milestone": "DAY_120",
              "weightKg": 24.50,
              "scrotalCircumferenceCm": 21.00,
              "date": "2026-10-14"
            }
        """.trimIndent()

        val weaningCreateJson = """
            {
              "animalId": "$lambId",
              "weightId": "$weaningWeightId",
              "weaningDate": "2026-10-15",
              "weaningWeightKg": 19.20,
              "destination": "LAMB_FATTENING",
              "scrotalCircumferenceCm": 22.00
            }
        """.trimIndent()

        val disposalUpdateJson = """
            {
              "animalId": "$lambId",
              "folioInterno": "CRIA-01",
              "breed": "Dorper",
              "color": "Blanco",
              "sex": "MALE",
              "birthDate": "2026-10-10",
              "weaningDate": "2026-10-15",
              "lifeStage": "LAMB_FATTENING",
              "reproductiveStatus": "EMPTY",
              "status": "SOLD"
            }
        """.trimIndent()

        val m1 = UUID.randomUUID()
        val m2 = UUID.randomUUID()
        val m3 = UUID.randomUUID()
        val m4 = UUID.randomUUID()
        val m5 = UUID.randomUUID()

        val result = useCase.execute(
            SyncBatchCommand(
                ranchId = ranchId,
                actorUsername = "owner_1",
                actorRole = Role.OWNER,
                canEditAnimals = true,
                mutations = listOf(
                    mutation(m1, SyncEntityType.ANIMAL, SyncOperation.CREATE, damCreateJson),
                    mutation(m2, SyncEntityType.BIRTH_EVENT, SyncOperation.CREATE, birthCreateJson),
                    mutation(m3, SyncEntityType.WEIGHT_MEASUREMENT, SyncOperation.CREATE, weightCreateJson),
                    mutation(m4, SyncEntityType.WEANING, SyncOperation.CREATE, weaningCreateJson),
                    mutation(m5, SyncEntityType.ANIMAL, SyncOperation.UPDATE, disposalUpdateJson),
                ),
            ),
        )

        assertEquals(5, result.processedCount)
        assertEquals(5, result.appliedCount)
        assertEquals(0, result.alreadyProcessedCount)
        assertEquals(0, result.rejectedCount)
        assertTrue(result.results.all { it.status == SyncMutationStatus.APPLIED })

        val savedLamb = store.animals[lambId]!!
        assertEquals(birthId, savedLamb.birthEventId)
        assertEquals(ParturitionType.TWIN, savedLamb.parturitionType)
        assertEquals(LifeStage.LAMB_FATTENING, savedLamb.lifeStage)
        assertEquals(AnimalStatus.SOLD, savedLamb.status)
        assertEquals(LocalDate.of(2026, 10, 15), savedLamb.weaningDate)

        // Dam transitioned to LACTATING on birth, then back to EMPTY on weaning.
        assertEquals(ReproductiveStatus.EMPTY, store.animals[damId]!!.reproductiveStatus)
        assertEquals(3, store.weights.size)
        assertEquals(5, store.processedMutations.size)
    }

    @Test
    fun `resending already processed mutationId returns ALREADY_PROCESSED without duplicating rows`() {
        val animalId = UUID.randomUUID()
        val mutationId = UUID.randomUUID()
        val createJson = """
            {
              "id": "$animalId",
              "folioInterno": "OV-100",
              "breed": "Katahdin",
              "color": "Canelo",
              "sex": "FEMALE",
              "birthDate": "2026-09-20",
              "lifeStage": "LAMB_LACTATING",
              "reproductiveStatus": "EMPTY"
            }
        """.trimIndent()

        val first = useCase.execute(
            SyncBatchCommand(
                ranchId = ranchId,
                actorUsername = "owner_1",
                actorRole = Role.OWNER,
                canEditAnimals = true,
                mutations = listOf(mutation(mutationId, SyncEntityType.ANIMAL, SyncOperation.CREATE, createJson)),
            ),
        )
        assertEquals(1, first.appliedCount)

        // Resend same mutationId twice in next batch
        val second = useCase.execute(
            SyncBatchCommand(
                ranchId = ranchId,
                actorUsername = "owner_1",
                actorRole = Role.OWNER,
                canEditAnimals = true,
                mutations = listOf(
                    mutation(mutationId, SyncEntityType.ANIMAL, SyncOperation.CREATE, createJson),
                    mutation(mutationId, SyncEntityType.ANIMAL, SyncOperation.CREATE, createJson),
                ),
            ),
        )

        assertEquals(0, second.appliedCount)
        assertEquals(2, second.alreadyProcessedCount)
        assertEquals(0, second.rejectedCount)
        assertEquals(1, store.animals.size)
    }

    @Test
    fun `rejects out-of-order weight before animal, closed 30-day birth, missing edit permission, and cross-ranch mutationId while isolating transactions`() {
        val damId = UUID.randomUUID()
        store.animals[damId] = AnimalRecord(
            id = damId,
            ranchId = ranchId,
            folioInterno = "MADRE-OK",
            breed = "Dorper",
            color = "Blanco",
            sex = Sex.FEMALE,
            birthDate = LocalDate.of(2024, 1, 1),
            lifeStage = LifeStage.ADULT,
            reproductiveStatus = ReproductiveStatus.PREGNANT,
            status = AnimalStatus.ACTIVE,
            updatedAt = fixedInstant,
        )

        val foreignMutationId = UUID.randomUUID()
        store.processedMutations[foreignMutationId] = otherRanchId

        val oldBirthMutation = UUID.randomUUID()
        val missingAnimalWeightMutation = UUID.randomUUID()
        val birthUpdateMutation = UUID.randomUUID()
        val unauthorizedUpdateMutation = UUID.randomUUID()
        val validAnimalId = UUID.randomUUID()
        val validMutation = UUID.randomUUID()

        val oldBirthJson = """
            {
              "damId": "$damId",
              "birthDate": "2026-09-01",
              "liveBornCount": 1,
              "stillbornCount": 0,
              "evidencePhotoS3Key": "$ranchId/births/old.webp",
              "lambs": [
                {
                  "folioInterno": "LATE-01",
                  "breed": "Dorper",
                  "color": "Blanco",
                  "sex": "FEMALE"
                }
              ]
            }
        """.trimIndent()

        val orphanWeightJson = """
            {
              "animalId": "${UUID.randomUUID()}",
              "milestone": "DAY_120",
              "weightKg": 20.00,
              "date": "2026-10-14"
            }
        """.trimIndent()

        val updateDamJson = """
            {
              "animalId": "$damId",
              "folioInterno": "MADRE-OK",
              "breed": "Dorper",
              "color": "Blanco",
              "sex": "FEMALE",
              "birthDate": "2024-01-01",
              "lifeStage": "ADULT",
              "reproductiveStatus": "EMPTY"
            }
        """.trimIndent()

        val validAnimalJson = """
            {
              "id": "$validAnimalId",
              "folioInterno": "OK-01",
              "breed": "Dorper",
              "color": "Blanco",
              "sex": "FEMALE",
              "birthDate": "2026-10-01",
              "lifeStage": "LAMB_LACTATING",
              "reproductiveStatus": "EMPTY"
            }
        """.trimIndent()

        val result = useCase.execute(
            SyncBatchCommand(
                ranchId = ranchId,
                actorUsername = "worker_capture_only",
                actorRole = Role.WORKER_BRANCH,
                canEditAnimals = false,
                mutations = listOf(
                    mutation(foreignMutationId, SyncEntityType.ANIMAL, SyncOperation.CREATE, validAnimalJson),
                    mutation(oldBirthMutation, SyncEntityType.BIRTH_EVENT, SyncOperation.CREATE, oldBirthJson),
                    mutation(missingAnimalWeightMutation, SyncEntityType.WEIGHT_MEASUREMENT, SyncOperation.CREATE, orphanWeightJson),
                    mutation(birthUpdateMutation, SyncEntityType.BIRTH_EVENT, SyncOperation.UPDATE, oldBirthJson),
                    mutation(unauthorizedUpdateMutation, SyncEntityType.ANIMAL, SyncOperation.UPDATE, updateDamJson),
                    mutation(validMutation, SyncEntityType.ANIMAL, SyncOperation.CREATE, validAnimalJson),
                ),
            ),
        )

        assertEquals(6, result.processedCount)
        assertEquals(1, result.appliedCount)
        assertEquals(5, result.rejectedCount)

        assertEquals("MUTATION_RANCH_CONFLICT", result.results[0].errorCode)
        assertEquals("VENTANA_PARTO_CERRADA", result.results[1].errorCode)
        assertEquals("ANIMAL_NOT_FOUND", result.results[2].errorCode)
        assertEquals("BIRTH_IMMUTABLE", result.results[3].errorCode)
        assertEquals("INSUFFICIENT_PERMISSION", result.results[4].errorCode)
        assertEquals(SyncMutationStatus.APPLIED, result.results[5].status)
        assertNull(result.results[5].errorCode)

        // Rejected mutations must NOT leave partial records or processed_mutations entries.
        assertEquals(2, store.animals.size)
        assertTrue(store.births.isEmpty())
        assertTrue(store.processedMutations.containsKey(validMutation))
        assertTrue(!store.processedMutations.containsKey(oldBirthMutation))
    }

    @Test
    fun `concurrent duplicate mutation race rolls back inner transaction and returns ALREADY_PROCESSED`() {
        val mutationId = UUID.randomUUID()
        val animalId = UUID.randomUUID()
        val json = """
            {
              "id": "$animalId",
              "folioInterno": "RACE-01",
              "breed": "Dorper",
              "color": "Blanco",
              "sex": "MALE",
              "birthDate": "2026-10-05",
              "lifeStage": "LAMB_LACTATING",
              "reproductiveStatus": "EMPTY"
            }
        """.trimIndent()

        store.simulateRaceOnNextRecordProcessed = true

        val result = useCase.execute(
            SyncBatchCommand(
                ranchId = ranchId,
                actorUsername = "owner_1",
                actorRole = Role.OWNER,
                canEditAnimals = true,
                mutations = listOf(mutation(mutationId, SyncEntityType.ANIMAL, SyncOperation.CREATE, json)),
            ),
        )

        assertEquals(1, result.alreadyProcessedCount)
        assertEquals(SyncMutationStatus.ALREADY_PROCESSED, result.results.single().status)
        // Rolled back in our transaction simulator so no duplicate animal was persisted.
        assertTrue(store.animals.isEmpty())
    }

    private fun mutation(
        id: UUID,
        entityType: SyncEntityType,
        operation: SyncOperation,
        payloadJson: String,
    ) = SyncBatchMutation(
        mutationId = id,
        entityType = entityType,
        operation = operation,
        occurredAt = fixedInstant,
        payloadJson = payloadJson,
    )

    private class InMemorySyncStore(
        private val userId: UUID,
    ) : ProcessedMutationPort,
        SyncTransactionPort,
        SyncBirthPersistencePort {

        val processedMutations = linkedMapOf<UUID, UUID>()
        val animals = linkedMapOf<UUID, AnimalRecord>()
        val births = linkedMapOf<UUID, SyncBirthRecord>()
        val weights = linkedMapOf<UUID, WeightMeasurement>()
        var simulateRaceOnNextRecordProcessed = false

        val animalPort = object : AnimalRepositoryPort {
            override fun searchSummaries(query: ListAnimalsQuery): AnimalPage =
                AnimalPage(emptyList(), query.page, query.size, 0, 0)

            override fun findDetailByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalDetail? = null

            override fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalRecord? =
                animals[animalId]?.takeIf { it.ranchId == ranchId }

            override fun findByIdAndRanchIdForUpdate(animalId: UUID, ranchId: UUID): AnimalRecord? =
                findByIdAndRanchId(animalId, ranchId)

            override fun existsById(animalId: UUID): Boolean = animals.containsKey(animalId)

            override fun existsByRanchIdAndFolioInterno(ranchId: UUID, folioInterno: String): Boolean =
                animals.values.any { it.ranchId == ranchId && it.folioInterno == folioInterno }

            override fun existsByRanchIdAndFolioInternoAndIdNot(
                ranchId: UUID,
                folioInterno: String,
                excludeAnimalId: UUID,
            ): Boolean = animals.values.any {
                it.ranchId == ranchId && it.folioInterno == folioInterno && it.id != excludeAnimalId
            }

            override fun existsByRanchIdAndSiniigaChip(ranchId: UUID, siniigaChip: String): Boolean =
                animals.values.any { it.ranchId == ranchId && it.siniigaChip == siniigaChip }

            override fun existsByRanchIdAndSiniigaChipAndIdNot(
                ranchId: UUID,
                siniigaChip: String,
                excludeAnimalId: UUID,
            ): Boolean = animals.values.any {
                it.ranchId == ranchId && it.siniigaChip == siniigaChip && it.id != excludeAnimalId
            }

            override fun hasDependentRecordsAsSire(ranchId: UUID, animalId: UUID): Boolean =
                animals.values.any { it.ranchId == ranchId && it.sireId == animalId } ||
                    births.values.any { it.ranchId == ranchId && it.sireId == animalId }

            override fun hasDependentRecordsAsDam(ranchId: UUID, animalId: UUID): Boolean =
                animals.values.any { it.ranchId == ranchId && it.damId == animalId } ||
                    births.values.any { it.ranchId == ranchId && it.damId == animalId }

            override fun create(animal: AnimalRecord): AnimalRecord {
                animals[animal.id] = animal
                return animal
            }

            override fun update(animal: AnimalRecord): AnimalRecord {
                animals[animal.id] = animal
                return animal
            }
        }

        val weightingPort = object : AnimalWeightingPort {
            override fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): Animal? =
                animals[animalId]?.takeIf { it.ranchId == ranchId }?.toDomain()

            override fun findByIdAndRanchIdForUpdate(animalId: UUID, ranchId: UUID): Animal? =
                findByIdAndRanchId(animalId, ranchId)

            override fun save(animal: Animal): Animal {
                val existing = animals[animal.id]!!
                animals[animal.id] = existing.copy(
                    weaningDate = animal.weaningDate,
                    lifeStage = animal.lifeStage,
                    reproductiveStatus = animal.reproductiveStatus,
                    status = animal.status,
                )
                return animal
            }

            override fun touchUpdatedAt(animalId: UUID, ranchId: UUID) {
                animals[animalId]?.let { animals[animalId] = it.copy(updatedAt = Instant.now()) }
            }
        }

        val weightRepo = object : WeightRepositoryPort {
            override fun existsById(weightId: UUID): Boolean = weights.containsKey(weightId)

            override fun findWeaningForAnimal(animalId: UUID, ranchId: UUID): List<WeightMeasurement> =
                weights.values
                    .filter { it.animalId == animalId && it.ranchId == ranchId && it.milestone == WeightMilestone.WEANING }
                    .sortedWith(compareByDescending<WeightMeasurement> { it.date }.thenByDescending { it.id })

            override fun create(measurement: WeightMeasurement): WeightMeasurement {
                weights[measurement.id] = measurement
                return measurement
            }

            override fun findHistoryByAnimalAndRanch(animalId: UUID, ranchId: UUID): List<WeightGainProjection> = emptyList()
        }

        override fun findRanchIdByMutationId(mutationId: UUID): UUID? = processedMutations[mutationId]

        override fun recordProcessed(mutationId: UUID, ranchId: UUID, processedAt: Instant) {
            if (simulateRaceOnNextRecordProcessed) {
                simulateRaceOnNextRecordProcessed = false
                processedMutations[mutationId] = ranchId
                throw DuplicateMutationException(mutationId)
            }
            if (processedMutations.containsKey(mutationId)) {
                throw DuplicateMutationException(mutationId)
            }
            processedMutations[mutationId] = ranchId
        }

        override fun <T> executeInNewTransaction(block: () -> T): T {
            val animalsBackup = animals.toMap()
            val birthsBackup = births.toMap()
            val weightsBackup = weights.toMap()
            val processedBackup = processedMutations.toMap()
            return try {
                block()
            } catch (ex: RuntimeException) {
                val raceWinnerRanch = if (ex is DuplicateMutationException) processedMutations[ex.mutationId] else null
                animals.clear()
                animals.putAll(animalsBackup)
                births.clear()
                births.putAll(birthsBackup)
                weights.clear()
                weights.putAll(weightsBackup)
                processedMutations.clear()
                processedMutations.putAll(processedBackup)
                if (ex is DuplicateMutationException && raceWinnerRanch != null) {
                    processedMutations[ex.mutationId] = raceWinnerRanch
                }
                throw ex
            }
        }

        override fun existsBirthById(birthId: UUID): Boolean = births.containsKey(birthId)

        override fun findUserIdByUsername(username: String): UUID = userId

        override fun createBirth(birth: SyncBirthRecord) {
            births[birth.id] = birth
            birth.lambs.forEach { lamb ->
                animals[lamb.id] = AnimalRecord(
                    id = lamb.id,
                    ranchId = birth.ranchId,
                    folioInterno = lamb.folioInterno,
                    siniigaChip = lamb.siniigaChip,
                    registroUno = lamb.registroUno,
                    adn = lamb.adn,
                    breed = lamb.breed,
                    color = lamb.color,
                    sex = lamb.sex,
                    birthDate = birth.birthDate,
                    entryReason = EntryReason.BIRTH,
                    criador = lamb.criador,
                    parturitionType = birth.parturitionType,
                    conceptionMethod = birth.conceptionMethod,
                    sireId = birth.sireId,
                    damId = birth.damId,
                    externalSireFolio = birth.externalSireFolio,
                    lifeStage = lamb.lifeStage,
                    reproductiveStatus = lamb.reproductiveStatus,
                    status = AnimalStatus.ACTIVE,
                    photoS3Key = lamb.photoS3Key,
                    birthEventId = birth.id,
                    updatedAt = birth.updatedAt,
                )
                if (lamb.birthWeightKg != null && lamb.weightId != null) {
                    weights[lamb.weightId] = WeightMeasurement(
                        id = lamb.weightId,
                        animalId = lamb.id,
                        ranchId = birth.ranchId,
                        milestone = WeightMilestone.BIRTH,
                        weightKg = lamb.birthWeightKg,
                        date = birth.birthDate,
                    )
                }
            }
            if (birth.transitionDamToLactating) {
                val dam = animals[birth.damId]!!
                animals[birth.damId] = dam.copy(reproductiveStatus = ReproductiveStatus.LACTATING)
            }
        }

        private fun AnimalRecord.toDomain() = Animal(
            id = id,
            ranchId = ranchId,
            folioInterno = folioInterno,
            siniigaChip = siniigaChip,
            registroUno = registroUno,
            adn = adn,
            breed = breed,
            color = color,
            sex = sex,
            birthDate = birthDate,
            weaningDate = weaningDate,
            parturitionType = parturitionType,
            conceptionMethod = conceptionMethod,
            sireId = sireId,
            damId = damId,
            externalSireFolio = externalSireFolio,
            externalDamFolio = externalDamFolio,
            lifeStage = lifeStage,
            reproductiveStatus = reproductiveStatus,
            status = status,
        )
    }
}
