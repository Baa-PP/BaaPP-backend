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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
    }

    @Test
    fun `RegisterWeightUseCase saves measurement for MALE with scrotal circumference`() {
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
    }

    @Test
    fun `GetAnimalWeightsUseCase returns ordered history and computed GDP`() {
        val lamb = sampleAnimal(sex = Sex.MALE, stage = LifeStage.LAMB_FATTENING, repro = ReproductiveStatus.EMPTY)
        val animalPort = FakeAnimalWeightingPort(lamb)
        val weightPort = FakeWeightRepositoryPort().apply {
            projections += listOf(
                WeightGainProjection(
                    weightId = UUID.randomUUID(),
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
                    weightId = UUID.randomUUID(),
                    animalId = lamb.id,
                    ranchId = ranchId,
                    folioInterno = lamb.folioInterno,
                    milestone = WeightMilestone.WEANING,
                    date = LocalDate.of(2026, 9, 14),
                    ageDays = 75,
                    weightKg = BigDecimal("22.75"),
                    previousWeightKg = BigDecimal("4.00"),
                    daysSincePrevious = 75,
                    gainKg = BigDecimal("18.75"),
                    dailyGainGrams = 250L,
                ),
            )
        }
        val useCase = GetAnimalWeightsUseCase(animalPort, weightPort)

        val history = useCase.execute(ranchId = ranchId, animalId = lamb.id)

        assertEquals(2, history.weights.size)
        assertEquals(WeightMilestone.BIRTH, history.weights[0].milestone)
        assertEquals(WeightMilestone.WEANING, history.weights[1].milestone)
        assertEquals(BigDecimal("0.2500"), history.weights[1].dailyGainKg)
        assertEquals(250L, history.weights[1].dailyGainGrams)
        assertEquals(250L, history.overallDailyGainGrams)
        assertEquals(BigDecimal("22.75"), history.latestWeightKg)
    }

    @Test
    fun `GetAnimalWeightsUseCase throws ResourceNotFoundException when animal is not in ranch`() {
        val useCase = GetAnimalWeightsUseCase(FakeAnimalWeightingPort(), FakeWeightRepositoryPort())
        assertThrows(ResourceNotFoundException::class.java) {
            useCase.execute(ranchId = ranchId, animalId = UUID.randomUUID())
        }
    }

    @Test
    fun `ConfirmWeaningUseCase saves WEANING weight, updates lamb destination and transitions mother to EMPTY`() {
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

        assertEquals(LocalDate.of(2026, 9, 14), updatedLamb.weaningDate)
        assertEquals(LifeStage.EWE_REPLACEMENT, updatedLamb.lifeStage)
        assertEquals(AnimalStatus.ACTIVE, updatedLamb.status)
        assertEquals(ReproductiveStatus.EMPTY, updatedMother.reproductiveStatus)
        assertTrue(result.motherTransitionedToEmpty)
        assertEquals(1, weightPort.saved.size)
        assertEquals(WeightMilestone.WEANING, weightPort.saved.first().milestone)
        assertEquals(BigDecimal("19.80"), weightPort.saved.first().weightKg)
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

        override fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): Animal? =
            store[animalId]?.takeIf { it.ranchId == ranchId }

        override fun save(animal: Animal): Animal {
            store[animal.id] = animal
            return animal
        }
    }

    private class FakeWeightRepositoryPort : WeightRepositoryPort {
        val saved = mutableListOf<WeightMeasurement>()
        val projections = mutableListOf<WeightGainProjection>()

        override fun save(measurement: WeightMeasurement): WeightMeasurement {
            saved += measurement
            return measurement
        }

        override fun findHistoryByAnimalAndRanch(animalId: UUID, ranchId: UUID): List<WeightGainProjection> =
            projections.filter { it.animalId == animalId && it.ranchId == ranchId }
                .sortedWith(compareBy<WeightGainProjection> { it.date }.thenBy { it.weightId })
    }
}
