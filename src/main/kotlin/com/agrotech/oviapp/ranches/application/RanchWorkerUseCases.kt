package com.agrotech.oviapp.ranches.application

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.ranches.domain.RanchActorPort
import com.agrotech.oviapp.ranches.domain.RanchPolicy
import com.agrotech.oviapp.ranches.domain.RanchRepositoryPort
import com.agrotech.oviapp.ranches.domain.RanchWorker
import com.agrotech.oviapp.ranches.domain.RanchWorkerRepositoryPort
import com.agrotech.oviapp.ranches.domain.WorkerPasswordEncoderPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class CreateRanchWorkerCommand(
    val principalIdentifier: String,
    val ranchId: UUID,
    val username: String,
    val rawPassword: String,
    val permissions: Map<String, Any?> = emptyMap(),
    val active: Boolean = true,
)

data class UpdateRanchWorkerCommand(
    val principalIdentifier: String,
    val ranchId: UUID,
    val workerId: UUID,
    val active: Boolean? = null,
    val permissions: Map<String, Any?>? = null,
)

@Service
class ListRanchWorkersUseCase(
    private val actorPort: RanchActorPort,
    private val workerPort: RanchWorkerRepositoryPort,
) {
    @Transactional(readOnly = true)
    fun execute(principalIdentifier: String, ranchId: UUID): List<RanchWorker> {
        val actor = RanchPolicy.requireActiveActor(actorPort.findByPrincipalIdentifier(principalIdentifier))
        RanchPolicy.requireCanManageWorkers(actor, ranchId)
        return workerPort.findAllByRanchId(ranchId)
    }
}

@Service
class CreateRanchWorkerUseCase(
    private val actorPort: RanchActorPort,
    private val ranchPort: RanchRepositoryPort,
    private val workerPort: RanchWorkerRepositoryPort,
    private val passwordEncoder: WorkerPasswordEncoderPort,
    private val clock: Clock,
) {
    @Transactional
    fun execute(command: CreateRanchWorkerCommand): RanchWorker {
        val actor = RanchPolicy.requireActiveActor(actorPort.findByPrincipalIdentifier(command.principalIdentifier))
        RanchPolicy.requireCanManageWorkers(actor, command.ranchId)

        if (ranchPort.findById(command.ranchId) == null) {
            throw ResourceNotFoundException("RANCH_NOT_FOUND", "Rancho no encontrado.")
        }

        val normalizedUsername = command.username.trim()
        if (normalizedUsername.isBlank()) {
            throw DomainValidationException("VALIDATION_ERROR", "El nombre de usuario es obligatorio.")
        }
        if (workerPort.existsByUsername(normalizedUsername)) {
            throw BusinessConflictException("USERNAME_ALREADY_EXISTS", "Ya existe una cuenta con ese usuario.")
        }

        val worker = RanchWorker(
            id = UUID.randomUUID(),
            ranchId = command.ranchId,
            username = normalizedUsername,
            active = command.active,
            permissions = command.permissions,
            createdAt = clock.instant(),
        )
        return workerPort.create(worker, passwordEncoder.encode(command.rawPassword))
    }
}

@Service
class UpdateRanchWorkerUseCase(
    private val actorPort: RanchActorPort,
    private val workerPort: RanchWorkerRepositoryPort,
    private val clock: Clock,
) {
    @Transactional
    fun execute(command: UpdateRanchWorkerCommand): RanchWorker {
        val actor = RanchPolicy.requireActiveActor(actorPort.findByPrincipalIdentifier(command.principalIdentifier))
        RanchPolicy.requireCanManageWorkers(actor, command.ranchId)

        if (command.active == null && command.permissions == null) {
            throw DomainValidationException("VALIDATION_ERROR", "Debe enviar al menos active o permissions para actualizar.")
        }

        val existing = workerPort.findByIdAndRanchId(command.workerId, command.ranchId)
            ?: throw ResourceNotFoundException("WORKER_NOT_FOUND", "Cuenta WORKER_BRANCH no encontrada en el rancho.")

        val updated = workerPort.save(
            existing.copy(
                active = command.active ?: existing.active,
                permissions = command.permissions ?: existing.permissions,
            ),
        )
        if (command.active == false) {
            workerPort.revokeActiveRefreshTokens(updated.id, clock.instant())
        }
        return updated
    }
}
