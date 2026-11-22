package com.agrotech.oviapp.animals.infrastructure

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
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.infrastructure.persistence.entity.AnimalEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.PesajeEntity
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadUseCase
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.hibernate.exception.ConstraintViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

interface AnimalResumenViewRow {
    fun getAnimalId(): UUID
    fun getRanchId(): UUID
    fun getFolioInterno(): String
    fun getChipSiniiga(): String?
    fun getRegistroUno(): String?
    fun getSexo(): String
    fun getEtapa(): String
    fun getEstadoReproductivo(): String
    fun getRaza(): String
    fun getColor(): String
    fun getFechaNacimiento(): LocalDate
    fun getEdadDias(): Int
    fun getFechaDestete(): LocalDate?
    fun getMotivoAlta(): String
    fun getCriador(): String?
    fun getPropietario(): String
    fun getFotoS3Key(): String?
    fun getUltimoPesoKg(): BigDecimal?
    fun getFechaUltimoPeso(): LocalDate?
    fun getPartos(): Int
    fun getCargada(): Boolean
    fun getEstado(): String
    fun getFechaMuerte(): LocalDate?
    fun getCausaMuerte(): String?
    fun getPadreId(): UUID?
    fun getMadreId(): UUID?
    fun getActualizadoEn(): Instant
}

interface AnimalDetailViewRow : AnimalResumenViewRow {
    fun getAdn(): String?
    fun getTipoParto(): String
    fun getMetodoConcepcion(): String
    fun getFolioPadreExterno(): String?
    fun getFolioMadreExterno(): String?
    fun getEtapaAlMorir(): String?
    fun getPartoId(): UUID?
}

interface AnimalInventoryJpaRepository : JpaRepository<AnimalEntity, UUID> {
    fun findByIdAndRanchoId(id: UUID, ranchoId: UUID): AnimalEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AnimalEntity a WHERE a.id = :id AND a.ranchoId = :ranchoId")
    fun findByIdAndRanchoIdForUpdate(
        @Param("id") id: UUID,
        @Param("ranchoId") ranchoId: UUID,
    ): AnimalEntity?

    fun existsByRanchoIdAndFolioInterno(ranchoId: UUID, folioInterno: String): Boolean
    fun existsByRanchoIdAndFolioInternoAndIdNot(ranchoId: UUID, folioInterno: String, id: UUID): Boolean
    fun existsByRanchoIdAndChipSiniiga(ranchoId: UUID, chipSiniiga: String): Boolean
    fun existsByRanchoIdAndChipSiniigaAndIdNot(ranchoId: UUID, chipSiniiga: String, id: UUID): Boolean

    @Query(
        value = """
            SELECT v.animal_id           AS animalId,
                   v.rancho_id           AS ranchId,
                   v.folio_interno       AS folioInterno,
                   v.chip_siniiga        AS chipSiniiga,
                   v.registro_uno        AS registroUno,
                   v.sexo                AS sexo,
                   v.etapa               AS etapa,
                   v.estado_reproductivo AS estadoReproductivo,
                   v.raza                AS raza,
                   v.color               AS color,
                   v.fecha_nacimiento    AS fechaNacimiento,
                   v.edad_dias           AS edadDias,
                   v.fecha_destete       AS fechaDestete,
                   v.motivo_alta         AS motivoAlta,
                   v.criador             AS criador,
                   v.propietario         AS propietario,
                   v.foto_s3_key         AS fotoS3Key,
                   v.ultimo_peso_kg      AS ultimoPesoKg,
                   v.fecha_ultimo_peso   AS fechaUltimoPeso,
                   v.partos              AS partos,
                   v.cargada             AS cargada,
                   v.estado              AS estado,
                   v.fecha_muerte        AS fechaMuerte,
                   v.causa_muerte        AS causaMuerte,
                   v.padre_id            AS padreId,
                   v.madre_id            AS madreId,
                   a.actualizado_en      AS actualizadoEn
            FROM v_animal_resumen v
            JOIN animales a ON a.id = v.animal_id
            WHERE v.rancho_id = :ranchId
              AND (CAST(:searchPattern AS TEXT) IS NULL
                   OR v.folio_interno ILIKE CAST(:searchPattern AS TEXT) ESCAPE '!'
                   OR v.chip_siniiga ILIKE CAST(:searchPattern AS TEXT) ESCAPE '!'
                   OR v.registro_uno ILIKE CAST(:searchPattern AS TEXT) ESCAPE '!')
              AND (CAST(:sexo AS VARCHAR) IS NULL OR v.sexo = CAST(:sexo AS VARCHAR))
              AND (CAST(:etapaVida AS VARCHAR) IS NULL OR v.etapa = CAST(:etapaVida AS VARCHAR))
              AND (CAST(:estadoReproductivo AS VARCHAR) IS NULL OR v.estado_reproductivo = CAST(:estadoReproductivo AS VARCHAR))
              AND (CAST(:razaPattern AS TEXT) IS NULL OR v.raza ILIKE CAST(:razaPattern AS TEXT) ESCAPE '!')
              AND (CAST(:estado AS VARCHAR) IS NULL OR v.estado = CAST(:estado AS VARCHAR))
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR a.actualizado_en > CAST(:updatedAfter AS TIMESTAMPTZ))
            ORDER BY v.folio_interno ASC, v.animal_id ASC
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM animales a
            WHERE a.rancho_id = :ranchId
              AND (CAST(:searchPattern AS TEXT) IS NULL
                   OR a.folio_interno ILIKE CAST(:searchPattern AS TEXT) ESCAPE '!'
                   OR a.chip_siniiga ILIKE CAST(:searchPattern AS TEXT) ESCAPE '!'
                   OR a.registro_uno ILIKE CAST(:searchPattern AS TEXT) ESCAPE '!')
              AND (CAST(:sexo AS VARCHAR) IS NULL OR a.sexo = CAST(:sexo AS VARCHAR))
              AND (CAST(:etapaVida AS VARCHAR) IS NULL OR a.etapa_vida = CAST(:etapaVida AS VARCHAR))
              AND (CAST(:estadoReproductivo AS VARCHAR) IS NULL OR a.estado_reproductivo = CAST(:estadoReproductivo AS VARCHAR))
              AND (CAST(:razaPattern AS TEXT) IS NULL OR a.raza ILIKE CAST(:razaPattern AS TEXT) ESCAPE '!')
              AND (CAST(:estado AS VARCHAR) IS NULL OR a.estado = CAST(:estado AS VARCHAR))
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR a.actualizado_en > CAST(:updatedAfter AS TIMESTAMPTZ))
        """,
        nativeQuery = true,
    )
    fun searchResumenView(
        @Param("ranchId") ranchId: UUID,
        @Param("searchPattern") searchPattern: String?,
        @Param("sexo") sexo: String?,
        @Param("etapaVida") etapaVida: String?,
        @Param("estadoReproductivo") estadoReproductivo: String?,
        @Param("razaPattern") razaPattern: String?,
        @Param("estado") estado: String?,
        @Param("updatedAfter") updatedAfter: String?,
        pageable: Pageable,
    ): Page<AnimalResumenViewRow>

    @Query(
        value = """
            SELECT v.animal_id           AS animalId,
                   v.rancho_id           AS ranchId,
                   v.folio_interno       AS folioInterno,
                   v.chip_siniiga        AS chipSiniiga,
                   v.registro_uno        AS registroUno,
                   a.adn                 AS adn,
                   v.sexo                AS sexo,
                   v.etapa               AS etapa,
                   v.estado_reproductivo AS estadoReproductivo,
                   v.raza                AS raza,
                   v.color               AS color,
                   v.fecha_nacimiento    AS fechaNacimiento,
                   v.edad_dias           AS edadDias,
                   v.fecha_destete       AS fechaDestete,
                   v.motivo_alta         AS motivoAlta,
                   v.criador             AS criador,
                   v.propietario         AS propietario,
                   v.foto_s3_key         AS fotoS3Key,
                   v.ultimo_peso_kg      AS ultimoPesoKg,
                   v.fecha_ultimo_peso   AS fechaUltimoPeso,
                   v.partos              AS partos,
                   v.cargada             AS cargada,
                   v.estado              AS estado,
                   v.fecha_muerte        AS fechaMuerte,
                   v.causa_muerte        AS causaMuerte,
                   a.etapa_al_morir      AS etapaAlMorir,
                   a.tipo_parto          AS tipoParto,
                   a.metodo_concepcion   AS metodoConcepcion,
                   v.padre_id            AS padreId,
                   v.madre_id            AS madreId,
                   a.folio_padre_externo AS folioPadreExterno,
                   a.folio_madre_externo AS folioMadreExterno,
                   a.parto_id            AS partoId,
                   a.actualizado_en      AS actualizadoEn
            FROM v_animal_resumen v
            JOIN animales a ON a.id = v.animal_id
            WHERE v.animal_id = :animalId AND v.rancho_id = :ranchId
        """,
        nativeQuery = true,
    )
    fun findDetailView(
        @Param("animalId") animalId: UUID,
        @Param("ranchId") ranchId: UUID,
    ): AnimalDetailViewRow?
}

interface AnimalPesajeJpaRepository : JpaRepository<PesajeEntity, UUID> {
    fun findAllByAnimalIdAndRanchoIdOrderByFechaAscIdAsc(
        animalId: UUID,
        ranchoId: UUID,
    ): List<PesajeEntity>
}

@Component
class AnimalRepositoryJpaAdapter(
    private val repository: AnimalInventoryJpaRepository,
    private val entityManager: EntityManager,
) : AnimalRepositoryPort {

    override fun searchSummaries(query: ListAnimalsQuery): AnimalPage {
        val page = repository.searchResumenView(
            ranchId = query.ranchId,
            searchPattern = toContainsLikePattern(query.search),
            sexo = query.sex?.name,
            etapaVida = query.lifeStage?.name,
            estadoReproductivo = query.reproductiveStatus?.name,
            razaPattern = toContainsLikePattern(query.breed),
            estado = query.status?.name,
            updatedAfter = query.updatedAfter?.toString(),
            pageable = PageRequest.of(query.page, query.size),
        )
        return AnimalPage(
            items = page.content.map { it.toSummary() },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }

    override fun findDetailByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalDetail? =
        repository.findDetailView(animalId, ranchId)?.toDetail()

    override fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): AnimalRecord? =
        repository.findByIdAndRanchoId(animalId, ranchId)?.toRecord()

    override fun findByIdAndRanchIdForUpdate(animalId: UUID, ranchId: UUID): AnimalRecord? =
        repository.findByIdAndRanchoIdForUpdate(animalId, ranchId)?.toRecord()

    override fun existsById(animalId: UUID): Boolean = repository.existsById(animalId)

    override fun existsByRanchIdAndFolioInterno(ranchId: UUID, folioInterno: String): Boolean =
        repository.existsByRanchoIdAndFolioInterno(ranchId, folioInterno)

    override fun existsByRanchIdAndFolioInternoAndIdNot(
        ranchId: UUID,
        folioInterno: String,
        excludeAnimalId: UUID,
    ): Boolean = repository.existsByRanchoIdAndFolioInternoAndIdNot(ranchId, folioInterno, excludeAnimalId)

    override fun existsByRanchIdAndSiniigaChip(ranchId: UUID, siniigaChip: String): Boolean =
        repository.existsByRanchoIdAndChipSiniiga(ranchId, siniigaChip)

    override fun existsByRanchIdAndSiniigaChipAndIdNot(
        ranchId: UUID,
        siniigaChip: String,
        excludeAnimalId: UUID,
    ): Boolean = repository.existsByRanchoIdAndChipSiniigaAndIdNot(ranchId, siniigaChip, excludeAnimalId)

    override fun create(animal: AnimalRecord): AnimalRecord {
        if (repository.existsById(animal.id)) {
            throw BusinessConflictException(
                code = "ANIMAL_ID_ALREADY_EXISTS",
                message = "Ya existe un ejemplar registrado con el identificador proporcionado.",
            )
        }
        val entity = animal.toNewEntity()
        // Use EntityManager.persist() directly because AnimalEntity does not implement Persistable<UUID>;
        // saveAndFlush() would invoke merge() and could silently overwrite an existing row under a race.
        flushTranslatingConflicts {
            entityManager.persist(entity)
            entityManager.flush()
        }
        return entity.toRecord()
    }

    override fun update(animal: AnimalRecord): AnimalRecord {
        val existing = repository.findByIdAndRanchoIdForUpdate(animal.id, animal.ranchId)
            ?: throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo.")

        existing.apply {
            folioInterno = animal.folioInterno
            chipSiniiga = animal.siniigaChip
            registroUno = animal.registroUno
            adn = animal.adn
            raza = animal.breed
            color = animal.color
            sexo = animal.sex.name
            fechaNacimiento = animal.birthDate
            fechaDestete = animal.weaningDate
            tipoParto = animal.parturitionType.name
            metodoConcepcion = animal.conceptionMethod.name
            padreId = animal.sireId
            madreId = animal.damId
            folioPadreExterno = animal.externalSireFolio
            folioMadreExterno = animal.externalDamFolio
            etapaVida = animal.lifeStage.name
            estadoReproductivo = animal.reproductiveStatus.name
            estado = animal.status.name
            fechaMuerte = animal.mortalityDate
            causaMuerte = animal.mortalityCause
            etapaAlMorir = animal.lifeStageAtDeath?.name
            motivoAlta = animal.entryReason.name
            criador = animal.criador
            fotoS3Key = animal.photoS3Key
            actualizadoEn = OffsetDateTime.ofInstant(animal.updatedAt, ZoneOffset.UTC)
        }
        return flushTranslatingConflicts { repository.saveAndFlush(existing) }.toRecord()
    }

    private fun <T> flushTranslatingConflicts(block: () -> T): T =
        try {
            block()
        } catch (ex: RuntimeException) {
            val constraintName = generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .firstOrNull { it.sqlState == PG_UNIQUE_VIOLATION }
                ?.constraintName
            when (constraintName) {
                CONSTRAINT_PK -> throw BusinessConflictException(
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
                else -> throw ex
            }
        }

    private fun toContainsLikePattern(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val escaped = trimmed
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
        return "%$escaped%"
    }

    private fun AnimalResumenViewRow.toSummary() = AnimalSummary(
        id = getAnimalId(),
        ranchId = getRanchId(),
        folioInterno = getFolioInterno(),
        siniigaChip = getChipSiniiga(),
        registroUno = getRegistroUno(),
        sex = Sex.valueOf(getSexo()),
        lifeStage = LifeStage.valueOf(getEtapa()),
        reproductiveStatus = ReproductiveStatus.valueOf(getEstadoReproductivo()),
        breed = getRaza(),
        color = getColor(),
        birthDate = getFechaNacimiento(),
        ageDays = getEdadDias(),
        weaningDate = getFechaDestete(),
        entryReason = EntryReason.valueOf(getMotivoAlta()),
        criador = getCriador(),
        propietario = getPropietario(),
        photoS3Key = getFotoS3Key(),
        latestWeightKg = getUltimoPesoKg(),
        latestWeightDate = getFechaUltimoPeso(),
        birthCount = getPartos(),
        pregnant = getCargada(),
        status = AnimalStatus.valueOf(getEstado()),
        mortalityDate = getFechaMuerte(),
        mortalityCause = getCausaMuerte(),
        sireId = getPadreId(),
        damId = getMadreId(),
        updatedAt = getActualizadoEn(),
    )

    private fun AnimalDetailViewRow.toDetail() = AnimalDetail(
        id = getAnimalId(),
        ranchId = getRanchId(),
        folioInterno = getFolioInterno(),
        siniigaChip = getChipSiniiga(),
        registroUno = getRegistroUno(),
        adn = getAdn(),
        breed = getRaza(),
        color = getColor(),
        sex = Sex.valueOf(getSexo()),
        birthDate = getFechaNacimiento(),
        ageDays = getEdadDias(),
        weaningDate = getFechaDestete(),
        entryReason = EntryReason.valueOf(getMotivoAlta()),
        criador = getCriador(),
        propietario = getPropietario(),
        lifeStage = LifeStage.valueOf(getEtapa()),
        reproductiveStatus = ReproductiveStatus.valueOf(getEstadoReproductivo()),
        status = AnimalStatus.valueOf(getEstado()),
        parturitionType = ParturitionType.valueOf(getTipoParto()),
        conceptionMethod = ConceptionMethod.valueOf(getMetodoConcepcion()),
        pregnant = getCargada(),
        birthCount = getPartos(),
        mortalityDate = getFechaMuerte(),
        mortalityCause = getCausaMuerte(),
        lifeStageAtDeath = getEtapaAlMorir()?.let { LifeStage.valueOf(it) },
        sireId = getPadreId(),
        damId = getMadreId(),
        externalSireFolio = getFolioPadreExterno(),
        externalDamFolio = getFolioMadreExterno(),
        birthEventId = getPartoId(),
        latestWeightKg = getUltimoPesoKg(),
        latestWeightDate = getFechaUltimoPeso(),
        weights = emptyList(),
        photoS3Key = getFotoS3Key(),
        photoUrl = null,
        updatedAt = getActualizadoEn(),
    )

    private fun AnimalEntity.toRecord() = AnimalRecord(
        id = id,
        ranchId = ranchoId,
        folioInterno = folioInterno,
        siniigaChip = chipSiniiga,
        registroUno = registroUno,
        adn = adn,
        breed = raza,
        color = color,
        sex = Sex.valueOf(sexo),
        birthDate = fechaNacimiento,
        weaningDate = fechaDestete,
        entryReason = EntryReason.valueOf(motivoAlta),
        criador = criador,
        parturitionType = ParturitionType.valueOf(tipoParto),
        conceptionMethod = ConceptionMethod.valueOf(metodoConcepcion),
        sireId = padreId,
        damId = madreId,
        externalSireFolio = folioPadreExterno,
        externalDamFolio = folioMadreExterno,
        lifeStage = LifeStage.valueOf(etapaVida),
        reproductiveStatus = ReproductiveStatus.valueOf(estadoReproductivo),
        status = AnimalStatus.valueOf(estado),
        mortalityDate = fechaMuerte,
        mortalityCause = causaMuerte,
        lifeStageAtDeath = etapaAlMorir?.let { LifeStage.valueOf(it) },
        photoS3Key = fotoS3Key,
        birthEventId = partoId,
        updatedAt = actualizadoEn.toInstant(),
    )

    private fun AnimalRecord.toNewEntity(): AnimalEntity {
        val nowOffset = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC)
        return AnimalEntity(
            id = id,
            ranchoId = ranchId,
            folioInterno = folioInterno,
            chipSiniiga = siniigaChip,
            registroUno = registroUno,
            adn = adn,
            raza = breed,
            color = color,
            sexo = sex.name,
            fechaNacimiento = birthDate,
            fechaDestete = weaningDate,
            tipoParto = parturitionType.name,
            metodoConcepcion = conceptionMethod.name,
            padreId = sireId,
            madreId = damId,
            folioPadreExterno = externalSireFolio,
            folioMadreExterno = externalDamFolio,
            etapaVida = lifeStage.name,
            estadoReproductivo = reproductiveStatus.name,
            estado = status.name,
            fechaMuerte = mortalityDate,
            causaMuerte = mortalityCause,
            etapaAlMorir = lifeStageAtDeath?.name,
            motivoAlta = entryReason.name,
            criador = criador,
            fotoS3Key = photoS3Key,
            partoId = birthEventId,
            creadoEn = nowOffset,
            actualizadoEn = nowOffset,
        )
    }

    companion object {
        private const val PG_UNIQUE_VIOLATION = "23505"
        private const val CONSTRAINT_PK = "animales_pkey"
        private const val CONSTRAINT_FOLIO = "animales_rancho_id_folio_interno_key"
        private const val CONSTRAINT_SINIIGA = "uq_animales_rancho_chip"
    }
}

@Component
class AnimalWeightReadJpaAdapter(
    private val repository: AnimalPesajeJpaRepository,
) : AnimalWeightReadPort {
    override fun findAllByAnimalAndRanchOrderedByDateAsc(
        animalId: UUID,
        ranchId: UUID,
    ): List<AnimalWeightRecord> =
        repository.findAllByAnimalIdAndRanchoIdOrderByFechaAscIdAsc(animalId, ranchId)
            .map { entity ->
                AnimalWeightRecord(
                    id = entity.getId(),
                    milestone = entity.hito,
                    weightKg = entity.pesoKg,
                    scrotalCircumferenceCm = entity.circunferenciaEscrotalCm,
                    date = entity.fecha,
                )
            }
}

@Component
class AnimalPhotoUrlMediaAdapter(
    private val generatePresignedDownloadUseCase: GeneratePresignedDownloadUseCase,
) : AnimalPhotoUrlPort {
    override fun generatePresignedDownloadUrl(ranchId: UUID, s3Key: String): String =
        generatePresignedDownloadUseCase.execute(
            GeneratePresignedDownloadCommand(
                ranchId = ranchId,
                s3Key = s3Key,
            ),
        ).downloadUrl
}
