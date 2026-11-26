package com.agrotech.oviapp.analytics.presentation

import com.agrotech.oviapp.analytics.application.GetAnalyticsOverviewUseCase
import com.agrotech.oviapp.analytics.domain.AnalyticsOverview
import com.agrotech.oviapp.analytics.domain.CullSellSuggestion
import com.agrotech.oviapp.analytics.domain.GrowthRankEntry
import com.agrotech.oviapp.analytics.domain.HerdSizePoint
import com.agrotech.oviapp.analytics.domain.HerdSummary
import com.agrotech.oviapp.analytics.domain.PendingWeaning
import com.agrotech.oviapp.analytics.domain.SeasonIndicator
import com.agrotech.oviapp.analytics.domain.StageWeightPoint
import com.agrotech.oviapp.analytics.domain.UnproductiveFemale
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RequireRanchPermission
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class HerdSummaryResponse(
    val ranchId: UUID,
    val activeAnimals: Long,
    val females: Long,
    val pregnant: Long,
    val lambs: Long,
    val males: Long,
    val studRams: Long,
    val animalsWithoutDiet: Long,
)

data class HerdSizePointResponse(val month: LocalDate, val animals: Long, val births: Long, val deaths: Long)

data class SeasonIndicatorResponse(
    val season: LocalDate,
    val exposedFemales: Long,
    val diagnosed: Long,
    val pregnant: Long,
    val pregnancyPct: BigDecimal?,
    val births: Long,
    val birthPct: BigDecimal?,
    val offspringBorn: Long,
    val prolificity: BigDecimal?,
    val offspringDead: Long,
    val mortalityPct: BigDecimal?,
    val mortalityAlert: Boolean,
)

data class GrowthRankEntryResponse(
    val animalId: UUID,
    val folioInterno: String,
    val ageDays: Int,
    val dailyGainG: BigDecimal,
    val estimatedWeightKg: BigDecimal?,
    val percentile: BigDecimal,
    val slowGrowth: Boolean,
    val suggestedDestination: String,
)

data class StageWeightPointResponse(val sex: Sex, val milestone: String, val averageKg: BigDecimal, val animals: Long)

data class PendingWeaningResponse(
    val animalId: UUID,
    val folioInterno: String,
    val ageDays: Int,
    val weaningTargetDays: Int,
    val daysToWeaning: Int,
    val overdue: Boolean,
)

data class UnproductiveFemaleResponse(
    val animalId: UUID,
    val folioInterno: String,
    val ageDays: Int,
    val pregnancyPct: BigDecimal?,
    val births: Int,
    val mortalityPct: BigDecimal?,
    val unproductive: Boolean,
    val suggestion: CullSellSuggestion,
)

data class AnalyticsOverviewResponse(
    val herd: HerdSummaryResponse,
    val herdSizeSeries: List<HerdSizePointResponse>,
    val seasons: List<SeasonIndicatorResponse>,
    val growthRanking: List<GrowthRankEntryResponse>,
    val weightByStage: List<StageWeightPointResponse>,
    val pendingWeanings: List<PendingWeaningResponse>,
    val overdueWeanings: Int,
    val unproductiveFemales: List<UnproductiveFemaleResponse>,
)

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(
    private val ranchAccessAuthorizer: RanchAccessAuthorizer,
    private val getAnalyticsOverviewUseCase: GetAnalyticsOverviewUseCase,
) {

    @GetMapping("/overview")
    @RequireRanchPermission(RanchPermission.LECTURA)
    fun overview(authentication: Authentication?): AnalyticsOverviewResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return getAnalyticsOverviewUseCase.execute(ranchId).toResponse()
    }

    private fun AnalyticsOverview.toResponse() = AnalyticsOverviewResponse(
        herd = herd.toResponse(),
        herdSizeSeries = herdSizeSeries.map { it.toResponse() },
        seasons = seasons.map { it.toResponse() },
        growthRanking = growthRanking.map { it.toResponse() },
        weightByStage = weightByStage.map { it.toResponse() },
        pendingWeanings = pendingWeanings.map { it.toResponse() },
        overdueWeanings = overdueWeanings,
        unproductiveFemales = unproductiveFemales.map { it.toResponse() },
    )

    private fun HerdSummary.toResponse() = HerdSummaryResponse(
        ranchId, activeAnimals, females, pregnant, lambs, males, studRams, animalsWithoutDiet,
    )

    private fun HerdSizePoint.toResponse() = HerdSizePointResponse(month, animals, births, deaths)

    private fun SeasonIndicator.toResponse() = SeasonIndicatorResponse(
        season, exposedFemales, diagnosed, pregnant, pregnancyPct, births, birthPct,
        offspringBorn, prolificity, offspringDead, mortalityPct, mortalityAlert,
    )

    private fun GrowthRankEntry.toResponse() = GrowthRankEntryResponse(
        animalId, folioInterno, ageDays, dailyGainG, estimatedWeightKg, percentile, slowGrowth, suggestedDestination,
    )

    private fun StageWeightPoint.toResponse() = StageWeightPointResponse(sex, milestone, averageKg, animals)

    private fun PendingWeaning.toResponse() = PendingWeaningResponse(
        animalId, folioInterno, ageDays, weaningTargetDays, daysToWeaning, overdue,
    )

    private fun UnproductiveFemale.toResponse() = UnproductiveFemaleResponse(
        animalId, folioInterno, ageDays, pregnancyPct, births, mortalityPct, unproductive, suggestion,
    )
}
