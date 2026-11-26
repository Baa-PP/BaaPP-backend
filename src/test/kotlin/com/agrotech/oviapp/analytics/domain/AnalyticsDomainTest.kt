package com.agrotech.oviapp.analytics.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class AnalyticsDomainTest {

    @Test
    fun `mortality alert fires only strictly above 3 percent and never without data`() {
        assertFalse(AnalyticsPolicy.isMortalityAlert(null))
        assertFalse(AnalyticsPolicy.isMortalityAlert(BigDecimal("3.0")))
        assertTrue(AnalyticsPolicy.isMortalityAlert(BigDecimal("3.1")))
    }

    @Test
    fun `weaning is overdue only after the ranch target days and reports days left`() {
        assertWeaning(ageDays = 75, targetDays = 75, expectedDaysLeft = 0, expectedOverdue = false)
        assertWeaning(ageDays = 76, targetDays = 75, expectedDaysLeft = -1, expectedOverdue = true)
        assertWeaning(ageDays = 76, targetDays = 90, expectedDaysLeft = 14, expectedOverdue = false)
    }

    private fun assertWeaning(ageDays: Int, targetDays: Int, expectedDaysLeft: Int, expectedOverdue: Boolean) {
        val weaning = PendingWeaning(UUID.randomUUID(), "OV-1", ageDays, targetDays)
        assertEquals(expectedDaysLeft, weaning.daysToWeaning)
        assertEquals(expectedOverdue, weaning.overdue)
    }
}
