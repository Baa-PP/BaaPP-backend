package com.agrotech.oviapp.ranches.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
import com.agrotech.oviapp.presentation.security.SecurityConfig
import com.agrotech.oviapp.ranches.application.CreateRanchWorkerUseCase
import com.agrotech.oviapp.ranches.application.ListAccessibleRanchesUseCase
import com.agrotech.oviapp.ranches.application.ListRanchWorkersUseCase
import com.agrotech.oviapp.ranches.application.UpdateRanchProfileUseCase
import com.agrotech.oviapp.ranches.application.UpdateRanchWorkerUseCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

@WebMvcTest(RanchesController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class, GlobalExceptionHandler::class)
class RanchesSecurityTest {

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

    @Test
    fun `unauthenticated ranch and worker endpoints return 401`() {
        mockMvc.get("/api/v1/ranches").andExpect { status { isUnauthorized() } }
        mockMvc.put("/api/v1/ranches/$ranchId") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/ranches/$ranchId/workers").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/ranches/$ranchId/workers") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
        mockMvc.patch("/api/v1/ranches/$ranchId/workers/$workerId") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }
}
