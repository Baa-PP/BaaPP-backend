package com.agrotech.oviapp.notifications.application

import com.agrotech.oviapp.notifications.domain.DeviceTokenPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class RegisterDeviceTokenUseCaseTest {

    @Test
    fun `registers the token for the authenticated user and device, replacing the previous one`() {
        val port = RecordingDeviceTokenPort()
        val useCase = RegisterDeviceTokenUseCase(port)

        useCase.execute(RegisterDeviceTokenCommand(username = "producer_1", deviceId = "phone-1", token = "tok-old"))
        useCase.execute(RegisterDeviceTokenCommand(username = "producer_1", deviceId = "phone-1", token = "tok-new"))

        assertEquals(
            listOf(Triple("producer_1", "phone-1", "tok-old"), Triple("producer_1", "phone-1", "tok-new")),
            port.registered,
        )
    }

    private class RecordingDeviceTokenPort : DeviceTokenPort {
        val registered = mutableListOf<Triple<String, String, String>>()

        override fun register(username: String, deviceId: String, token: String) {
            registered += Triple(username, deviceId, token)
        }

        override fun findTokensByRanch(ranchId: UUID): List<String> = emptyList()
    }
}
