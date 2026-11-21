package com.agrotech.oviapp.ranches.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.model.Role
import java.time.Instant
import java.util.UUID

data class RanchActor(
    val userId: UUID,
    val username: String,
    val role: Role,
    val ranchId: UUID?,
    val associationId: UUID?,
    val active: Boolean,
)

data class RanchProfile(
    val id: UUID,
    val name: String,
    val address: String,
    val owner: String,
    val weaningTargetDays: Int,
    val lastSyncedAt: Instant? = null,
) {
    init {
        RanchPolicy.validateWeaningTargetDays(weaningTargetDays)
    }
}

data class RanchWorker(
    val id: UUID,
    val ranchId: UUID,
    val username: String,
    val active: Boolean,
    val permissions: Map<String, Any?>,
    val createdAt: Instant,
)

object RanchPolicy {
    val ALLOWED_WEANING_TARGET_DAYS: Set<Int> = setOf(60, 75, 90)
    private const val MIN_PASSWORD_CHARS = 8
    private const val MAX_BCRYPT_PASSWORD_BYTES = 72

    fun validateWeaningTargetDays(days: Int) {
        if (days !in ALLOWED_WEANING_TARGET_DAYS) {
            throw DomainValidationException(
                code = "INVALID_WEANING_TARGET_DAYS",
                message = "La meta de destete debe ser 60, 75 o 90 días.",
            )
        }
    }

    fun validateWorkerPassword(rawPassword: String) {
        if (rawPassword.isBlank() || rawPassword.length < MIN_PASSWORD_CHARS) {
            throw DomainValidationException(
                code = "INVALID_PASSWORD_LENGTH",
                message = "La contraseña debe tener al menos $MIN_PASSWORD_CHARS caracteres.",
            )
        }
        if (rawPassword.toByteArray(Charsets.UTF_8).size > MAX_BCRYPT_PASSWORD_BYTES) {
            throw DomainValidationException(
                code = "INVALID_PASSWORD_LENGTH",
                message = "La contraseña no debe exceder $MAX_BCRYPT_PASSWORD_BYTES bytes en UTF-8.",
            )
        }
    }

    fun requireActiveActor(actor: RanchActor?): RanchActor {
        if (actor == null || !actor.active) {
            throw ForbiddenAccessException("RANCH_ACCESS_DENIED", "El usuario autenticado no tiene acceso activo.")
        }
        return actor
    }

    fun requireCanEditRanch(actor: RanchActor, ranchId: UUID, isAffiliated: Boolean) {
        requireActiveActor(actor)
        val allowed = when (actor.role) {
            Role.OWNER -> actor.ranchId == ranchId
            Role.WORKER_BRANCH -> false
            Role.VET_TECHNICIAN, Role.ASSOCIATION_SUPERUSER -> actor.ranchId == ranchId || isAffiliated
        }
        if (!allowed) {
            throw ForbiddenAccessException(
                code = "RANCH_EDIT_FORBIDDEN",
                message = "Solo el OWNER del rancho o la Asociación pueden modificar el perfil del rancho.",
            )
        }
    }

    fun requireCanManageWorkers(actor: RanchActor, ranchId: UUID) {
        requireActiveActor(actor)
        if (actor.role != Role.OWNER || actor.ranchId != ranchId) {
            throw ForbiddenAccessException(
                code = "WORKER_MANAGEMENT_FORBIDDEN",
                message = "Solo el OWNER del rancho puede administrar cuentas WORKER_BRANCH.",
            )
        }
    }
}

interface RanchActorPort {
    fun findByPrincipalIdentifier(principalIdentifier: String): RanchActor?
}

interface RanchRepositoryPort {
    fun findById(ranchId: UUID): RanchProfile?
    fun findByAssociationId(associationId: UUID): List<RanchProfile>
    fun isAffiliatedWithAssociation(associationId: UUID, ranchId: UUID): Boolean
    fun save(ranch: RanchProfile): RanchProfile
}

interface RanchWorkerRepositoryPort {
    fun findAllByRanchId(ranchId: UUID): List<RanchWorker>
    fun findByIdAndRanchId(workerId: UUID, ranchId: UUID): RanchWorker?
    fun findByIdAndRanchIdForUpdate(workerId: UUID, ranchId: UUID): RanchWorker?
    fun existsByUsername(username: String): Boolean
    fun create(worker: RanchWorker, passwordHash: String): RanchWorker
    fun updateWorker(
        workerId: UUID,
        ranchId: UUID,
        active: Boolean?,
        permissions: Map<String, Any?>?,
    ): RanchWorker?
    fun revokeActiveRefreshTokens(workerId: UUID, revokedAt: Instant)
}

interface WorkerPasswordEncoderPort {
    fun encode(rawPassword: String): String
}
