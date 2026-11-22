package com.agrotech.oviapp.notifications.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.notifications.application.RegisterDeviceTokenUseCase
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.SecurityConfig
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(NotificationsController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    RanchAccessAuthorizer::class,
)
class NotificationsSecurityTest {

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    @MockitoBean
    private lateinit var registerDeviceTokenUseCase: RegisterDeviceTokenUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `unauthenticated device token registration is blocked with 401`() {
        mockMvc.post("/api/v1/notifications/device-token") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"deviceId":"phone-1","token":"fcm-token-1"}"""
        }.andExpect { status { isUnauthorized() } }
    }
}
