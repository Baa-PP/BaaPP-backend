package com.agrotech.oviapp.animals.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class EntryReason {
    BIRTH,
    PURCHASE,
    REENTRY,
    TRANSFER,
    OTHER,
}

data class AnimalRecord(
    val id: UUID,
    val ranchId: UUID,
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
    val birthEventId: UUID? = null,
    val updatedAt: Instant,
)

data class AnimalSummary(
    val id: UUID,
    val ranchId: UUID,
    val folioInterno: String,
    val siniigaChip: String?,
    val registroUno: String?,
    val sex: Sex,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val breed: String,
    val color: String,
    val birthDate: LocalDate,
    val ageDays: Int,
    val weaningDate: LocalDate?,
    val entryReason: EntryReason,
    val criador: String?,
    val propietario: String,
    val photoS3Key: String?,
    val latestWeightKg: BigDecimal?,
    val latestWeightDate: LocalDate?,
    val birthCount: Int,
    val pregnant: Boolean,
    val status: AnimalStatus,
    val mortalityDate: LocalDate?,
    val mortalityCause: String?,
    val sireId: UUID?,
    val damId: UUID?,
    val updatedAt: Instant,
)

data class AnimalWeightRecord(
    val id: UUID,
    val milestone: String,
    val weightKg: BigDecimal,
    val scrotalCircumferenceCm: BigDecimal?,
    val date: LocalDate,
)

data class AnimalDetail(
    val id: UUID,
    val ranchId: UUID,
    val folioInterno: String,
    val siniigaChip: String?,
    val registroUno: String?,
    val adn: String?,
    val breed: String,
    val color: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val ageDays: Int,
    val weaningDate: LocalDate?,
    val entryReason: EntryReason,
    val criador: String?,
    val propietario: String,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val status: AnimalStatus,
    val parturitionType: ParturitionType,
    val conceptionMethod: ConceptionMethod,
    val pregnant: Boolean,
    val birthCount: Int,
    val mortalityDate: LocalDate?,
    val mortalityCause: String?,
    val lifeStageAtDeath: LifeStage?,
    val sireId: UUID?,
    val damId: UUID?,
    val externalSireFolio: String?,
    val externalDamFolio: String?,
    val birthEventId: UUID?,
    val latestWeightKg: BigDecimal?,
    val latestWeightDate: LocalDate?,
    val weights: List<AnimalWeightRecord>,
    val photoS3Key: String?,
    val photoUrl: String?,
    val updatedAt: Instant,
)

data class ListAnimalsQuery(
    val ranchId: UUID,
    val search: String? = null,
    val sex: Sex? = null,
    val lifeStage: LifeStage? = null,
    val reproductiveStatus: ReproductiveStatus? = null,
    val breed: String? = null,
    val status: AnimalStatus? = null,
    val updatedAfter: Instant? = null,
    val page: Int = 0,
    val size: Int = 20,
)

data class AnimalPage(
    val items: List<AnimalSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class MortalityResolution(
    val mortalityDate: LocalDate?,
    val mortalityCause: String?,
    val lifeStageAtDeath: LifeStage?,
)

data class NormalizedAnimalTextFields(
    val folioInterno: String,
    val siniigaChip: String?,
    val registroUno: String?,
    val adn: String?,
    val breed: String,
    val color: String,
    val criador: String?,
    val externalSireFolio: String?,
    val externalDamFolio: String?,
)

object AnimalPolicy {
    const val MAX_FOLIO_LENGTH = 60
    const val MAX_BREED_LENGTH = 80
    const val MAX_COLOR_LENGTH = 60
    const val MAX_OPTIONAL_CODE_LENGTH = 60
    const val MAX_CRIADOR_LENGTH = 150
    const val MIN_PAGE_SIZE = 1
    const val MAX_PAGE_SIZE = 100

    private val SINIIGA_CHIP_REGEX = Regex("^484\\d{7,12}$")

    fun normalizeTextFields(
        folioInterno: String,
        siniigaChip: String?,
        registroUno: String?,
        adn: String?,
        breed: String,
        color: String,
        criador: String?,
        externalSireFolio: String?,
        externalDamFolio: String?,
    ): NormalizedAnimalTextFields = NormalizedAnimalTextFields(
        folioInterno = normalizeRequiredText(folioInterno, MAX_FOLIO_LENGTH, "INVALID_FOLIO_INTERNO", "folioInterno"),
        siniigaChip = normalizeAndValidateSiniigaChip(siniigaChip),
        registroUno = normalizeOptionalText(registroUno, MAX_OPTIONAL_CODE_LENGTH, "INVALID_REGISTRO_UNO", "registroUno"),
        adn = normalizeOptionalText(adn, MAX_OPTIONAL_CODE_LENGTH, "INVALID_ADN", "adn"),
        breed = normalizeRequiredText(breed, MAX_BREED_LENGTH, "INVALID_BREED", "breed"),
        color = normalizeRequiredText(color, MAX_COLOR_LENGTH, "INVALID_COLOR", "color"),
        criador = normalizeOptionalText(criador, MAX_CRIADOR_LENGTH, "INVALID_CRIADOR", "criador"),
        externalSireFolio = normalizeOptionalText(
            externalSireFolio,
            MAX_OPTIONAL_CODE_LENGTH,
            "INVALID_EXTERNAL_SIRE_FOLIO",
            "externalSireFolio",
        ),
        externalDamFolio = normalizeOptionalText(
            externalDamFolio,
            MAX_OPTIONAL_CODE_LENGTH,
            "INVALID_EXTERNAL_DAM_FOLIO",
            "externalDamFolio",
        ),
    )

    fun normalizeRequiredText(
        raw: String,
        maxLength: Int,
        code: String,
        fieldLabel: String,
    ): String {
        val normalized = raw.trim()
        if (normalized.isEmpty()) {
            throw DomainValidationException(code, "El campo $fieldLabel es obligatorio.")
        }
        if (normalized.length > maxLength) {
            throw DomainValidationException(code, "El campo $fieldLabel no puede exceder $maxLength caracteres.")
        }
        return normalized
    }

    fun normalizeOptionalText(
        raw: String?,
        maxLength: Int,
        code: String,
        fieldLabel: String,
    ): String? {
        val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalized.length > maxLength) {
            throw DomainValidationException(code, "El campo $fieldLabel no puede exceder $maxLength caracteres.")
        }
        return normalized
    }

    fun normalizeAndValidateSiniigaChip(rawChip: String?): String? {
        val normalized = rawChip?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!SINIIGA_CHIP_REGEX.matches(normalized)) {
            throw DomainValidationException(
                code = "INVALID_SINIIGA_CHIP",
                message = "El chip SINIIGA debe tener entre 10 y 15 dígitos numéricos iniciando con 484.",
            )
        }
        return normalized
    }

    fun validateSexAndStates(
        sex: Sex,
        lifeStage: LifeStage,
        reproductiveStatus: ReproductiveStatus,
    ) {
        if (sex == Sex.MALE && (reproductiveStatus == ReproductiveStatus.PREGNANT || reproductiveStatus == ReproductiveStatus.LACTATING)) {
            throw DomainValidationException(
                code = "INVALID_REPRODUCTIVE_STATUS",
                message = "Un ejemplar macho (MALE) no puede tener estado reproductivo $reproductiveStatus.",
            )
        }
        if (sex == Sex.FEMALE && reproductiveStatus == ReproductiveStatus.STUD_RAM) {
            throw DomainValidationException(
                code = "INVALID_REPRODUCTIVE_STATUS",
                message = "Un ejemplar hembra (FEMALE) no puede tener estado reproductivo STUD_RAM.",
            )
        }
        if (sex == Sex.MALE && lifeStage == LifeStage.EWE_REPLACEMENT) {
            throw DomainValidationException(
                code = "INVALID_LIFE_STAGE",
                message = "Un ejemplar macho (MALE) no puede tener etapa de vida EWE_REPLACEMENT.",
            )
        }
    }

    fun validateDates(
        birthDate: LocalDate,
        weaningDate: LocalDate?,
        mortalityDate: LocalDate?,
    ) {
        if (weaningDate != null && weaningDate.isBefore(birthDate)) {
            throw DomainValidationException(
                code = "INVALID_WEANING_DATE",
                message = "La fecha de destete no puede ser anterior a la fecha de nacimiento.",
            )
        }
        if (mortalityDate != null && mortalityDate.isBefore(birthDate)) {
            throw DomainValidationException(
                code = "INVALID_MORTALITY_DATE",
                message = "La fecha de muerte no puede ser anterior a la fecha de nacimiento.",
            )
        }
    }

    fun validateMortality(
        status: AnimalStatus,
        sex: Sex,
        birthDate: LocalDate,
        mortalityDate: LocalDate?,
        mortalityCause: String?,
        lifeStageAtDeath: LifeStage?,
        defaultLifeStageAtDeath: LifeStage,
    ): MortalityResolution {
        val resolvedCause = mortalityCause?.trim()?.takeIf { it.isNotEmpty() }
        if (status == AnimalStatus.DECEASED) {
            if (mortalityDate == null || resolvedCause == null) {
                throw DomainValidationException(
                    code = "MORTALITY_INFO_REQUIRED",
                    message = "Un ejemplar con estado DECEASED requiere fecha de muerte y causa de muerte.",
                )
            }
            if (mortalityDate.isBefore(birthDate)) {
                throw DomainValidationException(
                    code = "INVALID_MORTALITY_DATE",
                    message = "La fecha de muerte no puede ser anterior a la fecha de nacimiento.",
                )
            }
            val resolvedStageAtDeath = lifeStageAtDeath ?: defaultLifeStageAtDeath
            if (sex == Sex.MALE && resolvedStageAtDeath == LifeStage.EWE_REPLACEMENT) {
                throw DomainValidationException(
                    code = "INVALID_LIFE_STAGE",
                    message = "Un ejemplar macho (MALE) no puede tener etapa al morir EWE_REPLACEMENT.",
                )
            }
            return MortalityResolution(
                mortalityDate = mortalityDate,
                mortalityCause = resolvedCause,
                lifeStageAtDeath = resolvedStageAtDeath,
            )
        }

        if (mortalityDate != null || resolvedCause != null || lifeStageAtDeath != null) {
            throw DomainValidationException(
                code = "MORTALITY_FIELDS_NOT_ALLOWED",
                message = "Los campos de mortalidad solo aplican cuando el estado es DECEASED.",
            )
        }
        return MortalityResolution(
            mortalityDate = null,
            mortalityCause = null,
            lifeStageAtDeath = null,
        )
    }

    fun validatePedigree(
        animalId: UUID,
        sireId: UUID?,
        damId: UUID?,
    ) {
        if (sireId != null && sireId == animalId) {
            throw DomainValidationException(
                code = "INVALID_PEDIGREE",
                message = "Un ejemplar no puede ser su propio padre.",
            )
        }
        if (damId != null && damId == animalId) {
            throw DomainValidationException(
                code = "INVALID_PEDIGREE",
                message = "Un ejemplar no puede ser su propia madre.",
            )
        }
        if (sireId != null && damId != null && sireId == damId) {
            throw DomainValidationException(
                code = "INVALID_PEDIGREE",
                message = "El padre y la madre no pueden ser el mismo ejemplar.",
            )
        }
    }

    fun validatePhotoS3Key(
        rawKey: String?,
        ranchId: UUID,
    ): String? {
        if (rawKey == null) return null
        if (rawKey.isBlank() ||
            rawKey != rawKey.trim() ||
            rawKey.contains("..") ||
            rawKey.contains("\\") ||
            rawKey.contains("\u0000") ||
            rawKey.contains("//")
        ) {
            throw DomainValidationException(
                code = "INVALID_PHOTO_S3_KEY",
                message = "La clave de fotografía S3 contiene caracteres o secuencias inválidas.",
            )
        }
        val expectedPrefix = "$ranchId/"
        if (!rawKey.startsWith(expectedPrefix)) {
            throw DomainValidationException(
                code = "INVALID_PHOTO_S3_KEY",
                message = "La clave de fotografía S3 debe pertenecer al rancho activo.",
            )
        }
        val relativeKey = rawKey.removePrefix(expectedPrefix)
        if (relativeKey.isBlank() || relativeKey.endsWith("/")) {
            throw DomainValidationException(
                code = "INVALID_PHOTO_S3_KEY",
                message = "La clave de fotografía S3 es inválida.",
            )
        }
        return rawKey
    }

    fun validatePagination(page: Int, size: Int) {
        if (page < 0 || size !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            throw DomainValidationException(
                code = "INVALID_PAGINATION",
                message = "Parámetros de paginación inválidos: page debe ser >= 0 y size entre $MIN_PAGE_SIZE y $MAX_PAGE_SIZE.",
            )
        }
    }
}

interface AnimalRepositoryPort {
    fun searchSummaries(query: ListAnimalsQuery): AnimalPage
    fun findDetailByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalDetail?
    fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalRecord?
    fun findByIdAndRanchIdForUpdate(animalId: UUID, ranchId: UUID): AnimalRecord?
    fun existsById(animalId: UUID): Boolean
    fun existsByRanchIdAndFolioInterno(ranchId: UUID, folioInterno: String): Boolean
    fun existsByRanchIdAndFolioInternoAndIdNot(ranchId: UUID, folioInterno: String, excludeAnimalId: UUID): Boolean
    fun existsByRanchIdAndSiniigaChip(ranchId: UUID, siniigaChip: String): Boolean
    fun existsByRanchIdAndSiniigaChipAndIdNot(ranchId: UUID, siniigaChip: String, excludeAnimalId: UUID): Boolean
    fun hasDependentRecordsAsSire(ranchId: UUID, animalId: UUID): Boolean
    fun hasDependentRecordsAsDam(ranchId: UUID, animalId: UUID): Boolean
    fun create(animal: AnimalRecord): AnimalRecord
    fun update(animal: AnimalRecord): AnimalRecord
}

interface AnimalPhotoUrlPort {
    fun generatePresignedDownloadUrl(ranchId: UUID, s3Key: String): String
}

interface AnimalWeightReadPort {
    fun findAllByAnimalAndRanchOrderedByDateAsc(animalId: UUID, ranchId: UUID): List<AnimalWeightRecord>
}
