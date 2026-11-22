package com.agrotech.oviapp.notifications.presentation

import com.agrotech.oviapp.notifications.application.RegisterDeviceTokenCommand
import com.agrotech.oviapp.notifications.application.RegisterDeviceTokenUseCase
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class RegisterDeviceTokenRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val deviceId: String = "",

    @field:NotBlank
    @field:Size(max = 512)
    val token: String = "",
)

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationsController(
    private val registerDeviceTokenUseCase: RegisterDeviceTokenUseCase,
) {

    @PostMapping("/device-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun registerDeviceToken(
        authentication: Authentication?,
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
    ) {
        val username = RanchContextInterceptor.extractAuthenticatedPrincipal(authentication)
            ?: throw RanchContextInterceptor.ranchAccessDeniedException()
        registerDeviceTokenUseCase.execute(
            RegisterDeviceTokenCommand(username = username, deviceId = request.deviceId, token = request.token),
        )
    }
}
