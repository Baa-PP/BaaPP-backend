package com.agrotech.oviapp.reports.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.SecurityConfig
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.agrotech.oviapp.reports.application.GetSabanaUseCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(SabanaReportsController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    RanchAccessAuthorizer::class,
)
class SabanaReportsSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    @MockitoBean
    private lateinit var getSabanaUseCase: GetSabanaUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @Test
    fun `unauthenticated sabana report requests are blocked with 401`() {
        mockMvc.get("/api/v1/reports/sabana")
            .andExpect { status { isUnauthorized() } }

        mockMvc.get("/api/v1/reports/sabana") {
            param("format", "xlsx")
        }.andExpect { status { isUnauthorized() } }
    }
}
