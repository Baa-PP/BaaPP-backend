package com.agrotech.oviapp.weights.application

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.Animal
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.weights.domain.AnimalWeightingPort
import com.agrotech.oviapp.weights.domain.WeaningDestination
import com.agrotech.oviapp.weights.domain.WeightGainProjection
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.domain.WeightRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class WeightUseCasesTest {

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `RegisterWeightUseCase rejects scrotal circumference for FEMALE animal`() {
        val femaleLamb = sampleAnimal(sex = Sex.FEMALE, stage = LifeStage.LAMB_LACTATING, repro = ReproductiveStatus.EMPTY)
        val animalPort = FakeAnimalWeightingPort(femaleLamb)
        val weightPort = FakeWeightRepositoryPort()
        val useCase = RegisterWeightUseCase(animalPort, weightPort)

        assertThrows(DomainValidationException::class.java) {
            useCase.execute(
                RegisterWeightCommand(
                    ranchId = ranchId,
                    animalId = femaleLamb.id,
                    milestone = WeightMilestone.DAY_120,
                    weightKg = BigDecimal("28.50"),
                    scrotalCircumferenceCm = BigDecimal("21.00"),
                    date = LocalDate.of(2026, 11, 1),
                ),
            )
        }
        assertTrue(weightPort.saved.isEmpty())
        assertTrue(animalPort.touchedIds.isEmpty())
    }

    @Test
    fun `RegisterWeightUseCase rejects duplicate client-supplied weight ID without overwriting`() {
        val maleLamb = sampleAnimal(sex = Sex.MALE, stage = LifeStage.LAMB_FATTENING, repro = ReproductiveStatus.EMPTY)
        val existingId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val existingMeasurement = WeightMeasurement(
            id = existingId,
            animalId = UUID.randomUUID(),
            ranchId = UUID.randomUUID(),
            milestone = WeightMilestone.BIRTH,
            weightKg = BigDecimal("4.20"),
            date = LocalDate.of(2026, 7, 1),
        )
        val animalPort = FakeAnimalWeightingPort(maleLamb)
        val weightPort = FakeWeightRepositoryPort(existingMeasurement)
        val useCase = RegisterWeightUseCase(animalPort, weightPort)

        assertThrows(BusinessConflictException::class.java) {
            useCase.execute(
                RegisterWeightCommand(
                    id = existingId,
                    ranchId = ranchId,
                    animalId = maleLamb.id,
                    milestone = WeightMilestone.EVALUATION,
                    weightKg = BigDecimal("35.00"),
                    date = LocalDate.of(2026, 11, 15),
                ),
            )
        }
        assertEquals(existingMeasurement, weightPort.savedById[existingId])
        assertTrue(animalPort.touchedIds.isEmpty())
    }

    @Test
    fun `RegisterWeightUseCase saves measurement for MALE with scrotal circumference and touches animal updatedAt`() {
        val maleLamb = sampleAnimal(sex = Sex.MALE, stage = LifeStage.LAMB_FATTENING, repro = ReproductiveStatus.EMPTY)
        val animalPort = FakeAnimalWeightingPort(maleLamb)
        val weightPort = FakeWeightRepositoryPort()
        val useCase = RegisterWeightUseCase(animalPort, weightPort)

        val result = useCase.execute(
            RegisterWeightCommand(
                ranchId = ranchId,
                animalId = maleLamb.id,
                milestone = WeightMilestone.DAY_150,
                weightKg = BigDecimal("36.20"),
                scrotalCircumferenceCm = BigDecimal("26.50"),
                date = LocalDate.of(2026, 11, 15),
            ),
        )

        assertEquals(maleLamb.id, result.animalId)
        assertEquals(WeightMilestone.DAY_150, result.milestone)
        assertEquals(BigDecimal("26.50"), result.scrotalCircumferenceCm)
        assertEquals(1, weightPort.saved.size)
        assertEquals(listOf(maleLamb.id to ranchId), animalPort.touchedIds)
    }

    @Test
    fun `GetAnimalWeightsUseCase returns ordered history and recomputes predecessor fields after final sort`() {
        val lamb = sampleAnimal(sex = Sex.MALE, stage = LifeStage.LAMB_FATTENING, repro = ReproductiveStatus.EMPTY)
        val birthId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val sameDayIdA = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val sameDayIdB = UUID.fromString("00000000-0000-0000-0000-000000000003")

        val animalPort = FakeAnimalWeightingPort(lamb)
        val weightPort = FakeWeightRepositoryPort().apply {
            // Simulate LAG from DB returning same-date rows in opposite order of weightId
            projections += listOf(
                WeightGainProjection(
                    weightId = birthId,
                    animalId = lamb.id,
                    ranchId = ranchId,
                    folioInterno = lamb.folioInterno,
                    milestone = WeightMilestone.BIRTH,
                    date = LocalDate.of(2026, 7, 1),
                    ageDays = 0,
                    weightKg = BigDecimal("4.00"),
                    previousWeightKg = null,
                    daysSincePrevious = null,
                    gainKg = null,
                    dailyGainGrams = null,
                ),
                WeightGainProjection(
                    weightId = sameDayIdB,
                    animalId = lamb.id,
                    ranchId = ranchId,
                    folioInterno = lamb.folioInterno,
                    milestone = WeightMilestone.EVALUATION,
                    date = LocalDate.of(2026, 9, 14),
                    ageDays = 75,
                    weightKg = BigDecimal("23.00"),
                    previousWeightKg = BigDecimal("4.00"),
                    daysSincePrevious = 75,
                    gainKg = BigDecimal("19.00"),
                    dailyGainGrams = 253L,
                ),
                WeightGainProjection(
                    weightId = sameDayIdA,
                    animalId = lamb.id,
                    ranchId = ranchId,
                    folioInterno = lamb.folioInterno,
                    milestone = WeightMilestone.WEANING,
                    date = LocalDate.of(2026, 9, 14),
                    ageDays = 75,
                    weightKg = BigDecimal("22.75"),
                    previousWeightKg = BigDecimal("23.00"),
                    daysSincePrevious = 0,
                    gainKg = BigDecimal("-0.25"),
                    dailyGainGrams = null,
                ),
            )
        }
        val useCase = GetAnimalWeightsUseCase(animalPort, weightPort)

        val history = useCase.execute(ranchId = ranchId, animalId = lamb.id)

        assertEquals(3, history.weights.size)
        // Sorted by (date, weightId): birthId -> sameDayIdA -> sameDayIdB
        assertEquals(birthId, history.weights[0].id)
        assertNull(history.weights[0].previousWeightKg)

        assertEquals(sameDayIdA, history.weights[1].id)
        assertEquals(BigDecimal("4.00"), history.weights[1].previousWeightKg)
        assertEquals(75, history.weights[1].daysSincePrevious)
        assertEquals(BigDecimal("18.75"), history.weights[1].gainKg)
        assertEquals(BigDecimal("0.2500"), history.weights[1].dailyGainKg)
        assertEquals(250L, history.weights[1].dailyGainGrams)

        assertEquals(sameDayIdB, history.weights[2].id)
        assertEquals(BigDecimal("22.75"), history.weights[2].previousWeightKg)
        assertEquals(0, history.weights[2].daysSincePrevious)
        assertEquals(BigDecimal("0.25"), history.weights[2].gainKg)
        assertNull(history.weights[2].dailyGainKg)
        assertNull(history.weights[2].dailyGainGrams)

        assertEquals(253L, history.overallDailyGainGrams)
        assertEquals(BigDecimal("23.00"), history.latestWeightKg)
    }

    @Test
    fun `GetAnimalWeightsUseCase throws ResourceNotFoundException when animal is not in ranch`() {
        val useCase = GetAnimalWeightsUseCase(FakeAnimalWeightingPort(), FakeWeightRepositoryPort())
        assertThrows(ResourceNotFoundException::class.java) {
            useCase.execute(ranchId = ranchId, animalId = UUID.randomUUID())
        }
    }

    @Test
    fun `ConfirmWeaningUseCase locks lamb and mother, saves WEANING weight, updates lamb destination and transitions mother to EMPTY`() {
        val mother = sampleAnimal(
            folio = "M-01",
            sex = Sex.FEMALE,
            stage = LifeStage.ADULT,
            repro = ReproductiveStatus.LACTATING,
            birthDate = LocalDate.of(2024, 2, 1),
        )
        val lamb = sampleAnimal(
            folio = "C-01",
            sex = Sex.FEMALE,
            stage = LifeStage.LAMB_LACTATING,
            repro = ReproductiveStatus.EMPTY,
            birthDate = LocalDate.of(2026, 7, 1),
            damId = mother.id,
        )
        val animalPort = FakeAnimalWeightingPort(mother, lamb)
        val weightPort = FakeWeightRepositoryPort()
        val useCase = ConfirmWeaningUseCase(animalPort, weightPort)

        val result = useCase.execute(
            ConfirmWeaningCommand(
                ranchId = ranchId,
                animalId = lamb.id,
                weaningDate = LocalDate.of(2026, 9, 14),
                weaningWeightKg = BigDecimal("19.80"),
                destination = WeaningDestination.EWE_REPLACEMENT,
            ),
        )

        val updatedLamb = animalPort.findByIdAndRanchId(lamb.id, ranchId)!!
        val updatedMother = animalPort.findByIdAndRanchId(mother.id, ranchId)!!

        assertTrue(animalPort.lockedIds.contains(lamb.id))
        assertTrue(animalPort.lockedIds.contains(mother.id))
        assertEquals(LocalDate.of(2026, 9, 14), updatedLamb.weaningDate)
        assertEquals(LifeStage.EWE_REPLACEMENT, updatedLamb.lifeStage)
        assertEquals(AnimalStatus.ACTIVE, updatedLamb.status)
        assertEquals(ReproductiveStatus.EMPTY, updatedMother.reproductiveStatus)
        assertTrue(result.motherTransitionedToEmpty)
        assertEquals(1, weightPort.saved.size)
        assertEquals(WeightMilestone.WEANING, weightPort.saved.first().milestone)
        assertEquals(BigDecimal("19.80"), weightPort.saved.first().weightKg)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `confirmation reuses a registered weaning weight and completes weaning once`(includeWeightId: Boolean) {
        val mother = sampleAnimal(sex = Sex.FEMALE, stage = LifeStage.ADULT, repro = ReproductiveStatus.LACTATING)
        val lamb = sampleAnimal(
            sex = Sex.MALE,
            stage = LifeStage.LAMB_LACTATING,
            repro = ReproductiveStatus.EMPTY,
            damId = mother.id,
        )
        val animals = FakeAnimalWeightingPort(mother, lamb)
        val weights = FakeWeightRepositoryPort()
        val registeredWeight = RegisterWeightUseCase(animals, weights).execute(
            RegisterWeightCommand(
                ranchId = ranchId,
                animalId = lamb.id,
                milestone = WeightMilestone.WEANING,
                weightKg = BigDecimal("21.00"),
                date = LocalDate.of(2026, 9, 14),
            ),
        )
        val command = ConfirmWeaningCommand(
            ranchId = ranchId,
            animalId = lamb.id,
            weaningDate = registeredWeight.date.plusDays(1),
            weaningWeightKg = BigDecimal("22.00"),
            destination = WeaningDestination.LAMB_FATTENING,
            weightId = registeredWeight.id.takeIf { includeWeightId },
        )
        val useCase = ConfirmWeaningUseCase(animals, weights)

        val result = useCase.execute(command)

        assertEquals(registeredWeight, result.weaningWeight)
        assertEquals(registeredWeight.date, result.weaningDate)
        val updatedLamb = animals.findByIdAndRanchId(lamb.id, ranchId)
        assertEquals(registeredWeight.date, updatedLamb?.weaningDate)
        assertEquals(LifeStage.LAMB_FATTENING, updatedLamb?.lifeStage)
        assertEquals(ReproductiveStatus.EMPTY, animals.findByIdAndRanchId(mother.id, ranchId)?.reproductiveStatus)
        assertTrue(result.motherTransitionedToEmpty)
        assertEquals("ANIMAL_ALREADY_WEANED", assertThrows(BusinessConflictException::class.java) {
            useCase.execute(command)
        }.code)
        assertEquals(listOf(registeredWeight), weights.saved)
    }

    @Test
    fun `ConfirmWeaningUseCase rejects EWE_REPLACEMENT for MALE lamb before saving weight`() {
        val maleLamb = sampleAnimal(
            folio = "C-M1",
            sex = Sex.MALE,
            stage = LifeStage.LAMB_LACTATING,
            repro = ReproductiveStatus.EMPTY,
        )
        val animalPort = FakeAnimalWeightingPort(maleLamb)
        val weightPort = FakeWeightRepositoryPort()
        val useCase = ConfirmWeaningUseCase(animalPort, weightPort)

        assertThrows(DomainValidationException::class.java) {
            useCase.execute(
                ConfirmWeaningCommand(
                    ranchId = ranchId,
                    animalId = maleLamb.id,
                    weaningDate = LocalDate.of(2026, 9, 14),
                    weaningWeightKg = BigDecimal("21.00"),
                    destination = WeaningDestination.EWE_REPLACEMENT,
                ),
            )
        }
        assertTrue(weightPort.saved.isEmpty())
        assertTrue(animalPort.touchedIds.isEmpty())
    }

    @Test
    fun `ConfirmWeaningUseCase rejects duplicate client-supplied weightId`() {
        val lamb = sampleAnimal(
            sex = Sex.MALE,
            stage = LifeStage.LAMB_LACTATING,
            repro = ReproductiveStatus.EMPTY,
        )
        val existingId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val existingMeasurement = WeightMeasurement(
            id = existingId,
            animalId = UUID.randomUUID(),
            ranchId = UUID.randomUUID(),
            milestone = WeightMilestone.BIRTH,
            weightKg = BigDecimal("4.00"),
            date = LocalDate.of(2026, 7, 1),
        )
        val useCase = ConfirmWeaningUseCase(
            FakeAnimalWeightingPort(lamb),
            FakeWeightRepositoryPort(existingMeasurement),
        )

        assertThrows(BusinessConflictException::class.java) {
            useCase.execute(
                ConfirmWeaningCommand(
                    ranchId = ranchId,
                    animalId = lamb.id,
                    weaningDate = LocalDate.of(2026, 9, 14),
                    weaningWeightKg = BigDecimal("20.00"),
                    destination = WeaningDestination.LAMB_FATTENING,
                    weightId = existingId,
                ),
            )
        }
    }

    @Test
    fun `ConfirmWeaningUseCase marks lamb SOLD and leaves mother unchanged when already EMPTY`() {
        val mother = sampleAnimal(
            folio = "M-02",
            sex = Sex.FEMALE,
            stage = LifeStage.ADULT,
            repro = ReproductiveStatus.EMPTY,
            birthDate = LocalDate.of(2024, 2, 1),
        )
        val lamb = sampleAnimal(
            folio = "C-02",
            sex = Sex.MALE,
            stage = LifeStage.LAMB_LACTATING,
            repro = ReproductiveStatus.EMPTY,
            birthDate = LocalDate.of(2026, 7, 1),
            damId = mother.id,
        )
        val animalPort = FakeAnimalWeightingPort(mother, lamb)
        val weightPort = FakeWeightRepositoryPort()
        val useCase = ConfirmWeaningUseCase(animalPort, weightPort)

        val result = useCase.execute(
            ConfirmWeaningCommand(
                ranchId = ranchId,
                animalId = lamb.id,
                weaningDate = LocalDate.of(2026, 9, 14),
                weaningWeightKg = BigDecimal("21.00"),
                destination = WeaningDestination.SOLD,
            ),
        )

        val updatedLamb = animalPort.findByIdAndRanchId(lamb.id, ranchId)!!
        assertEquals(AnimalStatus.SOLD, updatedLamb.status)
        assertEquals(LocalDate.of(2026, 9, 14), updatedLamb.weaningDate)
        assertFalse(result.motherTransitionedToEmpty)
    }

    @Test
    fun `ConfirmWeaningUseCase rejects already weaned animal`() {
        val lamb = sampleAnimal(
            sex = Sex.MALE,
            stage = LifeStage.LAMB_FATTENING,
            repro = ReproductiveStatus.EMPTY,
            weaningDate = LocalDate.of(2026, 9, 1),
        )
        val useCase = ConfirmWeaningUseCase(FakeAnimalWeightingPort(lamb), FakeWeightRepositoryPort())

        assertThrows(BusinessConflictException::class.java) {
            useCase.execute(
                ConfirmWeaningCommand(
                    ranchId = ranchId,
                    animalId = lamb.id,
                    weaningDate = LocalDate.of(2026, 9, 14),
                    weaningWeightKg = BigDecimal("20.00"),
                    destination = WeaningDestination.LAMB_FATTENING,
                ),
            )
        }
    }

    private fun sampleAnimal(
        folio: String = "OV-001",
        sex: Sex,
        stage: LifeStage,
        repro: ReproductiveStatus,
        birthDate: LocalDate = LocalDate.of(2026, 7, 1),
        weaningDate: LocalDate? = null,
        damId: UUID? = null,
    ) = Animal(
        id = UUID.randomUUID(),
        ranchId = ranchId,
        folioInterno = folio,
        breed = "Dorper",
        color = "Blanco",
        sex = sex,
        birthDate = birthDate,
        weaningDate = weaningDate,
        damId = damId,
        lifeStage = stage,
        reproductiveStatus = repro,
        status = AnimalStatus.ACTIVE,
    )

    private class FakeAnimalWeightingPort(vararg initial: Animal) : AnimalWeightingPort {
        private val store = initial.associateBy { it.id }.toMutableMap()
        val lockedIds = mutableSetOf<UUID>()
        val touchedIds = mutableListOf<Pair<UUID, UUID>>()

        override fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): Animal? =
            store[animalId]?.takeIf { it.ranchId == ranchId }

        override fun findByIdAndRanchIdForUpdate(animalId: UUID, ranchId: UUID): Animal? {
            lockedIds += animalId
            return findByIdAndRanchId(animalId, ranchId)
        }

        override fun save(animal: Animal): Animal {
            store[animal.id] = animal
            return animal
        }

        override fun touchUpdatedAt(animalId: UUID, ranchId: UUID) {
            touchedIds += animalId to ranchId
        }
    }

    private class FakeWeightRepositoryPort(vararg initial: WeightMeasurement) : WeightRepositoryPort {
        val saved = mutableListOf<WeightMeasurement>()
        val savedById = initial.associateBy { it.id }.toMutableMap()
        val projections = mutableListOf<WeightGainProjection>()

        override fun existsById(weightId: UUID): Boolean = savedById.containsKey(weightId)

        override fun findWeaningForAnimal(animalId: UUID, ranchId: UUID): List<WeightMeasurement> =
            savedById.values
                .filter { it.animalId == animalId && it.ranchId == ranchId && it.milestone == WeightMilestone.WEANING }
                .sortedWith(compareByDescending<WeightMeasurement> { it.date }.thenByDescending { it.id })

        override fun create(measurement: WeightMeasurement): WeightMeasurement {
            if (savedById.containsKey(measurement.id)) {
                throw BusinessConflictException("WEIGHT_ID_ALREADY_EXISTS", "Ya existe un pesaje con ese id.")
            }
            saved += measurement
            savedById[measurement.id] = measurement
            return measurement
        }

        override fun findHistoryByAnimalAndRanch(animalId: UUID, ranchId: UUID): List<WeightGainProjection> =
            projections.filter { it.animalId == animalId && it.ranchId == ranchId }
                .sortedWith(compareBy<WeightGainProjection> { it.date }.thenBy { it.weightId })
    }
}
