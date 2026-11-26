package com.agrotech.oviapp.notifications.presentation

import com.agrotech.oviapp.notifications.application.SendWeaningAlertsUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.scheduling.annotation.Scheduled

class WeaningAlertSchedulerTest {

    private val useCase = mock(SendWeaningAlertsUseCase::class.java)
    private val scheduler = WeaningAlertScheduler(useCase)

    @Test
    fun `daily run delegates to the send weaning alerts use case`() {
        scheduler.run()

        verify(useCase).execute()
    }

    @Test
    fun `an unexpected failure is contained so the schedule keeps firing on following days`() {
        willThrow(IllegalStateException("db down")).given(useCase).execute()

        scheduler.run()
    }

    @Test
    fun `runs once a day with a configurable cron and zone`() {
        val scheduled = WeaningAlertScheduler::class.java.getMethod("run").getAnnotation(Scheduled::class.java)

        assertEquals("\${app.notifications.weaning-alerts.cron:0 0 7 * * *}", scheduled.cron)
        assertEquals("\${app.notifications.weaning-alerts.zone:UTC}", scheduled.zone)
    }
}
