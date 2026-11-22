package com.agrotech.oviapp.notifications.application

import com.agrotech.oviapp.notifications.domain.DeviceTokenPort
import com.agrotech.oviapp.notifications.domain.DueWeaningLamb
import com.agrotech.oviapp.notifications.domain.PushDeliveryException
import com.agrotech.oviapp.notifications.domain.PushNotificationPort
import com.agrotech.oviapp.notifications.domain.WeaningAlertPort
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID

// Sin @Transactional a propósito: el envío push es I/O externa y cada rancho debe confirmar su "enviado" por separado.
@Service
@ConditionalOnProperty("app.notifications.weaning-alerts.enabled", havingValue = "true")
class SendWeaningAlertsUseCase(
    private val weaningAlertPort: WeaningAlertPort,
    private val deviceTokenPort: DeviceTokenPort,
    private val pushNotificationPort: PushNotificationPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute() {
        weaningAlertPort.findDueLambs()
            .groupBy { it.ranchId }
            .forEach { (ranchId, lambs) -> notifyRanch(ranchId, lambs) }
    }

    private fun notifyRanch(ranchId: UUID, lambs: List<DueWeaningLamb>) {
        val tokens = deviceTokenPort.findTokensByRanch(ranchId)
        if (tokens.isEmpty()) {
            log.debug("Rancho {} sin dispositivos registrados; {} corderos siguen pendientes de alerta.", ranchId, lambs.size)
            return
        }
        try {
            pushNotificationPort.send(tokens, TITLE, bodyFor(lambs))
        } catch (ex: PushDeliveryException) {
            log.error("No se pudo enviar la alerta de destete del rancho {}; se reintentará en la siguiente ejecución.", ranchId, ex)
            return
        }
        weaningAlertPort.markSent(lambs.map { it.animalId })
        log.info("Alerta de destete enviada al rancho {} por {} corderos.", ranchId, lambs.size)
    }

    private fun bodyFor(lambs: List<DueWeaningLamb>): String {
        val folios = lambs.map { it.folioInterno }
        val shown = folios.take(MAX_FOLIOS_IN_BODY).joinToString(", ")
        val hidden = folios.size - MAX_FOLIOS_IN_BODY
        val suffix = if (hidden > 0) " y $hidden más" else ""
        return "${folios.size} corderos alcanzaron la meta de destete: $shown$suffix."
    }

    private companion object {
        const val TITLE = "Corderos listos para destete"
        const val MAX_FOLIOS_IN_BODY = 5
    }
}
