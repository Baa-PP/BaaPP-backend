package com.agrotech.oviapp.notifications.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.notifications.application.RegisterDeviceTokenCommand
import com.agrotech.oviapp.notifications.application.RegisterDeviceTokenUseCase
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import org.junit.jupiter.api.Test
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

@WebMvcTest(NotificationsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class, RanchAccessAuthorizer::class)
class NotificationsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var registerDeviceTokenUseCase: RegisterDeviceTokenUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    private val principal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")

    @Test
    fun `device token is registered for the authenticated user without needing an active ranch`() {
        mockMvc.post("/api/v1/notifications/device-token") {
            this.principal = this@NotificationsControllerTest.principal
            contentType = MediaType.APPLICATION_JSON
            content = """{"deviceId":"phone-1","token":"fcm-token-1"}"""
        }.andExpect {
            status { isNoContent() }
        }

        verify(registerDeviceTokenUseCase).execute(
            RegisterDeviceTokenCommand(username = "producer_1", deviceId = "phone-1", token = "fcm-token-1"),
        )
    }

    @Test
    fun `blank token or device id is rejected with 400 before invoking the use case`() {
        mockMvc.post("/api/v1/notifications/device-token") {
            this.principal = this@NotificationsControllerTest.principal
            contentType = MediaType.APPLICATION_JSON
            content = """{"deviceId":"phone-1","token":" "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }

        mockMvc.post("/api/v1/notifications/device-token") {
            this.principal = this@NotificationsControllerTest.principal
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"fcm-token-1"}"""
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(registerDeviceTokenUseCase)
    }
}
