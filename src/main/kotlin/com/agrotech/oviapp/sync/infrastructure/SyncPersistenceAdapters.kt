package com.agrotech.oviapp.sync.infrastructure

import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.infrastructure.persistence.entity.AnimalEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.MutacionProcesadaEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.PartoEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.PesajeEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.UsuarioEntity
import com.agrotech.oviapp.sync.domain.DuplicateMutationException
import com.agrotech.oviapp.sync.domain.ProcessedMutationPort
import com.agrotech.oviapp.sync.domain.SyncAnimalCreatePayload
import com.agrotech.oviapp.sync.domain.SyncAnimalUpdatePayload
import com.agrotech.oviapp.sync.domain.SyncBirthCreatePayload
import com.agrotech.oviapp.sync.domain.SyncBirthLambPayload
import com.agrotech.oviapp.sync.domain.SyncBirthPersistencePort
import com.agrotech.oviapp.sync.domain.SyncBirthRecord
import com.agrotech.oviapp.sync.domain.SyncPayloadParserPort
import com.agrotech.oviapp.sync.domain.SyncTransactionPort
import com.agrotech.oviapp.sync.domain.SyncWeaningCreatePayload
import com.agrotech.oviapp.sync.domain.SyncWeightCreatePayload
import com.agrotech.oviapp.weights.domain.WeaningDestination
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

interface MutacionProcesadaJpaRepository : JpaRepository<MutacionProcesadaEntity, UUID>

interface SyncPartoJpaRepository : JpaRepository<PartoEntity, UUID>

interface SyncUsuarioJpaRepository : JpaRepository<UsuarioEntity, UUID> {
    fun findByUsuario(usuario: String): UsuarioEntity?
}

interface SyncAnimalLookupJpaRepository : JpaRepository<AnimalEntity, UUID> {
    fun findByIdAndRanchoId(id: UUID, ranchoId: UUID): AnimalEntity?
}

@Component
class ProcessedMutationJpaAdapter(
    private val repository: MutacionProcesadaJpaRepository,
    private val entityManager: EntityManager,
) : ProcessedMutationPort {

    override fun findRanchIdByMutationId(mutationId: UUID): UUID? =
        repository.findById(mutationId).orElse(null)?.ranchoId

    override fun recordProcessed(mutationId: UUID, ranchId: UUID, processedAt: Instant) {
        val entity = MutacionProcesadaEntity(
            mutacionId = mutationId,
            ranchoId = ranchId,
            procesadoEn = OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC),
        )
        try {
            // Persist directly instead of save() so concurrent duplicate mutations fail cleanly on the PK constraint.
            entityManager.persist(entity)
            entityManager.flush()
        } catch (ex: RuntimeException) {
            val isDuplicateMutation = generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .any { it.sqlState == PG_UNIQUE_VIOLATION && it.constraintName == CONSTRAINT_MUTACION_PK }
            if (isDuplicateMutation) {
                throw DuplicateMutationException(mutationId)
            }
            throw ex
        }
    }

    companion object {
        private const val PG_UNIQUE_VIOLATION = "23505"
        private const val CONSTRAINT_MUTACION_PK = "mutaciones_procesadas_pkey"
    }
}

@Component
class SyncTransactionAdapter(
    transactionManager: PlatformTransactionManager,
) : SyncTransactionPort {
    private val requiresNewTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    override fun <T> executeInNewTransaction(block: () -> T): T =
        requiresNewTemplate.execute { block() }
            ?: throw IllegalStateException("Sync transaction did not produce a result")
}

@Component
class SyncBirthPersistenceAdapter(
    private val partoRepository: SyncPartoJpaRepository,
    private val usuarioRepository: SyncUsuarioJpaRepository,
    private val animalRepository: SyncAnimalLookupJpaRepository,
    private val entityManager: EntityManager,
) : SyncBirthPersistencePort {

    override fun existsBirthById(birthId: UUID): Boolean = partoRepository.existsById(birthId)

    override fun findUserIdByUsername(username: String): UUID? =
        usuarioRepository.findByUsuario(username)?.getId()

    override fun createBirth(birth: SyncBirthRecord) {
        val nowOffset = OffsetDateTime.ofInstant(birth.updatedAt, ZoneOffset.UTC)
        val parto = PartoEntity(
            id = birth.id,
            ranchoId = birth.ranchId,
            madreId = birth.damId,
            padreId = birth.sireId,
            fecha = birth.birthDate,
            nacidosVivos = birth.liveBornCount,
            nacidosMuertos = birth.stillbornCount,
            fotoEvidenciaS3Key = birth.evidencePhotoS3Key,
            registradoPorUsuarioId = birth.registeredByUserId,
            creadoEn = nowOffset,
            actualizadoEn = nowOffset,
        )

        flushTranslatingConflicts {
            entityManager.persist(parto)

            birth.lambs.forEach { lamb ->
                val animal = AnimalEntity(
                    id = lamb.id,
                    ranchoId = birth.ranchId,
                    folioInterno = lamb.folioInterno,
                    chipSiniiga = lamb.siniigaChip,
                    registroUno = lamb.registroUno,
                    adn = lamb.adn,
                    raza = lamb.breed,
                    color = lamb.color,
                    sexo = lamb.sex.name,
                    fechaNacimiento = birth.birthDate,
                    tipoParto = birth.parturitionType.name,
                    metodoConcepcion = birth.conceptionMethod.name,
                    padreId = birth.sireId,
                    madreId = birth.damId,
                    folioPadreExterno = birth.externalSireFolio,
                    etapaVida = lamb.lifeStage.name,
                    estadoReproductivo = lamb.reproductiveStatus.name,
                    estado = AnimalStatus.ACTIVE.name,
                    motivoAlta = EntryReason.BIRTH.name,
                    criador = lamb.criador,
                    fotoS3Key = lamb.photoS3Key,
                    partoId = birth.id,
                    creadoEn = nowOffset,
                    actualizadoEn = nowOffset,
                )
                entityManager.persist(animal)

                if (lamb.birthWeightKg != null && lamb.weightId != null) {
                    val pesaje = PesajeEntity(
                        id = lamb.weightId,
                        animalId = lamb.id,
                        ranchoId = birth.ranchId,
                        hito = WeightMilestone.BIRTH.name,
                        pesoKg = lamb.birthWeightKg,
                        circunferenciaEscrotalCm = null,
                        fecha = birth.birthDate,
                        creadoEn = nowOffset,
                    )
                    entityManager.persist(pesaje)
                }
            }

            if (birth.transitionDamToLactating) {
                val dam = animalRepository.findByIdAndRanchoId(birth.damId, birth.ranchId)
                if (dam != null && dam.estado == AnimalStatus.ACTIVE.name) {
                    dam.estadoReproductivo = ReproductiveStatus.LACTATING.name
                    dam.actualizadoEn = nowOffset
                }
            }

            entityManager.flush()
        }
    }

    private fun flushTranslatingConflicts(block: () -> Unit) {
        try {
            block()
        } catch (ex: RuntimeException) {
            val constraintName = generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .firstOrNull { it.sqlState == PG_UNIQUE_VIOLATION }
                ?.constraintName
            when (constraintName) {
                CONSTRAINT_PARTOS_PK -> throw BusinessConflictException(
                    code = "BIRTH_ID_ALREADY_EXISTS",
                    message = "Ya existe un parto registrado con el identificador proporcionado.",
                )
                CONSTRAINT_ANIMALES_PK -> throw BusinessConflictException(
                    code = "ANIMAL_ID_ALREADY_EXISTS",
                    message = "Ya existe un ejemplar registrado con el identificador proporcionado.",
                )
                CONSTRAINT_FOLIO -> throw BusinessConflictException(
                    code = "FOLIO_ALREADY_EXISTS",
                    message = "Ya existe un ejemplar con ese folio interno en el rancho activo.",
                )
                CONSTRAINT_SINIIGA -> throw BusinessConflictException(
                    code = "SINIIGA_CHIP_ALREADY_EXISTS",
                    message = "Ya existe un ejemplar con ese chip SINIIGA en el rancho activo.",
                )
                CONSTRAINT_PESAJES_PK -> throw BusinessConflictException(
                    code = "WEIGHT_ID_ALREADY_EXISTS",
                    message = "Ya existe un pesaje registrado con el identificador proporcionado.",
                )
                else -> throw ex
            }
        }
    }

    companion object {
        private const val PG_UNIQUE_VIOLATION = "23505"
        private const val CONSTRAINT_PARTOS_PK = "partos_pkey"
        private const val CONSTRAINT_ANIMALES_PK = "animales_pkey"
        private const val CONSTRAINT_FOLIO = "animales_rancho_id_folio_interno_key"
        private const val CONSTRAINT_SINIIGA = "uq_animales_rancho_chip"
        private const val CONSTRAINT_PESAJES_PK = "pesajes_pkey"
    }
}

@Component
class SyncPayloadParserAdapter(
    private val objectMapper: ObjectMapper,
) : SyncPayloadParserPort {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AnimalCreateJson(
        val id: UUID? = null,
        @JsonAlias("folio_interno") val folioInterno: String? = null,
        @JsonAlias("siniiga_chip", "chip_siniiga") val siniigaChip: String? = null,
        @JsonAlias("registro_uno") val registroUno: String? = null,
        val adn: String? = null,
        @JsonAlias("raza") val breed: String? = null,
        val color: String? = null,
        @JsonAlias("sexo") val sex: Sex? = null,
        @JsonAlias("birth_date", "fecha_nacimiento") val birthDate: LocalDate? = null,
        @JsonAlias("weaning_date", "fecha_destete") val weaningDate: LocalDate? = null,
        @JsonAlias("entry_reason", "motivo_alta") val entryReason: EntryReason = EntryReason.BIRTH,
        val criador: String? = null,
        @JsonAlias("parturition_type", "tipo_parto") val parturitionType: ParturitionType = ParturitionType.SIMPLE,
        @JsonAlias("conception_method", "metodo_concepcion") val conceptionMethod: ConceptionMethod = ConceptionMethod.MN,
        @JsonAlias("sire_id", "padre_id") val sireId: UUID? = null,
        @JsonAlias("dam_id", "madre_id") val damId: UUID? = null,
        @JsonAlias("external_sire_folio", "folio_padre_externo") val externalSireFolio: String? = null,
        @JsonAlias("external_dam_folio", "folio_madre_externo") val externalDamFolio: String? = null,
        @JsonAlias("life_stage", "etapa_vida") val lifeStage: LifeStage? = null,
        @JsonAlias("reproductive_status", "estado_reproductivo") val reproductiveStatus: ReproductiveStatus? = null,
        @JsonAlias("estado") val status: AnimalStatus = AnimalStatus.ACTIVE,
        @JsonAlias("mortality_date", "fecha_muerte") val mortalityDate: LocalDate? = null,
        @JsonAlias("mortality_cause", "causa_muerte") val mortalityCause: String? = null,
        @JsonAlias("life_stage_at_death", "etapa_al_morir") val lifeStageAtDeath: LifeStage? = null,
        @JsonAlias("photo_s3_key", "foto_s3_key") val photoS3Key: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AnimalUpdateJson(
        @JsonAlias("animal_id", "id") val animalId: UUID? = null,
        @JsonAlias("folio_interno") val folioInterno: String? = null,
        @JsonAlias("siniiga_chip", "chip_siniiga") val siniigaChip: String? = null,
        @JsonAlias("registro_uno") val registroUno: String? = null,
        val adn: String? = null,
        @JsonAlias("raza") val breed: String? = null,
        val color: String? = null,
        @JsonAlias("sexo") val sex: Sex? = null,
        @JsonAlias("birth_date", "fecha_nacimiento") val birthDate: LocalDate? = null,
        @JsonAlias("weaning_date", "fecha_destete") val weaningDate: LocalDate? = null,
        @JsonAlias("entry_reason", "motivo_alta") val entryReason: EntryReason? = null,
        val criador: String? = null,
        @JsonAlias("parturition_type", "tipo_parto") val parturitionType: ParturitionType? = null,
        @JsonAlias("conception_method", "metodo_concepcion") val conceptionMethod: ConceptionMethod? = null,
        @JsonAlias("sire_id", "padre_id") val sireId: UUID? = null,
        @JsonAlias("dam_id", "madre_id") val damId: UUID? = null,
        @JsonAlias("external_sire_folio", "folio_padre_externo") val externalSireFolio: String? = null,
        @JsonAlias("external_dam_folio", "folio_madre_externo") val externalDamFolio: String? = null,
        @JsonAlias("life_stage", "etapa_vida") val lifeStage: LifeStage? = null,
        @JsonAlias("reproductive_status", "estado_reproductivo") val reproductiveStatus: ReproductiveStatus? = null,
        @JsonAlias("estado") val status: AnimalStatus? = null,
        @JsonAlias("mortality_date", "fecha_muerte") val mortalityDate: LocalDate? = null,
        @JsonAlias("mortality_cause", "causa_muerte") val mortalityCause: String? = null,
        @JsonAlias("life_stage_at_death", "etapa_al_morir") val lifeStageAtDeath: LifeStage? = null,
        @JsonAlias("photo_s3_key", "foto_s3_key") val photoS3Key: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class BirthLambJson(
        val id: UUID? = null,
        @JsonAlias("folio_interno") val folioInterno: String? = null,
        @JsonAlias("siniiga_chip", "chip_siniiga") val siniigaChip: String? = null,
        @JsonAlias("registro_uno") val registroUno: String? = null,
        val adn: String? = null,
        @JsonAlias("raza") val breed: String? = null,
        val color: String? = null,
        @JsonAlias("sexo") val sex: Sex? = null,
        val criador: String? = null,
        @JsonAlias("life_stage", "etapa_vida") val lifeStage: LifeStage = LifeStage.LAMB_LACTATING,
        @JsonAlias("reproductive_status", "estado_reproductivo") val reproductiveStatus: ReproductiveStatus = ReproductiveStatus.EMPTY,
        @JsonAlias("birth_weight_kg", "peso_nacimiento_kg") val birthWeightKg: BigDecimal? = null,
        @JsonAlias("weight_id") val weightId: UUID? = null,
        @JsonAlias("photo_s3_key", "foto_s3_key") val photoS3Key: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class BirthCreateJson(
        val id: UUID? = null,
        @JsonAlias("dam_id", "madre_id") val damId: UUID? = null,
        @JsonAlias("sire_id", "padre_id") val sireId: UUID? = null,
        @JsonAlias("external_sire_folio", "folio_padre_externo") val externalSireFolio: String? = null,
        @JsonAlias("birth_date", "fecha_nacimiento", "fecha") val birthDate: LocalDate? = null,
        @JsonAlias("live_born_count", "nacidos_vivos") val liveBornCount: Int? = null,
        @JsonAlias("stillborn_count", "nacidos_muertos") val stillbornCount: Int = 0,
        @JsonAlias("conception_method", "metodo_concepcion") val conceptionMethod: ConceptionMethod = ConceptionMethod.MN,
        @JsonAlias("evidence_photo_s3_key", "foto_evidencia_s3_key", "photo_s3_key", "photoS3Key")
        val evidencePhotoS3Key: String? = null,
        @JsonAlias("crias") val lambs: List<BirthLambJson> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class WeightCreateJson(
        val id: UUID? = null,
        @JsonAlias("animal_id") val animalId: UUID? = null,
        @JsonAlias("hito") val milestone: WeightMilestone? = null,
        @JsonAlias("weight_kg", "peso_kg") val weightKg: BigDecimal? = null,
        @JsonAlias("scrotal_circumference_cm", "circunferencia_escrotal_cm") val scrotalCircumferenceCm: BigDecimal? = null,
        @JsonAlias("fecha") val date: LocalDate? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class WeaningCreateJson(
        @JsonAlias("animal_id") val animalId: UUID? = null,
        @JsonAlias("weight_id") val weightId: UUID? = null,
        @JsonAlias("weaning_date", "fecha_destete") val weaningDate: LocalDate? = null,
        @JsonAlias("weaning_weight_kg", "peso_destete_kg") val weaningWeightKg: BigDecimal? = null,
        @JsonAlias("destino") val destination: WeaningDestination? = null,
        @JsonAlias("scrotal_circumference_cm", "circunferencia_escrotal_cm") val scrotalCircumferenceCm: BigDecimal? = null,
    )

    override fun parseAnimalCreate(payloadJson: String): SyncAnimalCreatePayload {
        val raw = deserialize(payloadJson, AnimalCreateJson::class.java)
        return SyncAnimalCreatePayload(
            id = raw.id,
            folioInterno = requireNonBlank(raw.folioInterno, "folioInterno"),
            siniigaChip = raw.siniigaChip,
            registroUno = raw.registroUno,
            adn = raw.adn,
            breed = requireNonBlank(raw.breed, "breed"),
            color = requireNonBlank(raw.color, "color"),
            sex = requireField(raw.sex, "sex"),
            birthDate = requireField(raw.birthDate, "birthDate"),
            weaningDate = raw.weaningDate,
            entryReason = raw.entryReason,
            criador = raw.criador,
            parturitionType = raw.parturitionType,
            conceptionMethod = raw.conceptionMethod,
            sireId = raw.sireId,
            damId = raw.damId,
            externalSireFolio = raw.externalSireFolio,
            externalDamFolio = raw.externalDamFolio,
            lifeStage = requireField(raw.lifeStage, "lifeStage"),
            reproductiveStatus = requireField(raw.reproductiveStatus, "reproductiveStatus"),
            status = raw.status,
            mortalityDate = raw.mortalityDate,
            mortalityCause = raw.mortalityCause,
            lifeStageAtDeath = raw.lifeStageAtDeath,
            photoS3Key = raw.photoS3Key,
        )
    }

    override fun parseAnimalUpdate(payloadJson: String): SyncAnimalUpdatePayload {
        val raw = deserialize(payloadJson, AnimalUpdateJson::class.java)
        return SyncAnimalUpdatePayload(
            animalId = requireField(raw.animalId, "animalId"),
            folioInterno = requireNonBlank(raw.folioInterno, "folioInterno"),
            siniigaChip = raw.siniigaChip,
            registroUno = raw.registroUno,
            adn = raw.adn,
            breed = requireNonBlank(raw.breed, "breed"),
            color = requireNonBlank(raw.color, "color"),
            sex = requireField(raw.sex, "sex"),
            birthDate = requireField(raw.birthDate, "birthDate"),
            weaningDate = raw.weaningDate,
            entryReason = raw.entryReason,
            criador = raw.criador,
            parturitionType = raw.parturitionType,
            conceptionMethod = raw.conceptionMethod,
            sireId = raw.sireId,
            damId = raw.damId,
            externalSireFolio = raw.externalSireFolio,
            externalDamFolio = raw.externalDamFolio,
            lifeStage = requireField(raw.lifeStage, "lifeStage"),
            reproductiveStatus = requireField(raw.reproductiveStatus, "reproductiveStatus"),
            status = raw.status,
            mortalityDate = raw.mortalityDate,
            mortalityCause = raw.mortalityCause,
            lifeStageAtDeath = raw.lifeStageAtDeath,
            photoS3Key = raw.photoS3Key,
        )
    }

    override fun parseBirthCreate(payloadJson: String): SyncBirthCreatePayload {
        val raw = deserialize(payloadJson, BirthCreateJson::class.java)
        return SyncBirthCreatePayload(
            id = raw.id,
            damId = requireField(raw.damId, "damId"),
            sireId = raw.sireId,
            externalSireFolio = raw.externalSireFolio,
            birthDate = requireField(raw.birthDate, "birthDate"),
            liveBornCount = raw.liveBornCount,
            stillbornCount = raw.stillbornCount,
            conceptionMethod = raw.conceptionMethod,
            evidencePhotoS3Key = raw.evidencePhotoS3Key,
            lambs = raw.lambs.mapIndexed { idx, lamb ->
                SyncBirthLambPayload(
                    id = lamb.id,
                    folioInterno = requireNonBlank(lamb.folioInterno, "lambs[$idx].folioInterno"),
                    siniigaChip = lamb.siniigaChip,
                    registroUno = lamb.registroUno,
                    adn = lamb.adn,
                    breed = requireNonBlank(lamb.breed, "lambs[$idx].breed"),
                    color = requireNonBlank(lamb.color, "lambs[$idx].color"),
                    sex = requireField(lamb.sex, "lambs[$idx].sex"),
                    criador = lamb.criador,
                    lifeStage = lamb.lifeStage,
                    reproductiveStatus = lamb.reproductiveStatus,
                    birthWeightKg = lamb.birthWeightKg,
                    weightId = lamb.weightId,
                    photoS3Key = lamb.photoS3Key,
                )
            },
        )
    }

    override fun parseWeightCreate(payloadJson: String): SyncWeightCreatePayload {
        val raw = deserialize(payloadJson, WeightCreateJson::class.java)
        return SyncWeightCreatePayload(
            id = raw.id,
            animalId = requireField(raw.animalId, "animalId"),
            milestone = requireField(raw.milestone, "milestone"),
            weightKg = requireField(raw.weightKg, "weightKg"),
            scrotalCircumferenceCm = raw.scrotalCircumferenceCm,
            date = requireField(raw.date, "date"),
        )
    }

    override fun parseWeaningCreate(payloadJson: String): SyncWeaningCreatePayload {
        val raw = deserialize(payloadJson, WeaningCreateJson::class.java)
        return SyncWeaningCreatePayload(
            animalId = requireField(raw.animalId, "animalId"),
            weightId = raw.weightId,
            weaningDate = requireField(raw.weaningDate, "weaningDate"),
            weaningWeightKg = requireField(raw.weaningWeightKg, "weaningWeightKg"),
            destination = requireField(raw.destination, "destination"),
            scrotalCircumferenceCm = raw.scrotalCircumferenceCm,
        )
    }

    private fun <T> deserialize(payloadJson: String, targetType: Class<T>): T {
        if (payloadJson.isBlank()) {
            throw DomainValidationException("INVALID_SYNC_PAYLOAD", "El payload de la mutación no puede estar vacío.")
        }
        return try {
            objectMapper.readValue(payloadJson, targetType)
        } catch (ex: JsonProcessingException) {
            throw DomainValidationException(
                code = "INVALID_SYNC_PAYLOAD",
                message = "El payload de la mutación no tiene un formato JSON válido.",
            )
        }
    }

    private fun <T : Any> requireField(value: T?, field: String): T =
        value ?: throw DomainValidationException("VALIDATION_ERROR", "Campo obligatorio faltante en payload: $field.")

    private fun requireNonBlank(value: String?, field: String): String =
        value?.takeIf { it.isNotBlank() }
            ?: throw DomainValidationException("VALIDATION_ERROR", "Campo obligatorio faltante en payload: $field.")
}
