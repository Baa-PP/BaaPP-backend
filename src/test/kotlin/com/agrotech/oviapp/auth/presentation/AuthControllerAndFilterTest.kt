package com.agrotech.oviapp.auth.presentation

import com.agrotech.oviapp.auth.application.AuthResult
import com.agrotech.oviapp.auth.application.LoginCommand
import com.agrotech.oviapp.auth.application.LoginUseCase
import com.agrotech.oviapp.auth.application.RefreshResult
import com.agrotech.oviapp.auth.application.RefreshTokenUseCase
import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.auth.domain.RanchSummary
import com.agrotech.oviapp.auth.infrastructure.HmacJwtTokenAdapter
import com.agrotech.oviapp.domain.exception.InvalidCredentialsException
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.HealthController
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.SecurityConfig
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.agrotech.oviapp.weights.application.AnimalWeightHistoryResult
import com.agrotech.oviapp.weights.application.ConfirmWeaningUseCase
import com.agrotech.oviapp.weights.application.GetAnimalWeightsUseCase
import com.agrotech.oviapp.weights.application.RegisterWeightUseCase
import com.agrotech.oviapp.weights.presentation.WeightsController
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
import java.time.Clock
import java.util.UUID

@WebMvcTest(AuthController::class, WeightsController::class, HealthController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    RanchAccessAuthorizer::class,
    GlobalExceptionHandler::class,
    AuthControllerAndFilterTest.JwtTestConfig::class,
)
class AuthControllerAndFilterTest {

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
    private lateinit var loginUseCase: LoginUseCase

    @MockitoBean
    private lateinit var refreshTokenUseCase: RefreshTokenUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var registerWeightUseCase: RegisterWeightUseCase

    @MockitoBean
    private lateinit var getAnimalWeightsUseCase: GetAnimalWeightsUseCase

    @MockitoBean
    private lateinit var confirmWeaningUseCase: ConfirmWeaningUseCase

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `POST auth login returns tokens, role, permissions, and accessible ranches`() {
        given(loginUseCase.execute(LoginCommand("owner1", "pass123"))).willReturn(
            AuthResult(
                accessToken = "jwt-access",
                refreshToken = "opaque-refresh",
                expiresInSeconds = 900L,
                username = "owner1",
                role = Role.OWNER,
                ranchId = ranchId,
                associationId = null,
                permissions = mapOf("captura" to true),
                ranches = listOf(RanchSummary(ranchId, "Rancho El Dorado", "Don Juan", 75)),
            ),
        )

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"owner1","password":"pass123"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("jwt-access") }
            jsonPath("$.refreshToken") { value("opaque-refresh") }
            jsonPath("$.role") { value("OWNER") }
            jsonPath("$.ranchId") { value(ranchId.toString()) }
            jsonPath("$.permissions.captura") { value(true) }
            jsonPath("$.ranches[0].name") { value("Rancho El Dorado") }
        }
    }

    @Test
    fun `POST auth login returns 401 on invalid credentials`() {
        given(loginUseCase.execute(LoginCommand("owner1", "bad")))
            .willThrow(InvalidCredentialsException("INVALID_CREDENTIALS", "Usuario o contraseña inválidos."))

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"owner1","password":"bad"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `POST auth refresh rotates tokens and returns 401 for revoked or expired token`() {
        given(refreshTokenUseCase.execute("valid-refresh")).willReturn(
            RefreshResult(accessToken = "new-jwt", refreshToken = "new-refresh", expiresInSeconds = 900L),
        )
        given(refreshTokenUseCase.execute("revoked-refresh"))
            .willThrow(InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado."))

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"valid-refresh"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("new-jwt") }
            jsonPath("$.refreshToken") { value("new-refresh") }
        }

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"revoked-refresh"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `JWT filter blocks invalid bearer token with 401 and authenticates valid JWT`() {
        val user = AuthUser(
            id = UUID.randomUUID(),
            username = "owner1",
            passwordHash = "hash",
            role = Role.OWNER,
            ranchId = ranchId,
            associationId = null,
            permissions = emptyMap(),
        )
        val validJwt = jwtTokenPort.issueAccessToken(user)
        given(ranchMembershipPort.resolveAccess("owner1", ranchId)).willReturn(RanchAccess(Role.OWNER))
        given(getAnimalWeightsUseCase.execute(ranchId, animalId)).willReturn(
            AnimalWeightHistoryResult(
                animalId = animalId,
                folioInterno = "OV-1",
                latestWeightKg = null,
                overallDailyGainKg = null,
                overallDailyGainGrams = null,
                weights = emptyList(),
            ),
        )

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer invalid.jwt.token")
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isUnauthorized() }
        }

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            header("Authorization", "Bearer $validJwt")
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.folioInterno") { value("OV-1") }
        }
    }
}
