package com.agrotech.oviapp.analytics.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class AnalyticsReadJpaAdapterTest {

    private val repository = mock(AnalyticsJpaRepository::class.java)
    private val adapter = AnalyticsReadJpaAdapter(repository)
    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `pending weanings carry the ranch weaning target from ranchos dias_meta_destete`() {
        val lambId = UUID.randomUUID()
        val row = mock(WeaningCandidateRow::class.java)
        given(row.getAnimalId()).willReturn(lambId)
        given(row.getFolioInterno()).willReturn("CORD-1")
        given(row.getEdadDias()).willReturn(70)
        given(row.getDiasMetaDestete()).willReturn(60)
        given(repository.findPendingWeanings(ranchId)).willReturn(listOf(row))

        val weanings = adapter.findPendingWeanings(ranchId)

        assertEquals(1, weanings.size)
        assertEquals(lambId, weanings.first().animalId)
        assertEquals(60, weanings.first().weaningTargetDays)
        assertEquals(true, weanings.first().overdue)
    }

    @Test
    fun `season indicators map the v_reproduccion_temporada projection`() {
        val row = mock(SeasonRow::class.java)
        given(row.getTemporada()).willReturn(LocalDate.of(2026, 4, 1))
        given(row.getExpuestas()).willReturn(20L)
        given(row.getDiagnosticadas()).willReturn(18L)
        given(row.getPrenadas()).willReturn(15L)
        given(row.getPctPrenez()).willReturn(BigDecimal("83.3"))
        given(row.getPartos()).willReturn(14L)
        given(row.getPctParto()).willReturn(BigDecimal("70.0"))
        given(row.getCriasNacidas()).willReturn(20L)
        given(row.getCriasPorParto()).willReturn(BigDecimal("1.43"))
        given(row.getCriasMuertas()).willReturn(1L)
        given(row.getPctMortalidad()).willReturn(BigDecimal("5.0"))
        given(repository.findSeasons(ranchId)).willReturn(listOf(row))

        val season = adapter.findSeasonIndicators(ranchId).single()

        assertEquals(LocalDate.of(2026, 4, 1), season.season)
        assertEquals(BigDecimal("83.3"), season.pregnancyPct)
        assertEquals(BigDecimal("1.43"), season.prolificity)
        assertEquals(true, season.mortalityAlert)
    }
}
