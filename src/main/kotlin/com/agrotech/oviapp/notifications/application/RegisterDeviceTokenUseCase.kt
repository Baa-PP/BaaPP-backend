package com.agrotech.oviapp.notifications.application

import com.agrotech.oviapp.notifications.domain.DeviceTokenPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class RegisterDeviceTokenCommand(
    val username: String,
    val deviceId: String,
    val token: String,
)

@Service
class RegisterDeviceTokenUseCase(
    private val deviceTokenPort: DeviceTokenPort,
) {
    @Transactional
    fun execute(command: RegisterDeviceTokenCommand) {
        deviceTokenPort.register(command.username, command.deviceId, command.token)
    }
}
