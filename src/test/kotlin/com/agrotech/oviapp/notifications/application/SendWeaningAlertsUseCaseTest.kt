package com.agrotech.oviapp.notifications.application

import com.agrotech.oviapp.notifications.domain.DeviceTokenPort
import com.agrotech.oviapp.notifications.domain.DueWeaningLamb
import com.agrotech.oviapp.notifications.domain.PushDeliveryException
import com.agrotech.oviapp.notifications.domain.PushNotificationPort
import com.agrotech.oviapp.notifications.domain.WeaningAlertPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SendWeaningAlertsUseCaseTest {

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("99999999-9999-9999-9999-999999999999")

    private val alerts = FakeWeaningAlertPort()
    private val tokens = FakeDeviceTokenPort()
    private val push = FakePushNotificationPort()
    private val useCase = SendWeaningAlertsUseCase(alerts, tokens, push)

    @Test
    fun `sends one grouped notification per ranch to that ranch devices and marks its lambs as sent`() {
        val lamb1 = lamb(ranchA, "CORD-1")
        val lamb2 = lamb(ranchA, "CORD-2")
        val lamb3 = lamb(ranchB, "CORD-3")
        alerts.due += listOf(lamb1, lamb2, lamb3)
        tokens.byRanch[ranchA] = listOf("tok-a1", "tok-a2")
        tokens.byRanch[ranchB] = listOf("tok-b1")

        useCase.execute()

        assertEquals(2, push.sent.size)
        val forA = push.sent.single { it.tokens == listOf("tok-a1", "tok-a2") }
        assertTrue(forA.body.contains("2"))
        assertTrue(forA.body.contains("CORD-1") && forA.body.contains("CORD-2"))
        assertEquals(listOf("tok-b1"), push.sent.single { it.tokens != forA.tokens }.tokens)
        assertEquals(setOf(lamb1.animalId, lamb2.animalId, lamb3.animalId), alerts.sent)
    }

    @Test
    fun `does not notify the same lamb twice across consecutive runs`() {
        alerts.due += lamb(ranchA, "CORD-1")
        tokens.byRanch[ranchA] = listOf("tok-a1")

        useCase.execute()
        useCase.execute()

        assertEquals(1, push.sent.size)
    }

    @Test
    fun `does not mark lambs as sent when delivery fails so the next run retries, and other ranches still get their alert`() {
        val failing = lamb(ranchA, "CORD-1")
        val healthy = lamb(ranchB, "CORD-2")
        alerts.due += listOf(failing, healthy)
        tokens.byRanch[ranchA] = listOf("tok-a1")
        tokens.byRanch[ranchB] = listOf("tok-b1")
        push.failForToken = "tok-a1"

        useCase.execute()

        assertEquals(setOf(healthy.animalId), alerts.sent)
        assertEquals(listOf("tok-b1"), push.sent.single().tokens)
    }

    @Test
    fun `ranch without registered devices is skipped and stays pending`() {
        alerts.due += lamb(ranchA, "CORD-1")

        useCase.execute()

        assertTrue(push.sent.isEmpty())
        assertTrue(alerts.sent.isEmpty())
    }

    private fun lamb(ranchId: UUID, folio: String) = DueWeaningLamb(UUID.randomUUID(), ranchId, folio)

    private class FakeWeaningAlertPort : WeaningAlertPort {
        val due = mutableListOf<DueWeaningLamb>()
        val sent = mutableSetOf<UUID>()

        override fun findDueLambs() = due.filterNot { it.animalId in sent }

        override fun markSent(animalIds: List<UUID>) {
            sent += animalIds
        }
    }

    private class FakeDeviceTokenPort : DeviceTokenPort {
        val byRanch = mutableMapOf<UUID, List<String>>()

        override fun register(username: String, deviceId: String, token: String) = Unit

        override fun findTokensByRanch(ranchId: UUID) = byRanch[ranchId].orEmpty()
    }

    private class FakePushNotificationPort : PushNotificationPort {
        data class Sent(val tokens: List<String>, val title: String, val body: String)

        val sent = mutableListOf<Sent>()
        var failForToken: String? = null

        override fun send(tokens: List<String>, title: String, body: String) {
            if (failForToken in tokens) throw PushDeliveryException("FCM unavailable")
            sent += Sent(tokens, title, body)
        }
    }
}
