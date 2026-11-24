package com.agrotech.oviapp.sync.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.agrotech.oviapp.sync.application.ProcessSyncBatchUseCase
import com.agrotech.oviapp.sync.domain.SyncBatchCommand
import com.agrotech.oviapp.sync.domain.SyncBatchResult
import com.agrotech.oviapp.sync.domain.SyncBatchResultItem
import com.agrotech.oviapp.sync.domain.SyncEntityType
import com.agrotech.oviapp.sync.domain.SyncMutationStatus
import com.agrotech.oviapp.sync.domain.SyncOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

@WebMvcTest(SyncController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class, RanchAccessAuthorizer::class)
class SyncControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var processSyncBatchUseCase: ProcessSyncBatchUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val ownerPrincipal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")

    @BeforeEach
    fun setUpMembership() {
        given(ranchMembershipPort.resolveAccess("producer_1", ranchId)).willReturn(RanchAccess(Role.OWNER))
        given(ranchMembershipPort.resolveAccess("producer_1", otherRanchId)).willReturn(null)
    }

    @Test
    fun `accepts snake_case and camelCase mutations in order and returns per-mutation status`() {
        val m1 = UUID.fromString("01935000-0000-7000-8000-000000000001")
        val m2 = UUID.fromString("01935000-0000-7000-8000-000000000002")

        val expected = SyncBatchResult(
            ranchId = ranchId,
            processedCount = 2,
            appliedCount = 1,
            alreadyProcessedCount = 1,
            rejectedCount = 0,
            results = listOf(
                SyncBatchResultItem(m1, SyncEntityType.ANIMAL, SyncOperation.CREATE, SyncMutationStatus.APPLIED),
                SyncBatchResultItem(m2, SyncEntityType.WEIGHT_MEASUREMENT, SyncOperation.CREATE, SyncMutationStatus.ALREADY_PROCESSED),
            ),
        )
        val captor = ArgumentCaptor.forClass(SyncBatchCommand::class.java)
        given(processSyncBatchUseCase.execute(captor.capture() ?: anyCommand())).willReturn(expected)

        mockMvc.post("/api/v1/sync/batch") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "mutations": [
                    {
                      "mutation_id": "$m1",
                      "entity_type": "ANIMAL",
                      "operation": "CREATE",
                      "occurred_at": "2026-10-15T10:00:00Z",
                      "payload_json": "{\"folioInterno\":\"OV-1\"}"
                    },
                    {
                      "mutationId": "$m2",
                      "entityType": "WEIGHT_MEASUREMENT",
                      "operation": "CREATE",
                      "occurredAt": "2026-10-15T10:00:01Z",
                      "payloadJson": {"animalId": "22222222-2222-2222-2222-222222222222", "milestone": "BIRTH", "weightKg": 3.5, "date": "2026-10-15"}
                    }
                  ]
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.ranchId") { value(ranchId.toString()) }
            jsonPath("$.processedCount") { value(2) }
            jsonPath("$.appliedCount") { value(1) }
            jsonPath("$.alreadyProcessedCount") { value(1) }
            jsonPath("$.results[0].mutationId") { value(m1.toString()) }
            jsonPath("$.results[0].status") { value("APPLIED") }
            jsonPath("$.results[1].mutationId") { value(m2.toString()) }
            jsonPath("$.results[1].status") { value("ALREADY_PROCESSED") }
        }

        verify(processSyncBatchUseCase).execute(captor.value)
        assertEquals(ranchId, captor.value.ranchId)
        assertEquals(listOf(m1, m2), captor.value.mutations.map { it.mutationId })
        assertEquals(true, captor.value.canEditAnimals)
    }

    @Test
    fun `WORKER_BRANCH without captura receives 403 and unauthorized ranch receives 403`() {
        val readOnlyWorker = TestingAuthenticationToken("worker_ro", null, "ROLE_WORKER_BRANCH")
        given(ranchMembershipPort.resolveAccess("worker_ro", ranchId))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf(RanchPermission.LECTURA.jsonKey to true)))

        mockMvc.post("/api/v1/sync/batch") {
            principal = readOnlyWorker
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"mutations":[]}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.post("/api/v1/sync/batch") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"mutations":[]}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        verifyNoInteractions(processSyncBatchUseCase)
    }

    @Test
    fun `missing X-Active-Ranch-Id header returns 400 MISSING_RANCH_CONTEXT`() {
        mockMvc.post("/api/v1/sync/batch") {
            principal = ownerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """{"mutations":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_RANCH_CONTEXT") }
        }

        verifyNoInteractions(processSyncBatchUseCase)
    }

    private fun anyCommand() = SyncBatchCommand(
        ranchId = ranchId,
        actorUsername = "producer_1",
        actorRole = Role.OWNER,
        canEditAnimals = true,
        mutations = emptyList(),
    )
}
