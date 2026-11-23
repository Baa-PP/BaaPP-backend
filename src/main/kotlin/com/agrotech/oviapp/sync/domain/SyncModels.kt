package com.agrotech.oviapp.sync.domain

import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.domain.exception.BusinessRuleViolationException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.domain.service.BirthAntiFraudPolicy
import com.agrotech.oviapp.weights.domain.WeaningDestination
import com.agrotech.oviapp.weights.domain.WeightMilestone
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class SyncEntityType {
    ANIMAL,
    BIRTH_EVENT,
    WEIGHT_MEASUREMENT,
    WEANING,
}

enum class SyncOperation {
    CREATE,
    UPDATE,
}

enum class SyncMutationStatus {
    APPLIED,
    ALREADY_PROCESSED,
    REJECTED,
}

data class SyncBatchMutation(
    val mutationId: UUID,
    val entityType: SyncEntityType,
    val operation: SyncOperation,
    val occurredAt: Instant,
    val payloadJson: String,
)

data class SyncBatchResultItem(
    val mutationId: UUID,
    val entityType: SyncEntityType,
    val operation: SyncOperation,
    val status: SyncMutationStatus,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

data class SyncBatchCommand(
    val ranchId: UUID,
    val actorUsername: String,
    val actorRole: Role,
    val canEditAnimals: Boolean,
    val mutations: List<SyncBatchMutation>,
)

data class SyncBatchResult(
    val ranchId: UUID,
    val processedCount: Int,
    val appliedCount: Int,
    val alreadyProcessedCount: Int,
    val rejectedCount: Int,
    val results: List<SyncBatchResultItem>,
)

data class SyncAnimalCreatePayload(
    val id: UUID? = null,
    val folioInterno: String,
    val siniigaChip: String? = null,
    val registroUno: String? = null,
    val adn: String? = null,
    val breed: String,
    val color: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val weaningDate: LocalDate? = null,
    val entryReason: EntryReason = EntryReason.BIRTH,
    val criador: String? = null,
    val parturitionType: ParturitionType = ParturitionType.SIMPLE,
    val conceptionMethod: ConceptionMethod = ConceptionMethod.MN,
    val sireId: UUID? = null,
    val damId: UUID? = null,
    val externalSireFolio: String? = null,
    val externalDamFolio: String? = null,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val status: AnimalStatus = AnimalStatus.ACTIVE,
    val mortalityDate: LocalDate? = null,
    val mortalityCause: String? = null,
    val lifeStageAtDeath: LifeStage? = null,
    val photoS3Key: String? = null,
)

data class SyncAnimalUpdatePayload(
    val animalId: UUID,
    val folioInterno: String,
    val siniigaChip: String? = null,
    val registroUno: String? = null,
    val adn: String? = null,
    val breed: String,
    val color: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val weaningDate: LocalDate? = null,
    val entryReason: EntryReason? = null,
    val criador: String? = null,
    val parturitionType: ParturitionType? = null,
    val conceptionMethod: ConceptionMethod? = null,
    val sireId: UUID? = null,
    val damId: UUID? = null,
    val externalSireFolio: String? = null,
    val externalDamFolio: String? = null,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val status: AnimalStatus? = null,
    val mortalityDate: LocalDate? = null,
    val mortalityCause: String? = null,
    val lifeStageAtDeath: LifeStage? = null,
    val photoS3Key: String? = null,
)

data class SyncBirthLambPayload(
    val id: UUID? = null,
    val folioInterno: String,
    val siniigaChip: String? = null,
    val registroUno: String? = null,
    val adn: String? = null,
    val breed: String,
    val color: String,
    val sex: Sex,
    val criador: String? = null,
    val lifeStage: LifeStage = LifeStage.LAMB_LACTATING,
    val reproductiveStatus: ReproductiveStatus = ReproductiveStatus.EMPTY,
    val birthWeightKg: BigDecimal? = null,
    val weightId: UUID? = null,
    val photoS3Key: String? = null,
)

data class SyncBirthCreatePayload(
    val id: UUID? = null,
    val damId: UUID,
    val sireId: UUID? = null,
    val externalSireFolio: String? = null,
    val birthDate: LocalDate,
    val liveBornCount: Int? = null,
    val stillbornCount: Int = 0,
    val conceptionMethod: ConceptionMethod = ConceptionMethod.MN,
    val evidencePhotoS3Key: String? = null,
    val lambs: List<SyncBirthLambPayload> = emptyList(),
)

data class SyncWeightCreatePayload(
    val id: UUID? = null,
    val animalId: UUID,
    val milestone: WeightMilestone,
    val weightKg: BigDecimal,
    val scrotalCircumferenceCm: BigDecimal? = null,
    val date: LocalDate,
)

data class SyncWeaningCreatePayload(
    val animalId: UUID,
    val weightId: UUID? = null,
    val weaningDate: LocalDate,
    val weaningWeightKg: BigDecimal,
    val destination: WeaningDestination,
    val scrotalCircumferenceCm: BigDecimal? = null,
)

data class SyncBirthLambRecord(
    val id: UUID,
    val folioInterno: String,
    val siniigaChip: String?,
    val registroUno: String?,
    val adn: String?,
    val breed: String,
    val color: String,
    val sex: Sex,
    val criador: String?,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val birthWeightKg: BigDecimal?,
    val weightId: UUID?,
    val photoS3Key: String?,
)

data class SyncBirthRecord(
    val id: UUID,
    val ranchId: UUID,
    val damId: UUID,
    val sireId: UUID?,
    val externalSireFolio: String?,
    val birthDate: LocalDate,
    val liveBornCount: Int,
    val stillbornCount: Int,
    val parturitionType: ParturitionType,
    val conceptionMethod: ConceptionMethod,
    val evidencePhotoS3Key: String,
    val registeredByUserId: UUID,
    val transitionDamToLactating: Boolean,
    val lambs: List<SyncBirthLambRecord>,
    val updatedAt: Instant,
)

class DuplicateMutationException(val mutationId: UUID) : RuntimeException("Mutación ya procesada: $mutationId")

object SyncBirthPolicy {
    fun validateInitialRegistration(birthDate: LocalDate, today: LocalDate, evidencePhotoKey: String?) {
        if (evidencePhotoKey.isNullOrBlank()) {
            throw DomainValidationException(
                code = "MISSING_BIRTH_EVIDENCE",
                message = "Evidencia fotográfica obligatoria para registrar un nacimiento.",
            )
        }
        try {
            BirthAntiFraudPolicy.validateInitialRegistration(birthDate, today, evidencePhotoKey)
        } catch (ex: IllegalArgumentException) {
            val daysSinceBirth = ChronoUnit.DAYS.between(birthDate, today)
            throw BusinessRuleViolationException.birthRegistrationWindowClosed(daysSinceBirth)
        }
    }

    fun validateCounts(liveBornCount: Int, stillbornCount: Int, lambsSize: Int) {
        if (liveBornCount < 0 || stillbornCount < 0 || liveBornCount + stillbornCount < 1) {
            throw DomainValidationException(
                code = "INVALID_BIRTH_COUNTS",
                message = "El parto debe registrar al menos una cría nacida viva o muerta sin valores negativos.",
            )
        }
        if (lambsSize != liveBornCount) {
            throw DomainValidationException(
                code = "INVALID_BIRTH_LAMBS_COUNT",
                message = "La cantidad de crías enviadas ($lambsSize) debe coincidir con nacidos vivos ($liveBornCount).",
            )
        }
    }

    fun deduceParturitionType(totalBorn: Int): ParturitionType =
        when (totalBorn) {
            1 -> ParturitionType.SIMPLE
            2 -> ParturitionType.TWIN
            3 -> ParturitionType.TRIPLET
            4 -> ParturitionType.QUADRUPLET
            else -> ParturitionType.QUINTUPLET
        }
}

interface ProcessedMutationPort {
    fun findRanchIdByMutationId(mutationId: UUID): UUID?
    fun recordProcessed(mutationId: UUID, ranchId: UUID, processedAt: Instant)
}

interface SyncPayloadParserPort {
    fun parseAnimalCreate(payloadJson: String): SyncAnimalCreatePayload
    fun parseAnimalUpdate(payloadJson: String): SyncAnimalUpdatePayload
    fun parseBirthCreate(payloadJson: String): SyncBirthCreatePayload
    fun parseWeightCreate(payloadJson: String): SyncWeightCreatePayload
    fun parseWeaningCreate(payloadJson: String): SyncWeaningCreatePayload
}

interface SyncBirthPersistencePort {
    fun existsBirthById(birthId: UUID): Boolean
    fun findUserIdByUsername(username: String): UUID?
    fun createBirth(birth: SyncBirthRecord)
}

interface SyncTransactionPort {
    fun <T> executeInNewTransaction(block: () -> T): T
}
