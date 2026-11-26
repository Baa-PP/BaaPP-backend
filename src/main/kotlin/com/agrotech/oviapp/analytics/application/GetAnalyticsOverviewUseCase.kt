package com.agrotech.oviapp.analytics.application

import com.agrotech.oviapp.analytics.domain.AnalyticsOverview
import com.agrotech.oviapp.analytics.domain.AnalyticsPolicy
import com.agrotech.oviapp.analytics.domain.AnalyticsReadPort
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetAnalyticsOverviewUseCase(
    private val analyticsReadPort: AnalyticsReadPort,
) {
    @Transactional(readOnly = true)
    fun execute(ranchId: UUID): AnalyticsOverview {
        val herd = analyticsReadPort.findHerdSummary(ranchId)
            ?: throw ResourceNotFoundException("RANCH_NOT_FOUND", "Rancho no encontrado.")
        return AnalyticsOverview(
            herd = herd,
            herdSizeSeries = analyticsReadPort.findHerdSizeSeries(ranchId),
            seasons = analyticsReadPort.findSeasonIndicators(ranchId),
            growthRanking = analyticsReadPort.findGrowthRanking(ranchId, AnalyticsPolicy.LIST_LIMIT),
            weightByStage = analyticsReadPort.findWeightByStage(ranchId),
            pendingWeanings = analyticsReadPort.findPendingWeanings(ranchId),
            unproductiveFemales = analyticsReadPort.findCullSellFemales(ranchId, AnalyticsPolicy.LIST_LIMIT),
        )
    }
}
