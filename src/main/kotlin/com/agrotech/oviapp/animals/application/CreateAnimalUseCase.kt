package com.agrotech.oviapp.animals.application

import com.agrotech.oviapp.animals.domain.AnimalPolicy
import com.agrotech.oviapp.animals.domain.AnimalRecord
import com.agrotech.oviapp.animals.domain.AnimalRepositoryPort
import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateAnimalCommand(
    val id: UUID? = null,
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
)

@Service
class CreateAnimalUseCase(
    private val animalRepository: AnimalRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(CreateAnimalUseCase::class.java)

    @Transactional
    fun execute(command: CreateAnimalCommand): AnimalRecord {
        if (command.id != null && animalRepository.existsById(command.id)) {
            throw BusinessConflictException(
                code = "ANIMAL_ID_ALREADY_EXISTS",
                message = "Ya existe un ejemplar registrado con el identificador proporcionado.",
            )
        }

        val animalId = command.id ?: UUID.randomUUID()
        val text = AnimalPolicy.normalizeTextFields(
            folioInterno = command.folioInterno,
            siniigaChip = command.siniigaChip,
            registroUno = command.registroUno,
            adn = command.adn,
            breed = command.breed,
            color = command.color,
            criador = command.criador,
            externalSireFolio = command.externalSireFolio,
            externalDamFolio = command.externalDamFolio,
        )

        AnimalPolicy.validateSexAndStates(command.sex, command.lifeStage, command.reproductiveStatus)
        AnimalPolicy.validateDates(command.birthDate, command.weaningDate, command.mortalityDate)
        val mortality = AnimalPolicy.validateMortality(
            status = command.status,
            sex = command.sex,
            birthDate = command.birthDate,
            mortalityDate = command.mortalityDate,
            mortalityCause = command.mortalityCause,
            lifeStageAtDeath = command.lifeStageAtDeath,
            defaultLifeStageAtDeath = command.lifeStage,
        )
        AnimalPolicy.validatePedigree(animalId, command.sireId, command.damId)
        val photoS3Key = AnimalPolicy.validatePhotoS3Key(command.photoS3Key, command.ranchId)

        validateParentsInRanch(animalRepository, command.ranchId, command.sireId, command.damId)

        if (animalRepository.existsByRanchIdAndFolioInterno(command.ranchId, text.folioInterno)) {
            throw BusinessConflictException(
                code = "FOLIO_ALREADY_EXISTS",
                message = "Ya existe un ejemplar con ese folio interno en el rancho activo.",
            )
        }
        if (text.siniigaChip != null &&
            animalRepository.existsByRanchIdAndSiniigaChip(command.ranchId, text.siniigaChip)
        ) {
            throw BusinessConflictException(
                code = "SINIIGA_CHIP_ALREADY_EXISTS",
                message = "Ya existe un ejemplar con ese chip SINIIGA en el rancho activo.",
            )
        }

        val record = AnimalRecord(
            id = animalId,
            ranchId = command.ranchId,
            folioInterno = text.folioInterno,
            siniigaChip = text.siniigaChip,
            registroUno = text.registroUno,
            adn = text.adn,
            breed = text.breed,
            color = text.color,
            sex = command.sex,
            birthDate = command.birthDate,
            weaningDate = command.weaningDate,
            entryReason = command.entryReason,
            criador = text.criador,
            parturitionType = command.parturitionType,
            conceptionMethod = command.conceptionMethod,
            sireId = command.sireId,
            damId = command.damId,
            externalSireFolio = text.externalSireFolio,
            externalDamFolio = text.externalDamFolio,
            lifeStage = command.lifeStage,
            reproductiveStatus = command.reproductiveStatus,
            status = command.status,
            mortalityDate = mortality.mortalityDate,
            mortalityCause = mortality.mortalityCause,
            lifeStageAtDeath = mortality.lifeStageAtDeath,
            photoS3Key = photoS3Key,
            birthEventId = null,
            updatedAt = Instant.now(),
        )
        val created = animalRepository.create(record)
        log.info("Created animal id={} ranchId={}", created.id, created.ranchId)
        return created
    }
}

internal fun validateParentsInRanch(
    animalRepository: AnimalRepositoryPort,
    ranchId: UUID,
    sireId: UUID?,
    damId: UUID?,
) {
    if (sireId != null) {
        val sire = animalRepository.findByIdAndRanchId(sireId, ranchId)
            ?: throw DomainValidationException(
                code = "INVALID_SIRE",
                message = "El padre especificado no existe en el rancho activo.",
            )
        if (sire.sex != Sex.MALE) {
            throw DomainValidationException(
                code = "INVALID_SIRE_SEX",
                message = "El padre debe ser un ejemplar macho (MALE).",
            )
        }
    }
    if (damId != null) {
        val dam = animalRepository.findByIdAndRanchId(damId, ranchId)
            ?: throw DomainValidationException(
                code = "INVALID_DAM",
                message = "La madre especificada no existe en el rancho activo.",
            )
        if (dam.sex != Sex.FEMALE) {
            throw DomainValidationException(
                code = "INVALID_DAM_SEX",
                message = "La madre debe ser un ejemplar hembra (FEMALE).",
            )
        }
    }
}
