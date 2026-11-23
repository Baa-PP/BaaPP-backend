package com.agrotech.oviapp.sync.application

import com.agrotech.oviapp.animals.application.CreateAnimalCommand
import com.agrotech.oviapp.animals.application.CreateAnimalUseCase
import com.agrotech.oviapp.animals.application.UpdateAnimalCommand
import com.agrotech.oviapp.animals.application.UpdateAnimalUseCase
import com.agrotech.oviapp.animals.domain.AnimalPolicy
import com.agrotech.oviapp.animals.domain.AnimalRepositoryPort
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.BusinessRuleViolationException
import com.agrotech.oviapp.domain.exception.DomainException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.sync.domain.DuplicateMutationException
import com.agrotech.oviapp.sync.domain.ProcessedMutationPort
import com.agrotech.oviapp.sync.domain.SyncAnimalCreatePayload
import com.agrotech.oviapp.sync.domain.SyncAnimalUpdatePayload
import com.agrotech.oviapp.sync.domain.SyncBatchCommand
import com.agrotech.oviapp.sync.domain.SyncBatchMutation
import com.agrotech.oviapp.sync.domain.SyncBatchResult
import com.agrotech.oviapp.sync.domain.SyncBatchResultItem
import com.agrotech.oviapp.sync.domain.SyncBirthCreatePayload
import com.agrotech.oviapp.sync.domain.SyncBirthLambRecord
import com.agrotech.oviapp.sync.domain.SyncBirthPersistencePort
import com.agrotech.oviapp.sync.domain.SyncBirthPolicy
import com.agrotech.oviapp.sync.domain.SyncBirthRecord
import com.agrotech.oviapp.sync.domain.SyncEntityType
import com.agrotech.oviapp.sync.domain.SyncMutationStatus
import com.agrotech.oviapp.sync.domain.SyncOperation
import com.agrotech.oviapp.sync.domain.SyncPayloadParserPort
import com.agrotech.oviapp.sync.domain.SyncTransactionPort
import com.agrotech.oviapp.sync.domain.SyncWeaningCreatePayload
import com.agrotech.oviapp.sync.domain.SyncWeightCreatePayload
import com.agrotech.oviapp.weights.application.ConfirmWeaningCommand
import com.agrotech.oviapp.weights.application.ConfirmWeaningUseCase
import com.agrotech.oviapp.weights.application.RegisterWeightCommand
import com.agrotech.oviapp.weights.application.RegisterWeightUseCase
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.domain.WeightRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class ProcessSyncBatchUseCase(
    private val processedMutationPort: ProcessedMutationPort,
    private val payloadParser: SyncPayloadParserPort,
    private val transactionPort: SyncTransactionPort,
    private val createAnimalUseCase: CreateAnimalUseCase,
    private val updateAnimalUseCase: UpdateAnimalUseCase,
    private val registerWeightUseCase: RegisterWeightUseCase,
    private val confirmWeaningUseCase: ConfirmWeaningUseCase,
    private val animalRepository: AnimalRepositoryPort,
    private val weightRepository: WeightRepositoryPort,
    private val birthPersistencePort: SyncBirthPersistencePort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ProcessSyncBatchUseCase::class.java)

    fun execute(command: SyncBatchCommand): SyncBatchResult {
        val results = command.mutations.map { mutation ->
            processSingleMutation(command, mutation)
        }
        return SyncBatchResult(
            ranchId = command.ranchId,
            processedCount = results.size,
            appliedCount = results.count { it.status == SyncMutationStatus.APPLIED },
            alreadyProcessedCount = results.count { it.status == SyncMutationStatus.ALREADY_PROCESSED },
            rejectedCount = results.count { it.status == SyncMutationStatus.REJECTED },
            results = results,
        )
    }

    private fun processSingleMutation(
        command: SyncBatchCommand,
        mutation: SyncBatchMutation,
    ): SyncBatchResultItem =
        try {
            // Let DomainException / DuplicateMutationException escape executeInNewTransaction so the inner
            // transaction rolls back cleanly before converting the error into a per-item status.
            val status = transactionPort.executeInNewTransaction {
                val recordedRanchId = processedMutationPort.findRanchIdByMutationId(mutation.mutationId)
                if (recordedRanchId != null) {
                    if (recordedRanchId == command.ranchId) {
                        return@executeInNewTransaction SyncMutationStatus.ALREADY_PROCESSED
                    }
                    throw ForbiddenAccessException(
                        code = "MUTATION_RANCH_CONFLICT",
                        message = "El identificador de mutación pertenece a otro rancho.",
                    )
                }

                applyMutation(command, mutation)
                processedMutationPort.recordProcessed(mutation.mutationId, command.ranchId, clock.instant())
                SyncMutationStatus.APPLIED
            }
            SyncBatchResultItem(
                mutationId = mutation.mutationId,
                entityType = mutation.entityType,
                operation = mutation.operation,
                status = status,
            )
        } catch (ex: DuplicateMutationException) {
            val recordedRanchId = processedMutationPort.findRanchIdByMutationId(mutation.mutationId)
            if (recordedRanchId == command.ranchId) {
                SyncBatchResultItem(
                    mutationId = mutation.mutationId,
                    entityType = mutation.entityType,
                    operation = mutation.operation,
                    status = SyncMutationStatus.ALREADY_PROCESSED,
                )
            } else {
                SyncBatchResultItem(
                    mutationId = mutation.mutationId,
                    entityType = mutation.entityType,
                    operation = mutation.operation,
                    status = SyncMutationStatus.REJECTED,
                    errorCode = "MUTATION_RANCH_CONFLICT",
                    errorMessage = "El identificador de mutación pertenece a otro rancho.",
                )
            }
        } catch (ex: DomainException) {
            log.info(
                "Sync mutation rejected mutationId={} ranchId={} entity={} op={} code={}",
                mutation.mutationId,
                command.ranchId,
                mutation.entityType,
                mutation.operation,
                ex.code,
            )
            SyncBatchResultItem(
                mutationId = mutation.mutationId,
                entityType = mutation.entityType,
                operation = mutation.operation,
                status = SyncMutationStatus.REJECTED,
                errorCode = ex.code,
                errorMessage = ex.message,
            )
        }

    private fun applyMutation(
        command: SyncBatchCommand,
        mutation: SyncBatchMutation,
    ) {
        when (mutation.entityType to mutation.operation) {
            SyncEntityType.ANIMAL to SyncOperation.CREATE -> {
                val payload = payloadParser.parseAnimalCreate(mutation.payloadJson)
                createAnimalUseCase.execute(payload.toCommand(command.ranchId))
            }
            SyncEntityType.ANIMAL to SyncOperation.UPDATE -> {
                if (!command.canEditAnimals) {
                    throw ForbiddenAccessException(
                        code = "INSUFFICIENT_PERMISSION",
                        message = "No cuenta con el permiso 'edicion' para actualizar ejemplares en el rancho activo.",
                    )
                }
                val payload = payloadParser.parseAnimalUpdate(mutation.payloadJson)
                updateAnimalUseCase.execute(payload.toCommand(command.ranchId))
            }
            SyncEntityType.BIRTH_EVENT to SyncOperation.CREATE -> {
                val payload = payloadParser.parseBirthCreate(mutation.payloadJson)
                applyBirthCreate(command, payload)
            }
            SyncEntityType.BIRTH_EVENT to SyncOperation.UPDATE -> {
                throw BusinessRuleViolationException(
                    code = "BIRTH_IMMUTABLE",
                    message = "Los nacimientos guardados no pueden editarse desde sincronización; utilice la bandeja de auditoría.",
                )
            }
            SyncEntityType.WEIGHT_MEASUREMENT to SyncOperation.CREATE -> {
                val payload = payloadParser.parseWeightCreate(mutation.payloadJson)
                registerWeightUseCase.execute(payload.toCommand(command.ranchId))
            }
            SyncEntityType.WEANING to SyncOperation.CREATE -> {
                val payload = payloadParser.parseWeaningCreate(mutation.payloadJson)
                confirmWeaningUseCase.execute(payload.toCommand(command.ranchId))
            }
            else -> throw DomainValidationException(
                code = "UNSUPPORTED_SYNC_OPERATION",
                message = "Operación de sincronización no soportada: ${mutation.entityType}/${mutation.operation}.",
            )
        }
    }

    private fun applyBirthCreate(
        command: SyncBatchCommand,
        payload: SyncBirthCreatePayload,
    ) {
        val birthId = payload.id ?: UUID.randomUUID()
        if (birthPersistencePort.existsBirthById(birthId)) {
            throw BusinessConflictException(
                code = "BIRTH_ID_ALREADY_EXISTS",
                message = "Ya existe un parto registrado con el identificador proporcionado.",
            )
        }

        val evidencePhotoKey = AnimalPolicy.validatePhotoS3Key(payload.evidencePhotoS3Key, command.ranchId)
        SyncBirthPolicy.validateInitialRegistration(
            birthDate = payload.birthDate,
            today = LocalDate.now(clock),
            evidencePhotoKey = evidencePhotoKey,
        )

        val liveBornCount = payload.liveBornCount ?: payload.lambs.size
        SyncBirthPolicy.validateCounts(
            liveBornCount = liveBornCount,
            stillbornCount = payload.stillbornCount,
            lambsSize = payload.lambs.size,
        )

        val dam = animalRepository.findByIdAndRanchIdForUpdate(payload.damId, command.ranchId)
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
        if (dam.status == AnimalStatus.DECEASED) {
            throw BusinessRuleViolationException(
                code = "DAM_DECEASED",
                message = "No se puede registrar un parto para una madre fallecida.",
            )
        }

        if (payload.sireId != null) {
            if (payload.sireId == payload.damId) {
                throw DomainValidationException(
                    code = "INVALID_PEDIGREE",
                    message = "El padre y la madre no pueden ser el mismo ejemplar.",
                )
            }
            val sire = animalRepository.findByIdAndRanchId(payload.sireId, command.ranchId)
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

        val normalizedExternalSire = AnimalPolicy.normalizeOptionalText(
            payload.externalSireFolio,
            AnimalPolicy.MAX_OPTIONAL_CODE_LENGTH,
            "INVALID_EXTERNAL_SIRE_FOLIO",
            "externalSireFolio",
        )

        val parturitionType = SyncBirthPolicy.deduceParturitionType(liveBornCount + payload.stillbornCount)

        val seenLambIds = mutableSetOf<UUID>()
        val seenFolios = mutableSetOf<String>()
        val seenChips = mutableSetOf<String>()
        val seenWeightIds = mutableSetOf<UUID>()

        val lambRecords = payload.lambs.map { lamb ->
            val lambId = lamb.id ?: UUID.randomUUID()
            if (!seenLambIds.add(lambId) || animalRepository.existsById(lambId)) {
                throw BusinessConflictException(
                    code = "ANIMAL_ID_ALREADY_EXISTS",
                    message = "Ya existe un ejemplar registrado con el identificador proporcionado.",
                )
            }
            if (lambId == payload.damId || lambId == payload.sireId) {
                throw DomainValidationException(
                    code = "INVALID_PEDIGREE",
                    message = "Una cría no puede compartir identificador con su madre o padre.",
                )
            }

            val normalizedText = AnimalPolicy.normalizeTextFields(
                folioInterno = lamb.folioInterno,
                siniigaChip = lamb.siniigaChip,
                registroUno = lamb.registroUno,
                adn = lamb.adn,
                breed = lamb.breed,
                color = lamb.color,
                criador = lamb.criador,
                externalSireFolio = normalizedExternalSire,
                externalDamFolio = null,
            )

            if (!seenFolios.add(normalizedText.folioInterno) ||
                animalRepository.existsByRanchIdAndFolioInterno(command.ranchId, normalizedText.folioInterno)
            ) {
                throw BusinessConflictException(
                    code = "FOLIO_ALREADY_EXISTS",
                    message = "Ya existe un ejemplar con ese folio interno en el rancho activo.",
                )
            }

            if (normalizedText.siniigaChip != null &&
                (!seenChips.add(normalizedText.siniigaChip) ||
                    animalRepository.existsByRanchIdAndSiniigaChip(command.ranchId, normalizedText.siniigaChip))
            ) {
                throw BusinessConflictException(
                    code = "SINIIGA_CHIP_ALREADY_EXISTS",
                    message = "Ya existe un ejemplar con ese chip SINIIGA en el rancho activo.",
                )
            }

            AnimalPolicy.validateSexAndStates(lamb.sex, lamb.lifeStage, lamb.reproductiveStatus)
            val lambPhotoKey = AnimalPolicy.validatePhotoS3Key(lamb.photoS3Key, command.ranchId)

            val weightId = if (lamb.birthWeightKg != null) {
                val candidateWeightId = lamb.weightId ?: UUID.randomUUID()
                if (!seenWeightIds.add(candidateWeightId) || weightRepository.existsById(candidateWeightId)) {
                    throw BusinessConflictException(
                        code = "WEIGHT_ID_ALREADY_EXISTS",
                        message = "Ya existe un pesaje registrado con el identificador proporcionado.",
                    )
                }
                // Reuse WeightMeasurement domain validation (weight bounds & 2-decimal precision).
                WeightMeasurement(
                    id = candidateWeightId,
                    animalId = lambId,
                    ranchId = command.ranchId,
                    milestone = WeightMilestone.BIRTH,
                    weightKg = lamb.birthWeightKg,
                    scrotalCircumferenceCm = null,
                    date = payload.birthDate,
                )
                candidateWeightId
            } else {
                null
            }

            SyncBirthLambRecord(
                id = lambId,
                folioInterno = normalizedText.folioInterno,
                siniigaChip = normalizedText.siniigaChip,
                registroUno = normalizedText.registroUno,
                adn = normalizedText.adn,
                breed = normalizedText.breed,
                color = normalizedText.color,
                sex = lamb.sex,
                criador = normalizedText.criador,
                lifeStage = lamb.lifeStage,
                reproductiveStatus = lamb.reproductiveStatus,
                birthWeightKg = lamb.birthWeightKg,
                weightId = weightId,
                photoS3Key = lambPhotoKey,
            )
        }

        val registeredByUserId = birthPersistencePort.findUserIdByUsername(command.actorUsername)
            ?: throw ResourceNotFoundException(
                code = "USER_NOT_FOUND",
                message = "No se encontró el usuario autenticado para registrar el parto.",
            )

        val transitionDamToLactating =
            liveBornCount > 0 && dam.status == AnimalStatus.ACTIVE && dam.reproductiveStatus != ReproductiveStatus.LACTATING

        birthPersistencePort.createBirth(
            SyncBirthRecord(
                id = birthId,
                ranchId = command.ranchId,
                damId = payload.damId,
                sireId = payload.sireId,
                externalSireFolio = normalizedExternalSire,
                birthDate = payload.birthDate,
                liveBornCount = liveBornCount,
                stillbornCount = payload.stillbornCount,
                parturitionType = parturitionType,
                conceptionMethod = payload.conceptionMethod,
                evidencePhotoS3Key = evidencePhotoKey!!,
                registeredByUserId = registeredByUserId,
                transitionDamToLactating = transitionDamToLactating,
                lambs = lambRecords,
                updatedAt = clock.instant(),
            ),
        )
    }

    private fun SyncAnimalCreatePayload.toCommand(ranchId: UUID) = CreateAnimalCommand(
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
        entryReason = entryReason,
        criador = criador,
        parturitionType = parturitionType,
        conceptionMethod = conceptionMethod,
        sireId = sireId,
        damId = damId,
        externalSireFolio = externalSireFolio,
        externalDamFolio = externalDamFolio,
        lifeStage = lifeStage,
        reproductiveStatus = reproductiveStatus,
        status = status,
        mortalityDate = mortalityDate,
        mortalityCause = mortalityCause,
        lifeStageAtDeath = lifeStageAtDeath,
        photoS3Key = photoS3Key,
    )

    private fun SyncAnimalUpdatePayload.toCommand(ranchId: UUID) = UpdateAnimalCommand(
        animalId = animalId,
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
        entryReason = entryReason,
        criador = criador,
        parturitionType = parturitionType,
        conceptionMethod = conceptionMethod,
        sireId = sireId,
        damId = damId,
        externalSireFolio = externalSireFolio,
        externalDamFolio = externalDamFolio,
        lifeStage = lifeStage,
        reproductiveStatus = reproductiveStatus,
        status = status,
        mortalityDate = mortalityDate,
        mortalityCause = mortalityCause,
        lifeStageAtDeath = lifeStageAtDeath,
        photoS3Key = photoS3Key,
    )

    private fun SyncWeightCreatePayload.toCommand(ranchId: UUID) = RegisterWeightCommand(
        id = id,
        ranchId = ranchId,
        animalId = animalId,
        milestone = milestone,
        weightKg = weightKg,
        scrotalCircumferenceCm = scrotalCircumferenceCm,
        date = date,
    )

    private fun SyncWeaningCreatePayload.toCommand(ranchId: UUID) = ConfirmWeaningCommand(
        ranchId = ranchId,
        animalId = animalId,
        weaningDate = weaningDate,
        weaningWeightKg = weaningWeightKg,
        destination = destination,
        scrotalCircumferenceCm = scrotalCircumferenceCm,
        weightId = weightId,
    )
}
