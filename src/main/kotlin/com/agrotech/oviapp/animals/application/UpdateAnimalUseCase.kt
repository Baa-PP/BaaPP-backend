package com.agrotech.oviapp.animals.application

import com.agrotech.oviapp.animals.domain.AnimalPolicy
import com.agrotech.oviapp.animals.domain.AnimalRecord
import com.agrotech.oviapp.animals.domain.AnimalRepositoryPort
import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
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

data class UpdateAnimalCommand(
    val animalId: UUID,
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

@Service
class UpdateAnimalUseCase(
    private val animalRepository: AnimalRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(UpdateAnimalUseCase::class.java)

    @Transactional
    fun execute(command: UpdateAnimalCommand): AnimalRecord {
        val existing = animalRepository.findByIdAndRanchIdForUpdate(command.animalId, command.ranchId)
            ?: throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo.")

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

        val targetStatus = command.status ?: existing.status
        val targetEntryReason = command.entryReason ?: existing.entryReason
        val targetParturitionType = command.parturitionType ?: existing.parturitionType
        val targetConceptionMethod = command.conceptionMethod ?: existing.conceptionMethod

        AnimalPolicy.validateSexAndStates(command.sex, command.lifeStage, command.reproductiveStatus)
        AnimalPolicy.validateDates(command.birthDate, command.weaningDate, command.mortalityDate)
        val mortality = AnimalPolicy.validateMortality(
            status = targetStatus,
            sex = command.sex,
            birthDate = command.birthDate,
            mortalityDate = command.mortalityDate,
            mortalityCause = command.mortalityCause,
            lifeStageAtDeath = command.lifeStageAtDeath,
            defaultLifeStageAtDeath = existing.lifeStageAtDeath ?: existing.lifeStage,
        )
        AnimalPolicy.validatePedigree(existing.id, command.sireId, command.damId)
        val photoS3Key = AnimalPolicy.validatePhotoS3Key(command.photoS3Key, command.ranchId)

        if (command.sex != existing.sex) {
            when (existing.sex) {
                Sex.MALE -> if (animalRepository.hasDependentRecordsAsSire(command.ranchId, existing.id)) {
                    throw BusinessConflictException(
                        code = "SEX_CHANGE_CONFLICT",
                        message = "No se puede cambiar el sexo del ejemplar porque está registrado como padre en el rancho.",
                    )
                }
                Sex.FEMALE -> if (animalRepository.hasDependentRecordsAsDam(command.ranchId, existing.id)) {
                    throw BusinessConflictException(
                        code = "SEX_CHANGE_CONFLICT",
                        message = "No se puede cambiar el sexo del ejemplar porque está registrado como madre en el rancho.",
                    )
                }
            }
        }

        validateParentsInRanch(animalRepository, command.ranchId, command.sireId, command.damId)

        if (animalRepository.existsByRanchIdAndFolioInternoAndIdNot(command.ranchId, text.folioInterno, existing.id)) {
            throw BusinessConflictException(
                code = "FOLIO_ALREADY_EXISTS",
                message = "Ya existe un ejemplar con ese folio interno en el rancho activo.",
            )
        }
        if (text.siniigaChip != null &&
            animalRepository.existsByRanchIdAndSiniigaChipAndIdNot(command.ranchId, text.siniigaChip, existing.id)
        ) {
            throw BusinessConflictException(
                code = "SINIIGA_CHIP_ALREADY_EXISTS",
                message = "Ya existe un ejemplar con ese chip SINIIGA en el rancho activo.",
            )
        }

        val updated = AnimalRecord(
            id = existing.id,
            ranchId = existing.ranchId,
            folioInterno = text.folioInterno,
            siniigaChip = text.siniigaChip,
            registroUno = text.registroUno,
            adn = text.adn,
            breed = text.breed,
            color = text.color,
            sex = command.sex,
            birthDate = command.birthDate,
            weaningDate = command.weaningDate,
            entryReason = targetEntryReason,
            criador = text.criador,
            parturitionType = targetParturitionType,
            conceptionMethod = targetConceptionMethod,
            sireId = command.sireId,
            damId = command.damId,
            externalSireFolio = text.externalSireFolio,
            externalDamFolio = text.externalDamFolio,
            lifeStage = command.lifeStage,
            reproductiveStatus = command.reproductiveStatus,
            status = targetStatus,
            mortalityDate = mortality.mortalityDate,
            mortalityCause = mortality.mortalityCause,
            lifeStageAtDeath = mortality.lifeStageAtDeath,
            photoS3Key = photoS3Key,
            birthEventId = existing.birthEventId,
            updatedAt = Instant.now(),
        )
        val saved = animalRepository.update(updated)
        log.info("Updated animal id={} ranchId={} status={}", saved.id, saved.ranchId, saved.status)
        return saved
    }
}
