package com.agrotech.oviapp.ranches.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
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
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

@WebMvcTest(RanchesController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class RanchesControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    @MockitoBean
    private lateinit var listAccessibleRanchesUseCase: ListAccessibleRanchesUseCase

    @MockitoBean
    private lateinit var updateRanchProfileUseCase: UpdateRanchProfileUseCase

    @MockitoBean
    private lateinit var listRanchWorkersUseCase: ListRanchWorkersUseCase

    @MockitoBean
    private lateinit var createRanchWorkerUseCase: CreateRanchWorkerUseCase

    @MockitoBean
    private lateinit var updateRanchWorkerUseCase: UpdateRanchWorkerUseCase

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val workerId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val ownerPrincipal = TestingAuthenticationToken("rancho.sauces", null, "ROLE_OWNER")

    @Test
    fun `GET ranches returns accessible ranches and lastSyncedAt`() {
        val syncedAt = Instant.parse("2026-11-20T18:30:00Z")
        given(listAccessibleRanchesUseCase.execute("rancho.sauces")).willReturn(
            listOf(RanchProfile(ranchId, "Rancho Los Sauces", "Km 12", "Carlos", 75, syncedAt)),
        )

        mockMvc.get("/api/v1/ranches") {
            principal = ownerPrincipal
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(ranchId.toString()) }
            jsonPath("$[0].name") { value("Rancho Los Sauces") }
            jsonPath("$[0].weaningTargetDays") { value(75) }
            jsonPath("$[0].lastSyncedAt") { value("2026-11-20T18:30:00Z") }
        }
    }

    @Test
    fun `PUT ranch updates profile and returns 403 when role is forbidden`() {
        val validCommand = UpdateRanchCommand("rancho.sauces", ranchId, "Nuevo Nombre", "Nueva Dir", "Carlos", 60)
        given(updateRanchProfileUseCase.execute(validCommand))
            .willReturn(RanchProfile(ranchId, "Nuevo Nombre", "Nueva Dir", "Carlos", 60, null))

        mockMvc.put("/api/v1/ranches/$ranchId") {
            principal = ownerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Nuevo Nombre","address":"Nueva Dir","owner":"Carlos","weaningTargetDays":60}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Nuevo Nombre") }
            jsonPath("$.weaningTargetDays") { value(60) }
        }

        val workerPrincipal = TestingAuthenticationToken("worker_1", null, "ROLE_WORKER_BRANCH")
        given(updateRanchProfileUseCase.execute(validCommand.copy(principalIdentifier = "worker_1")))
            .willThrow(ForbiddenAccessException("RANCH_EDIT_FORBIDDEN", "Solo OWNER o Asociación pueden editar el rancho."))

        mockMvc.put("/api/v1/ranches/$ranchId") {
            principal = workerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Nuevo Nombre","address":"Nueva Dir","owner":"Carlos","weaningTargetDays":60}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_EDIT_FORBIDDEN") }
        }
    }

    @Test
    fun `worker endpoints list, create with 201 or 409, and patch without exposing password`() {
        val createdAt = Instant.parse("2026-11-21T01:00:00Z")
        val worker = RanchWorker(
            id = workerId,
            ranchId = ranchId,
            username = "capataz_1",
            active = true,
            permissions = mapOf("read_inventory" to true),
            createdAt = createdAt,
        )
        given(listRanchWorkersUseCase.execute("rancho.sauces", ranchId)).willReturn(listOf(worker))
        given(
            createRanchWorkerUseCase.execute(
                CreateRanchWorkerCommand("rancho.sauces", ranchId, "capataz_1", "Password123!", mapOf("read_inventory" to true)),
            ),
        ).willReturn(worker)
        given(
            createRanchWorkerUseCase.execute(
                CreateRanchWorkerCommand("rancho.sauces", ranchId, "duplicado", "Password123!", emptyMap()),
            ),
        ).willThrow(BusinessConflictException("USERNAME_ALREADY_EXISTS", "El usuario ya existe."))
        given(
            updateRanchWorkerUseCase.execute(
                UpdateRanchWorkerCommand("rancho.sauces", ranchId, workerId, active = false, permissions = mapOf("read_inventory" to false)),
            ),
        ).willReturn(worker.copy(active = false, permissions = mapOf("read_inventory" to false)))

        mockMvc.get("/api/v1/ranches/$ranchId/workers") {
            principal = ownerPrincipal
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].username") { value("capataz_1") }
            jsonPath("$[0].active") { value(true) }
            jsonPath("$[0].passwordHash") { doesNotExist() }
        }

        mockMvc.post("/api/v1/ranches/$ranchId/workers") {
            principal = ownerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"capataz_1","password":"Password123!","permissions":{"read_inventory":true}}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(workerId.toString()) }
            jsonPath("$.passwordHash") { doesNotExist() }
        }

        mockMvc.post("/api/v1/ranches/$ranchId/workers") {
            principal = ownerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"duplicado","password":"Password123!"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USERNAME_ALREADY_EXISTS") }
        }

        mockMvc.patch("/api/v1/ranches/$ranchId/workers/$workerId") {
            principal = ownerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """{"active":false,"permissions":{"read_inventory":false}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(false) }
            jsonPath("$.permissions.read_inventory") { value(false) }
        }
    }
}
