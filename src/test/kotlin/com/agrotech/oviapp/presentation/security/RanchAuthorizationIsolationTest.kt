package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.auth.infrastructure.HmacJwtTokenAdapter
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadUseCase
import com.agrotech.oviapp.media.application.GeneratePresignedUploadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedUploadUseCase
import com.agrotech.oviapp.media.domain.PresignedDownloadResult
import com.agrotech.oviapp.media.domain.PresignedUploadResult
import com.agrotech.oviapp.media.presentation.MediaController
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.weights.application.AnimalWeightHistoryResult
import com.agrotech.oviapp.weights.application.ConfirmWeaningUseCase
import com.agrotech.oviapp.weights.application.GetAnimalWeightsUseCase
import com.agrotech.oviapp.weights.application.RegisterWeightCommand
import com.agrotech.oviapp.weights.application.RegisterWeightUseCase
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.presentation.WeightsController
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/test-auth")
class TestRanchAuthorizationController {

    @GetMapping("/plain")
    fun plainEndpoint(): Map<String, String> = mapOf("status" to "ok")

    @PutMapping("/edicion")
    @RequireRanchPermission(RanchPermission.EDICION)
    fun editEndpoint(): Map<String, String> = mapOf("action" to "edicion")

    @GetMapping("/exportacion")
    @RequireRanchPermission(RanchPermission.EXPORTACION)
    fun exportEndpoint(): Map<String, String> = mapOf("action" to "exportacion")
}

@RestController
@RequestMapping("/api/v1/test-auth-class")
@RequireRanchPermission(RanchPermission.LECTURA)
class ClassLevelPermissionTestController {

    @GetMapping("/report")
    fun report(): Map<String, String> = mapOf("scope" to "class")
}

@WebMvcTest(
    WeightsController::class,
    MediaController::class,
    TestRanchAuthorizationController::class,
    ClassLevelPermissionTestController::class,
)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    RanchAccessAuthorizer::class,
    GlobalExceptionHandler::class,
    RanchAuthorizationIsolationTest.JwtTestConfig::class,
)
class RanchAuthorizationIsolationTest {

    @TestConfiguration
    class JwtTestConfig {
        @Bean
        fun jwtTokenPort(objectMapper: ObjectMapper): JwtTokenPort = HmacJwtTokenAdapter(
            secret = "test-only-hmac-secret-at-least-32-bytes",
            accessTtlSeconds = 900,
            objectMapper = objectMapper,
            clock = Clock.systemUTC(),
        )
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtTokenPort: JwtTokenPort

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var registerWeightUseCase: RegisterWeightUseCase

    @MockitoBean
    private lateinit var getAnimalWeightsUseCase: GetAnimalWeightsUseCase

    @MockitoBean
    private lateinit var confirmWeaningUseCase: ConfirmWeaningUseCase

    @MockitoBean
    private lateinit var generatePresignedUploadUseCase: GeneratePresignedUploadUseCase

    @MockitoBean
    private lateinit var generatePresignedDownloadUseCase: GeneratePresignedDownloadUseCase

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val associationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `OWNER and WORKER_BRANCH manipulating X-Active-Ranch-Id to foreign ranch receive 403`() {
        val ownerToken = issueToken("owner_a", Role.OWNER, ranchId = ranchA)
        val workerToken = issueToken(
            "worker_a",
            Role.WORKER_BRANCH,
            ranchId = ranchA,
            permissions = mapOf("lectura" to true, "captura" to true),
        )
        given(ranchMembershipPort.resolveAccess("owner_a", ranchB)).willReturn(null)
        given(ranchMembershipPort.resolveAccess("worker_a", ranchB)).willReturn(null)

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $ownerToken")
            header("X-Active-Ranch-Id", ranchB.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $workerToken")
            header("X-Active-Ranch-Id", ranchB.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        verifyNoInteractions(getAnimalWeightsUseCase)
        assertNull(RanchContextInterceptor.currentRanchId())
        assertNull(RanchContextInterceptor.currentVerifiedContext())
    }

    @Test
    fun `ASSOCIATION_SUPERUSER and VET_TECHNICIAN access affiliated ranch and receive 403 on unaffiliated ranch`() {
        val superuserToken = issueToken("assoc_admin", Role.ASSOCIATION_SUPERUSER, associationId = associationId)
        val vetToken = issueToken("vet_1", Role.VET_TECHNICIAN, associationId = associationId)

        given(ranchMembershipPort.resolveAccess("assoc_admin", ranchA)).willReturn(RanchAccess(Role.ASSOCIATION_SUPERUSER))
        given(ranchMembershipPort.resolveAccess("assoc_admin", ranchB)).willReturn(null)
        given(ranchMembershipPort.resolveAccess("vet_1", ranchA)).willReturn(RanchAccess(Role.VET_TECHNICIAN))
        given(ranchMembershipPort.resolveAccess("vet_1", ranchB)).willReturn(null)
        given(getAnimalWeightsUseCase.execute(ranchA, animalId)).willReturn(emptyWeightHistory())

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $superuserToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.folioInterno") { value("OV-01") }
        }

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $superuserToken")
            header("X-Active-Ranch-Id", ranchB.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $vetToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $vetToken")
            header("X-Active-Ranch-Id", ranchB.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        verify(ranchMembershipPort, times(1)).resolveAccess("assoc_admin", ranchA)
        verify(ranchMembershipPort, times(1)).resolveAccess("vet_1", ranchA)
    }

    @Test
    fun `WORKER_BRANCH requires lectura, captura, edicion, and exportacion JSONB permissions respectively`() {
        val readOnlyToken = issueToken("worker_read", Role.WORKER_BRANCH, ranchId = ranchA)
        val captureOnlyToken = issueToken("worker_capture", Role.WORKER_BRANCH, ranchId = ranchA)
        val editOnlyToken = issueToken("worker_edit", Role.WORKER_BRANCH, ranchId = ranchA)
        val exportOnlyToken = issueToken("worker_export", Role.WORKER_BRANCH, ranchId = ranchA)

        given(ranchMembershipPort.resolveAccess("worker_read", ranchA))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf("lectura" to true)))
        given(ranchMembershipPort.resolveAccess("worker_capture", ranchA))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf("captura" to true)))
        given(ranchMembershipPort.resolveAccess("worker_edit", ranchA))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf("edicion" to true)))
        given(ranchMembershipPort.resolveAccess("worker_export", ranchA))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf("exportacion" to true)))

        given(getAnimalWeightsUseCase.execute(ranchA, animalId)).willReturn(emptyWeightHistory())
        given(
            generatePresignedDownloadUseCase.execute(
                GeneratePresignedDownloadCommand(ranchA, "$ranchA/births/a.webp"),
            ),
        ).willReturn(PresignedDownloadResult("https://s3.example/get", "$ranchA/births/a.webp", 300L))
        given(
            generatePresignedUploadUseCase.execute(
                GeneratePresignedUploadCommand(ranchA, "image/webp", 1024L, "births"),
            ),
        ).willReturn(PresignedUploadResult("https://s3.example/put", "$ranchA/births/new.webp", 300L))
        val weightCommand = RegisterWeightCommand(
            ranchId = ranchA,
            animalId = animalId,
            milestone = WeightMilestone.WEANING,
            weightKg = BigDecimal("21.50"),
            scrotalCircumferenceCm = null,
            date = LocalDate.of(2026, 10, 15),
        )
        given(registerWeightUseCase.execute(weightCommand)).willReturn(
            WeightMeasurement(
                id = UUID.randomUUID(),
                animalId = animalId,
                ranchId = ranchA,
                milestone = WeightMilestone.WEANING,
                weightKg = BigDecimal("21.50"),
                scrotalCircumferenceCm = null,
                date = LocalDate.of(2026, 10, 15),
            ),
        )

        // LECTURA: allowed for worker_read, denied for worker_capture
        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $readOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/media/presigned-download") {
            header("Authorization", "Bearer $readOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
            param("s3Key", "$ranchA/births/a.webp")
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/test-auth-class/report") {
            header("Authorization", "Bearer $readOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $captureOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.get("/api/v1/test-auth-class/report") {
            header("Authorization", "Bearer $captureOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        // CAPTURA: denied for worker_read, allowed for worker_capture
        val weightBody = """
            {
              "animalId": "$animalId",
              "milestone": "WEANING",
              "weightKg": 21.50,
              "date": "2026-10-15"
            }
        """.trimIndent()
        mockMvc.post("/api/v1/weights") {
            header("Authorization", "Bearer $readOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
            contentType = MediaType.APPLICATION_JSON
            content = weightBody
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.post("/api/v1/weights") {
            header("Authorization", "Bearer $captureOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
            contentType = MediaType.APPLICATION_JSON
            content = weightBody
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/media/presigned-upload") {
            header("Authorization", "Bearer $readOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/webp","sizeBytes":1024,"category":"births"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.post("/api/v1/media/presigned-upload") {
            header("Authorization", "Bearer $captureOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/webp","sizeBytes":1024,"category":"births"}"""
        }.andExpect { status { isOk() } }

        // EDICION: denied for worker_read, allowed for worker_edit
        mockMvc.put("/api/v1/test-auth/edicion") {
            header("Authorization", "Bearer $readOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.put("/api/v1/test-auth/edicion") {
            header("Authorization", "Bearer $editOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect { status { isOk() } }

        // EXPORTACION: denied for worker_read, allowed for worker_export
        mockMvc.get("/api/v1/test-auth/exportacion") {
            header("Authorization", "Bearer $readOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.get("/api/v1/test-auth/exportacion") {
            header("Authorization", "Bearer $exportOnlyToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect { status { isOk() } }

        assertNull(RanchContextInterceptor.currentRanchId())
        assertNull(RanchContextInterceptor.currentVerifiedContext())
    }

    @Test
    fun `interceptor blocks spoofed or malformed X-Active-Ranch-Id even on endpoints without RanchAccessAuthorizer`() {
        val ownerToken = issueToken("owner_a", Role.OWNER, ranchId = ranchA)
        given(ranchMembershipPort.resolveAccess("owner_a", ranchA)).willReturn(RanchAccess(Role.OWNER))
        given(ranchMembershipPort.resolveAccess("owner_a", ranchB)).willReturn(null)

        // Plain endpoint without X-Active-Ranch-Id succeeds
        mockMvc.get("/api/v1/test-auth/plain") {
            header("Authorization", "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ok") }
        }

        // Plain endpoint with authorized X-Active-Ranch-Id succeeds
        mockMvc.get("/api/v1/test-auth/plain") {
            header("Authorization", "Bearer $ownerToken")
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect { status { isOk() } }

        // Plain endpoint with spoofed X-Active-Ranch-Id is blocked by interceptor with 403
        mockMvc.get("/api/v1/test-auth/plain") {
            header("Authorization", "Bearer $ownerToken")
            header("X-Active-Ranch-Id", ranchB.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        // Plain endpoint with malformed X-Active-Ranch-Id is blocked by interceptor with 400
        mockMvc.get("/api/v1/test-auth/plain") {
            header("Authorization", "Bearer $ownerToken")
            header("X-Active-Ranch-Id", "not-a-uuid")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_RANCH_CONTEXT") }
        }

        // Permission-annotated endpoint without X-Active-Ranch-Id returns 400 MISSING_RANCH_CONTEXT
        mockMvc.get("/api/v1/test-auth/exportacion") {
            header("Authorization", "Bearer $ownerToken")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_RANCH_CONTEXT") }
        }

        // Unauthenticated requests still get 401 from Spring Security
        mockMvc.get("/api/v1/test-auth/exportacion") {
            header("X-Active-Ranch-Id", ranchA.toString())
        }.andExpect {
            status { isUnauthorized() }
        }

        assertNull(RanchContextInterceptor.currentRanchId())
        assertNull(RanchContextInterceptor.currentVerifiedContext())
    }

    private fun issueToken(
        username: String,
        role: Role,
        ranchId: UUID? = null,
        associationId: UUID? = null,
        permissions: Map<String, Any?> = emptyMap(),
    ): String = jwtTokenPort.issueAccessToken(
        AuthUser(
            id = UUID.randomUUID(),
            username = username,
            passwordHash = "hash",
            role = role,
            ranchId = ranchId,
            associationId = associationId,
            permissions = permissions,
        ),
    )

    private fun emptyWeightHistory() = AnimalWeightHistoryResult(
        animalId = animalId,
        folioInterno = "OV-01",
        latestWeightKg = null,
        overallDailyGainKg = null,
        overallDailyGainGrams = null,
        weights = emptyList(),
    )
}
