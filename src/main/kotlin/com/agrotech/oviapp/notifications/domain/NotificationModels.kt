package com.agrotech.oviapp.notifications.domain

import java.util.UUID

data class DueWeaningLamb(
    val animalId: UUID,
    val ranchId: UUID,
    val folioInterno: String,
)

class PushDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface DeviceTokenPort {
    /** Guarda el token del dispositivo del usuario; reemplaza el anterior del mismo dispositivo. */
    fun register(username: String, deviceId: String, token: String)

    fun findTokensByRanch(ranchId: UUID): List<String>
}

interface PushNotificationPort {
    /** @throws PushDeliveryException si la notificación no llegó a ningún dispositivo. */
    fun send(tokens: List<String>, title: String, body: String)
}

interface WeaningAlertPort {
    /** Corderos activos sin destetar que alcanzaron `dias_meta_destete` de su rancho y aún no fueron notificados. */
    fun findDueLambs(): List<DueWeaningLamb>

    fun markSent(animalIds: List<UUID>)
}
