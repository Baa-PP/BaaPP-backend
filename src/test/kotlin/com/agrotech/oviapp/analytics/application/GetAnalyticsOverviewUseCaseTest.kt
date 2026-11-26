package com.agrotech.oviapp.analytics.application

import com.agrotech.oviapp.analytics.domain.AnalyticsPolicy
import com.agrotech.oviapp.analytics.domain.AnalyticsReadPort
import com.agrotech.oviapp.analytics.domain.CullSellSuggestion
import com.agrotech.oviapp.analytics.domain.GrowthRankEntry
import com.agrotech.oviapp.analytics.domain.HerdSizePoint
import com.agrotech.oviapp.analytics.domain.HerdSummary
import com.agrotech.oviapp.analytics.domain.PendingWeaning
import com.agrotech.oviapp.analytics.domain.SeasonIndicator
import com.agrotech.oviapp.analytics.domain.StageWeightPoint
import com.agrotech.oviapp.analytics.domain.UnproductiveFemale
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class GetAnalyticsOverviewUseCaseTest {

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("99999999-9999-9999-9999-999999999999")

    @Test
    fun `overview contains only the data of the requested ranch`() {
        val port = FakeAnalyticsReadPort()
        port.herds[ranchA] = herd(ranchA, activeAnimals = 10)
        port.herds[ranchB] = herd(ranchB, activeAnimals = 99)
        port.females[ranchA] = listOf(female("OV-A", CullSellSuggestion.CULL))
        port.females[ranchB] = listOf(female("OV-B", CullSellSuggestion.SELL))
        port.weanings[ranchA] = listOf(PendingWeaning(UUID.randomUUID(), "CORD-A", 70, 60))
        port.weanings[ranchB] = listOf(PendingWeaning(UUID.randomUUID(), "CORD-B", 70, 90))

        val overview = GetAnalyticsOverviewUseCase(port).execute(ranchA)

        assertEquals(10L, overview.herd.activeAnimals)
        assertEquals(listOf("OV-A"), overview.unproductiveFemales.map { it.folioInterno })
        assertEquals(listOf("CORD-A"), overview.pendingWeanings.map { it.folioInterno })
    }

    @Test
    fun `overdue weanings are counted from each lamb ranch target`() {
        val port = FakeAnalyticsReadPort()
        port.herds[ranchA] = herd(ranchA, activeAnimals = 3)
        port.weanings[ranchA] = listOf(
            PendingWeaning(UUID.randomUUID(), "VENCIDO", ageDays = 80, weaningTargetDays = 75),
            PendingWeaning(UUID.randomUUID(), "EN-TIEMPO", ageDays = 75, weaningTargetDays = 75),
        )

        val overview = GetAnalyticsOverviewUseCase(port).execute(ranchA)

        assertEquals(2, overview.pendingWeanings.size)
        assertEquals(1, overview.overdueWeanings)
    }

    @Test
    fun `mortality alert is raised for a season above 3 percent`() {
        val port = FakeAnalyticsReadPort()
        port.herds[ranchA] = herd(ranchA, activeAnimals = 3)
        port.seasons[ranchA] = listOf(season(BigDecimal("3.1")), season(BigDecimal("2.9")))

        val overview = GetAnalyticsOverviewUseCase(port).execute(ranchA)

        assertEquals(listOf(true, false), overview.seasons.map { it.mortalityAlert })
    }

    @Test
    fun `ranked lists are requested with the policy limit`() {
        val port = FakeAnalyticsReadPort()
        port.herds[ranchA] = herd(ranchA, activeAnimals = 3)

        GetAnalyticsOverviewUseCase(port).execute(ranchA)

        assertEquals(listOf(AnalyticsPolicy.LIST_LIMIT, AnalyticsPolicy.LIST_LIMIT), port.limitsUsed)
    }

    @Test
    fun `unknown ranch is reported as not found`() {
        assertThrows(ResourceNotFoundException::class.java) {
            GetAnalyticsOverviewUseCase(FakeAnalyticsReadPort()).execute(ranchA)
        }
    }

    private fun herd(ranchId: UUID, activeAnimals: Long) = HerdSummary(
        ranchId = ranchId,
        activeAnimals = activeAnimals,
        females = 0,
        pregnant = 0,
        lambs = 0,
        males = 0,
        studRams = 0,
        animalsWithoutDiet = 0,
    )

    private fun female(folio: String, suggestion: CullSellSuggestion) = UnproductiveFemale(
        animalId = UUID.randomUUID(),
        folioInterno = folio,
        ageDays = 900,
        pregnancyPct = null,
        births = 0,
        mortalityPct = null,
        unproductive = true,
        suggestion = suggestion,
    )

    private fun season(mortalityPct: BigDecimal) = SeasonIndicator(
        season = LocalDate.of(2026, 1, 1),
        exposedFemales = 10,
        diagnosed = 10,
        pregnant = 8,
        pregnancyPct = BigDecimal("80.0"),
        births = 8,
        birthPct = BigDecimal("80.0"),
        offspringBorn = 10,
        prolificity = BigDecimal("1.25"),
        offspringDead = 1,
        mortalityPct = mortalityPct,
    )

    private class FakeAnalyticsReadPort : AnalyticsReadPort {
        val herds = mutableMapOf<UUID, HerdSummary>()
        val seasons = mutableMapOf<UUID, List<SeasonIndicator>>()
        val weanings = mutableMapOf<UUID, List<PendingWeaning>>()
        val females = mutableMapOf<UUID, List<UnproductiveFemale>>()
        val limitsUsed = mutableListOf<Int>()

        override fun findHerdSummary(ranchId: UUID) = herds[ranchId]
        override fun findHerdSizeSeries(ranchId: UUID): List<HerdSizePoint> = emptyList()
        override fun findSeasonIndicators(ranchId: UUID) = seasons[ranchId].orEmpty()
        override fun findGrowthRanking(ranchId: UUID, limit: Int): List<GrowthRankEntry> {
            limitsUsed += limit
            return emptyList()
        }
        override fun findWeightByStage(ranchId: UUID): List<StageWeightPoint> = emptyList()
        override fun findPendingWeanings(ranchId: UUID) = weanings[ranchId].orEmpty()
        override fun findCullSellFemales(ranchId: UUID, limit: Int): List<UnproductiveFemale> {
            limitsUsed += limit
            return females[ranchId].orEmpty()
        }
    }
}
