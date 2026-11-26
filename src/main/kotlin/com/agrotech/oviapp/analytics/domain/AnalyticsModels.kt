package com.agrotech.oviapp.analytics.domain

import com.agrotech.oviapp.domain.model.Sex
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object AnalyticsPolicy {
    val MORTALITY_ALERT_THRESHOLD_PCT: BigDecimal = BigDecimal("3")
    const val LIST_LIMIT = 50

    fun isMortalityAlert(mortalityPct: BigDecimal?): Boolean =
        mortalityPct != null && mortalityPct > MORTALITY_ALERT_THRESHOLD_PCT
}

data class HerdSummary(
    val ranchId: UUID,
    val activeAnimals: Long,
    val females: Long,
    val pregnant: Long,
    val lambs: Long,
    val males: Long,
    val studRams: Long,
    val animalsWithoutDiet: Long,
)

data class HerdSizePoint(
    val month: LocalDate,
    val animals: Long,
    val births: Long,
    val deaths: Long,
)

data class SeasonIndicator(
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
) {
    val mortalityAlert: Boolean get() = AnalyticsPolicy.isMortalityAlert(mortalityPct)
}

data class GrowthRankEntry(
    val animalId: UUID,
    val folioInterno: String,
    val ageDays: Int,
    val dailyGainG: BigDecimal,
    val estimatedWeightKg: BigDecimal?,
    val percentile: BigDecimal,
    val slowGrowth: Boolean,
    val suggestedDestination: String,
)

data class StageWeightPoint(
    val sex: Sex,
    val milestone: String,
    val averageKg: BigDecimal,
    val animals: Long,
)

data class PendingWeaning(
    val animalId: UUID,
    val folioInterno: String,
    val ageDays: Int,
    val weaningTargetDays: Int,
) {
    val daysToWeaning: Int get() = weaningTargetDays - ageDays
    val overdue: Boolean get() = ageDays > weaningTargetDays
}

enum class CullSellSuggestion { CULL, SELL }

data class UnproductiveFemale(
    val animalId: UUID,
    val folioInterno: String,
    val ageDays: Int,
    val pregnancyPct: BigDecimal?,
    val births: Int,
    val mortalityPct: BigDecimal?,
    val unproductive: Boolean,
    val suggestion: CullSellSuggestion,
)

data class AnalyticsOverview(
    val herd: HerdSummary,
    val herdSizeSeries: List<HerdSizePoint>,
    val seasons: List<SeasonIndicator>,
    val growthRanking: List<GrowthRankEntry>,
    val weightByStage: List<StageWeightPoint>,
    val pendingWeanings: List<PendingWeaning>,
    val unproductiveFemales: List<UnproductiveFemale>,
) {
    val overdueWeanings: Int get() = pendingWeanings.count { it.overdue }
}

interface AnalyticsReadPort {
    fun findHerdSummary(ranchId: UUID): HerdSummary?
    fun findHerdSizeSeries(ranchId: UUID): List<HerdSizePoint>
    fun findSeasonIndicators(ranchId: UUID): List<SeasonIndicator>
    fun findGrowthRanking(ranchId: UUID, limit: Int): List<GrowthRankEntry>
    fun findWeightByStage(ranchId: UUID): List<StageWeightPoint>
    fun findPendingWeanings(ranchId: UUID): List<PendingWeaning>
    fun findCullSellFemales(ranchId: UUID, limit: Int): List<UnproductiveFemale>
}
