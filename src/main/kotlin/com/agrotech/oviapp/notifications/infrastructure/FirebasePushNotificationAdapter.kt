package com.agrotech.oviapp.notifications.infrastructure

import com.agrotech.oviapp.notifications.domain.PushDeliveryException
import com.agrotech.oviapp.notifications.domain.PushNotificationPort
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.FileInputStream

@Component
@ConditionalOnProperty("app.notifications.weaning-alerts.enabled", havingValue = "true")
class FirebasePushNotificationAdapter(
    @Value("\${app.firebase.credentials-path:}") credentialsPath: String,
) : PushNotificationPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(firebaseApp(credentialsPath))

    // shortcut: tokens inválidos (UNREGISTERED) no se purgan, subir a limpieza de tokens si crece el ruido en logs.
    override fun send(tokens: List<String>, title: String, body: String) {
        val notification = Notification.builder().setTitle(title).setBody(body).build()
        val failures = tokens.chunked(MAX_TOKENS_PER_MULTICAST).sumOf { batch ->
            val message = MulticastMessage.builder().addAllTokens(batch).setNotification(notification).build()
            val response = try {
                messaging.sendEachForMulticast(message)
            } catch (ex: FirebaseMessagingException) {
                throw PushDeliveryException("Firebase rechazó el envío de la notificación.", ex)
            }
            response.failureCount
        }
        if (failures == tokens.size) {
            throw PushDeliveryException("La notificación no llegó a ninguno de los ${tokens.size} dispositivos.")
        }
        if (failures > 0) {
            log.warn("La notificación falló en {} de {} dispositivos.", failures, tokens.size)
        }
    }

    private companion object {
        const val MAX_TOKENS_PER_MULTICAST = 500

        fun firebaseApp(credentialsPath: String): FirebaseApp {
            check(credentialsPath.isNotBlank()) {
                "app.firebase.credentials-path (FIREBASE_CREDENTIALS_PATH) es obligatorio si las alertas de destete están activas."
            }
            return FirebaseApp.getApps().firstOrNull() ?: FileInputStream(credentialsPath).use { credentials ->
                FirebaseApp.initializeApp(
                    FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(credentials)).build(),
                )
            }
        }
    }
}
