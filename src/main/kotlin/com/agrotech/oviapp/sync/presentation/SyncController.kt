package com.agrotech.oviapp.sync.presentation

import com.agrotech.oviapp.auth.domain.JwtClaims
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RequireRanchPermission
import com.agrotech.oviapp.sync.application.ProcessSyncBatchUseCase
import com.agrotech.oviapp.sync.domain.SyncBatchCommand
import com.agrotech.oviapp.sync.domain.SyncBatchMutation
import com.agrotech.oviapp.sync.domain.SyncBatchResult
import com.agrotech.oviapp.sync.domain.SyncBatchResultItem
import com.agrotech.oviapp.sync.domain.SyncEntityType
import com.agrotech.oviapp.sync.domain.SyncMutationStatus
import com.agrotech.oviapp.sync.domain.SyncOperation
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class SyncBatchMutationRequest(
    @field:NotNull
    @JsonAlias("mutation_id")
    val mutationId: UUID? = null,

    @field:NotNull
    @JsonAlias("entity_type")
    val entityType: SyncEntityType? = null,

    @field:NotNull
    val operation: SyncOperation? = null,

    @field:NotNull
    @JsonAlias("occurred_at")
    val occurredAt: Instant? = null,

    @field:NotNull
    @JsonAlias("payload_json", "payload")
    val payloadJson: JsonNode? = null,
)

data class SyncBatchRequest(
    @field:Valid
    @field:NotNull
    @field:Size(max = MAX_BATCH_SIZE)
    val mutations: List<SyncBatchMutationRequest>? = null,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class SyncBatchItemResponse(
    val mutationId: UUID,
    val entityType: SyncEntityType,
    val operation: SyncOperation,
    val status: SyncMutationStatus,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

data class SyncBatchResponse(
    val ranchId: UUID,
    val processedCount: Int,
    val appliedCount: Int,
    val alreadyProcessedCount: Int,
    val rejectedCount: Int,
    val results: List<SyncBatchItemResponse>,
)

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val ranchAccessAuthorizer: RanchAccessAuthorizer,
    private val processSyncBatchUseCase: ProcessSyncBatchUseCase,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/batch")
    @RequireRanchPermission(RanchPermission.CAPTURA)
    fun processBatch(
        authentication: Authentication?,
        @Valid @RequestBody request: SyncBatchRequest,
    ): SyncBatchResponse {
        val authorized = ranchAccessAuthorizer.requireAuthorizedAccess(authentication, RanchPermission.CAPTURA)
        val rawMutations = request.mutations ?: throw invalidPayload("mutations")
        val mutations = rawMutations.map { item ->
            SyncBatchMutation(
                mutationId = item.mutationId ?: throw invalidPayload("mutationId"),
                entityType = item.entityType ?: throw invalidPayload("entityType"),
                operation = item.operation ?: throw invalidPayload("operation"),
                occurredAt = item.occurredAt ?: throw invalidPayload("occurredAt"),
                payloadJson = extractPayloadJson(item.payloadJson),
            )
        }

        val actorRole = resolveActorRole(authentication, authorized.access.role)
        return processSyncBatchUseCase.execute(
            SyncBatchCommand(
                ranchId = authorized.ranchId,
                actorUsername = authorized.principalName,
                actorRole = actorRole,
                canEditAnimals = authorized.access.hasPermission(RanchPermission.EDICION),
                mutations = mutations,
            ),
        ).toResponse()
    }

    private fun extractPayloadJson(node: JsonNode?): String {
        if (node == null || node.isNull) {
            throw invalidPayload("payloadJson")
        }
        return when {
            node.isTextual -> node.asText().takeIf { it.isNotBlank() }
                ?: throw DomainValidationException("INVALID_SYNC_PAYLOAD", "El payload de la mutación no puede estar vacío.")
            node.isObject -> objectMapper.writeValueAsString(node)
            else -> throw DomainValidationException(
                code = "INVALID_SYNC_PAYLOAD",
                message = "El campo payloadJson debe ser un string JSON o un objeto JSON.",
            )
        }
    }

    private fun resolveActorRole(authentication: Authentication?, fallback: Role): Role =
        (authentication?.details as? JwtClaims)?.role ?: fallback

    private fun invalidPayload(field: String) =
        DomainValidationException("VALIDATION_ERROR", "Campo obligatorio faltante: $field.")

    private fun SyncBatchResult.toResponse() = SyncBatchResponse(
        ranchId = ranchId,
        processedCount = processedCount,
        appliedCount = appliedCount,
        alreadyProcessedCount = alreadyProcessedCount,
        rejectedCount = rejectedCount,
        results = results.map { it.toResponse() },
    )

    private fun SyncBatchResultItem.toResponse() = SyncBatchItemResponse(
        mutationId = mutationId,
        entityType = entityType,
        operation = operation,
        status = status,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )
}
