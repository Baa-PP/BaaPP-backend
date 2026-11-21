package com.agrotech.oviapp.ranches.presentation

import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.ranches.application.CreateRanchWorkerCommand
import com.agrotech.oviapp.ranches.application.CreateRanchWorkerUseCase
import com.agrotech.oviapp.ranches.application.ListAccessibleRanchesUseCase
import com.agrotech.oviapp.ranches.application.ListRanchWorkersUseCase
import com.agrotech.oviapp.ranches.application.UpdateRanchCommand
import com.agrotech.oviapp.ranches.application.UpdateRanchProfileUseCase
import com.agrotech.oviapp.ranches.application.UpdateRanchWorkerCommand
import com.agrotech.oviapp.ranches.application.UpdateRanchWorkerUseCase
import com.agrotech.oviapp.ranches.domain.RanchProfile
import com.agrotech.oviapp.ranches.domain.RanchWorker
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class RanchProfileResponse(
    val id: UUID,
    val name: String,
    val address: String,
    val owner: String,
    val weaningTargetDays: Int,
    val lastSyncedAt: Instant?,
)

data class UpdateRanchProfileRequest(
    @field:NotBlank
    @field:Size(max = 150)
    val name: String = "",

    @field:NotBlank
    val address: String = "",

    @field:NotBlank
    @field:Size(max = 150)
    val owner: String = "",

    @field:NotNull
    val weaningTargetDays: Int? = null,
)

data class RanchWorkerResponse(
    val id: UUID,
    val ranchId: UUID,
    val username: String,
    val active: Boolean,
    val permissions: Map<String, Any?>,
    val createdAt: Instant,
)

data class CreateRanchWorkerRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 100)
    val username: String = "",

    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String = "",

    val permissions: Map<String, Any?> = emptyMap(),
    val active: Boolean = true,
)

data class UpdateRanchWorkerRequest(
    val active: Boolean? = null,
    val permissions: Map<String, Any?>? = null,
)

@RestController
@RequestMapping("/api/v1/ranches")
class RanchesController(
    private val listAccessibleRanchesUseCase: ListAccessibleRanchesUseCase,
    private val updateRanchProfileUseCase: UpdateRanchProfileUseCase,
    private val listRanchWorkersUseCase: ListRanchWorkersUseCase,
    private val createRanchWorkerUseCase: CreateRanchWorkerUseCase,
    private val updateRanchWorkerUseCase: UpdateRanchWorkerUseCase,
) {

    @GetMapping
    fun listAccessibleRanches(authentication: Authentication?): List<RanchProfileResponse> {
        val principal = requirePrincipalIdentifier(authentication)
        return listAccessibleRanchesUseCase.execute(principal).map { it.toResponse() }
    }

    @PutMapping("/{id}")
    fun updateRanchProfile(
        authentication: Authentication?,
        @PathVariable("id") ranchId: UUID,
        @Valid @RequestBody request: UpdateRanchProfileRequest,
    ): RanchProfileResponse {
        val principal = requirePrincipalIdentifier(authentication)
        return updateRanchProfileUseCase.execute(
            UpdateRanchCommand(
                principalIdentifier = principal,
                ranchId = ranchId,
                name = request.name,
                address = request.address,
                owner = request.owner,
                weaningTargetDays = request.weaningTargetDays ?: 0,
            ),
        ).toResponse()
    }

    @GetMapping("/{id}/workers")
    fun listWorkers(
        authentication: Authentication?,
        @PathVariable("id") ranchId: UUID,
    ): List<RanchWorkerResponse> {
        val principal = requirePrincipalIdentifier(authentication)
        return listRanchWorkersUseCase.execute(principal, ranchId).map { it.toResponse() }
    }

    @PostMapping("/{id}/workers")
    fun createWorker(
        authentication: Authentication?,
        @PathVariable("id") ranchId: UUID,
        @Valid @RequestBody request: CreateRanchWorkerRequest,
    ): ResponseEntity<RanchWorkerResponse> {
        val principal = requirePrincipalIdentifier(authentication)
        val created = createRanchWorkerUseCase.execute(
            CreateRanchWorkerCommand(
                principalIdentifier = principal,
                ranchId = ranchId,
                username = request.username,
                rawPassword = request.password,
                permissions = request.permissions,
                active = request.active,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    @PatchMapping("/{id}/workers/{workerId}")
    fun updateWorker(
        authentication: Authentication?,
        @PathVariable("id") ranchId: UUID,
        @PathVariable("workerId") workerId: UUID,
        @Valid @RequestBody request: UpdateRanchWorkerRequest,
    ): RanchWorkerResponse {
        val principal = requirePrincipalIdentifier(authentication)
        return updateRanchWorkerUseCase.execute(
            UpdateRanchWorkerCommand(
                principalIdentifier = principal,
                ranchId = ranchId,
                workerId = workerId,
                active = request.active,
                permissions = request.permissions,
            ),
        ).toResponse()
    }

    private fun requirePrincipalIdentifier(authentication: Authentication?): String =
        authentication
            ?.takeIf { it.isAuthenticated }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: throw ForbiddenAccessException("RANCH_ACCESS_DENIED", "El usuario autenticado no tiene acceso activo.")

    private fun RanchProfile.toResponse() = RanchProfileResponse(
        id = id,
        name = name,
        address = address,
        owner = owner,
        weaningTargetDays = weaningTargetDays,
        lastSyncedAt = lastSyncedAt,
    )

    private fun RanchWorker.toResponse() = RanchWorkerResponse(
        id = id,
        ranchId = ranchId,
        username = username,
        active = active,
        permissions = permissions,
        createdAt = createdAt,
    )
}
