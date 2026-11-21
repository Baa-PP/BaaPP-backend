package com.agrotech.oviapp

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.presentation.HealthController
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
import com.agrotech.oviapp.presentation.security.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options

@WebMvcTest(HealthController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@TestPropertySource(properties = ["app.cors.allowed-origins=https://main.example.amplifyapp.com,http://localhost:3000"])
class HealthAndSecurityTest {

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoints are public and unauthenticated api routes are blocked`() {
        mockMvc.get("/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ok") }
            }

        mockMvc.get("/api/v1/health")
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/animals")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `cors preflight allows configured Amplify origin`() {
        mockMvc.options("/api/v1/health") {
            header("Origin", "https://main.example.amplifyapp.com")
            header("Access-Control-Request-Method", "GET")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", "https://main.example.amplifyapp.com") }
        }
    }
}
