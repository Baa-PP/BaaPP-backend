package com.agrotech.oviapp.ranches.infrastructure

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.infrastructure.persistence.AsociacionRanchoJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.UsuarioJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.entity.MutacionProcesadaEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.RanchoEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.RefreshTokenEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.UsuarioEntity
import com.agrotech.oviapp.ranches.domain.RanchActor
import com.agrotech.oviapp.ranches.domain.RanchActorPort
import com.agrotech.oviapp.ranches.domain.RanchProfile
import com.agrotech.oviapp.ranches.domain.RanchRepositoryPort
import com.agrotech.oviapp.ranches.domain.RanchWorker
import com.agrotech.oviapp.ranches.domain.RanchWorkerRepositoryPort
import com.agrotech.oviapp.ranches.domain.WorkerPasswordEncoderPort
import jakarta.persistence.LockModeType
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

interface RanchProfileJpaRepository : JpaRepository<RanchoEntity, UUID> {
    @Query(
        value = """
            SELECT r.*
            FROM ranchos r
            JOIN asociaciones_ranchos ar ON ar.rancho_id = r.id
            WHERE ar.asociacion_id = :asociacionId
            ORDER BY r.nombre ASC
        """,
        nativeQuery = true,
    )
    fun findAllByAssociationId(@Param("asociacionId") associationId: UUID): List<RanchoEntity>
}

interface RanchLastSyncProjection {
    fun getRanchId(): UUID
    fun getLastSyncedAt(): Instant?
}

interface RanchSyncJpaRepository : JpaRepository<MutacionProcesadaEntity, UUID> {
    @Query(
        value = """
            SELECT rancho_id AS ranchId, MAX(procesado_en) AS lastSyncedAt
            FROM mutaciones_procesadas
            WHERE rancho_id IN (:ranchIds)
            GROUP BY rancho_id
        """,
        nativeQuery = true,
    )
    fun findLastSyncedByRanchIds(@Param("ranchIds") ranchIds: Collection<UUID>): List<RanchLastSyncProjection>
}

interface RanchWorkerJpaRepository : JpaRepository<UsuarioEntity, UUID> {
    fun findAllByRanchoIdAndRolOrderByUsuarioAsc(ranchoId: UUID, rol: String): List<UsuarioEntity>
    fun findByIdAndRanchoIdAndRol(id: UUID, ranchoId: UUID, rol: String): UsuarioEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UsuarioEntity u WHERE u.id = :id AND u.ranchoId = :ranchoId AND u.rol = :rol")
    fun findByIdAndRanchoIdAndRolForUpdate(
        @Param("id") id: UUID,
        @Param("ranchoId") ranchoId: UUID,
        @Param("rol") rol: String,
    ): UsuarioEntity?

    fun existsByUsuario(usuario: String): Boolean
}

interface WorkerRefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, UUID> {
    @Modifying
    @Query(
        "UPDATE RefreshTokenEntity rt SET rt.revocadoEn = :revokedAt WHERE rt.usuarioId = :userId AND rt.revocadoEn IS NULL",
    )
    fun revokeAllActiveByUsuarioId(
        @Param("userId") userId: UUID,
        @Param("revokedAt") revokedAt: OffsetDateTime,
    ): Int
}

@Component
class RanchActorJpaAdapter(
    private val usuarioRepository: UsuarioJpaRepository,
) : RanchActorPort {
    override fun findByPrincipalIdentifier(principalIdentifier: String): RanchActor? {
        val entity = usuarioRepository.findByUsuario(principalIdentifier)
            ?: runCatching { UUID.fromString(principalIdentifier) }
                .getOrNull()
                ?.let { id -> usuarioRepository.findById(id).orElse(null) }
            ?: return null
        val role = runCatching { Role.valueOf(entity.rol) }.getOrNull() ?: return null
        return RanchActor(
            userId = entity.id,
            username = entity.usuario,
            role = role,
            ranchId = entity.ranchoId,
            associationId = entity.asociacionId,
            active = entity.activo,
        )
    }
}

@Component
class RanchRepositoryJpaAdapter(
    private val ranchRepository: RanchProfileJpaRepository,
    private val syncRepository: RanchSyncJpaRepository,
    private val asociacionRanchoRepository: AsociacionRanchoJpaRepository,
) : RanchRepositoryPort {

    override fun findById(ranchId: UUID): RanchProfile? {
        val entity = ranchRepository.findById(ranchId).orElse(null) ?: return null
        val lastSynced = syncRepository.findLastSyncedByRanchIds(listOf(entity.id))
            .firstOrNull()
            ?.getLastSyncedAt()
        return entity.toProfile(lastSynced)
    }

    override fun findByAssociationId(associationId: UUID): List<RanchProfile> {
        val entities = ranchRepository.findAllByAssociationId(associationId)
        if (entities.isEmpty()) return emptyList()
        val syncMap = syncRepository.findLastSyncedByRanchIds(entities.map { it.id })
            .associate { it.getRanchId() to it.getLastSyncedAt() }
        return entities.map { it.toProfile(syncMap[it.id]) }
    }

    override fun isAffiliatedWithAssociation(associationId: UUID, ranchId: UUID): Boolean =
        asociacionRanchoRepository.existsByIdAsociacionIdAndIdRanchoId(associationId, ranchId)

    override fun save(ranch: RanchProfile): RanchProfile {
        val entity = ranchRepository.findById(ranch.id).orElse(null)
            ?: return ranch.also {
                ranchRepository.save(
                    RanchoEntity(
                        id = ranch.id,
                        nombre = ranch.name,
                        direccion = ranch.address,
                        propietario = ranch.owner,
                        diasMetaDestete = ranch.weaningTargetDays,
                    ),
                )
            }
        entity.nombre = ranch.name
        entity.direccion = ranch.address
        entity.propietario = ranch.owner
        entity.diasMetaDestete = ranch.weaningTargetDays
        val saved = ranchRepository.save(entity)
        return saved.toProfile(ranch.lastSyncedAt)
    }

    private fun RanchoEntity.toProfile(lastSyncedAt: Instant?) = RanchProfile(
        id = id,
        name = nombre,
        address = direccion,
        owner = propietario,
        weaningTargetDays = diasMetaDestete,
        lastSyncedAt = lastSyncedAt,
    )
}

@Component
class RanchWorkerRepositoryJpaAdapter(
    private val workerRepository: RanchWorkerJpaRepository,
    private val refreshTokenRepository: WorkerRefreshTokenJpaRepository,
) : RanchWorkerRepositoryPort {

    override fun findAllByRanchId(ranchId: UUID): List<RanchWorker> =
        workerRepository.findAllByRanchoIdAndRolOrderByUsuarioAsc(ranchId, Role.WORKER_BRANCH.name)
            .map { it.toWorker() }

    override fun findByIdAndRanchId(workerId: UUID, ranchId: UUID): RanchWorker? =
        workerRepository.findByIdAndRanchoIdAndRol(workerId, ranchId, Role.WORKER_BRANCH.name)
            ?.toWorker()

    override fun findByIdAndRanchIdForUpdate(workerId: UUID, ranchId: UUID): RanchWorker? =
        workerRepository.findByIdAndRanchoIdAndRolForUpdate(workerId, ranchId, Role.WORKER_BRANCH.name)
            ?.toWorker()

    override fun existsByUsername(username: String): Boolean =
        workerRepository.existsByUsuario(username)

    override fun create(worker: RanchWorker, passwordHash: String): RanchWorker {
        val entity = UsuarioEntity(
            id = worker.id,
            ranchoId = worker.ranchId,
            asociacionId = null,
            usuario = worker.username,
            contrasenaHash = passwordHash,
            rol = Role.WORKER_BRANCH.name,
            permisos = worker.permissions,
            activo = worker.active,
            creadoEn = OffsetDateTime.ofInstant(worker.createdAt, ZoneOffset.UTC),
        )
        val saved = try {
            workerRepository.saveAndFlush(entity)
        } catch (ex: DataIntegrityViolationException) {
            val duplicateUser = generateSequence(ex.cause) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .any { it.sqlState == "23505" && it.constraintName == "usuarios_usuario_key" }
            if (!duplicateUser) throw ex
            throw BusinessConflictException("USERNAME_ALREADY_EXISTS", "Ya existe una cuenta con ese usuario.")
        }
        return saved.toWorker()
    }

    override fun updateWorker(
        workerId: UUID,
        ranchId: UUID,
        active: Boolean?,
        permissions: Map<String, Any?>?,
    ): RanchWorker? {
        val existing = workerRepository.findByIdAndRanchoIdAndRolForUpdate(workerId, ranchId, Role.WORKER_BRANCH.name)
            ?: return null
        if (active != null) {
            existing.activo = active
        }
        if (permissions != null) {
            existing.permisos = permissions
        }
        return workerRepository.save(existing).toWorker()
    }

    override fun revokeActiveRefreshTokens(workerId: UUID, revokedAt: Instant) {
        refreshTokenRepository.revokeAllActiveByUsuarioId(workerId, OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC))
    }

    private fun UsuarioEntity.toWorker() = RanchWorker(
        id = id,
        ranchId = ranchoId ?: throw IllegalStateException("Worker missing rancho_id"),
        username = usuario,
        active = activo,
        permissions = permisos,
        createdAt = creadoEn.toInstant(),
    )
}

@Component
class WorkerBCryptPasswordEncoderAdapter : WorkerPasswordEncoderPort {
    private val encoder = BCryptPasswordEncoder()
    override fun encode(rawPassword: String): String = encoder.encode(rawPassword)
}
