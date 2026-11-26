package com.agrotech.oviapp.notifications.presentation

import com.agrotech.oviapp.notifications.application.SendWeaningAlertsUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.notifications.weaning-alerts.enabled", havingValue = "true")
class WeaningAlertScheduler(
    private val sendWeaningAlertsUseCase: SendWeaningAlertsUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${app.notifications.weaning-alerts.cron:0 0 7 * * *}",
        zone = "\${app.notifications.weaning-alerts.zone:UTC}",
    )
    fun run() {
        try {
            sendWeaningAlertsUseCase.execute()
        } catch (ex: Exception) {
            log.error("Falló la ejecución diaria de alertas de destete.", ex)
        }
    }
}
