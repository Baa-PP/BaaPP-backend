package com.agrotech.oviapp.analytics.presentation

import com.agrotech.oviapp.analytics.application.GetAnalyticsOverviewUseCase
import com.agrotech.oviapp.analytics.domain.AnalyticsOverview
import com.agrotech.oviapp.analytics.domain.CullSellSuggestion
import com.agrotech.oviapp.analytics.domain.HerdSummary
import com.agrotech.oviapp.analytics.domain.PendingWeaning
import com.agrotech.oviapp.analytics.domain.SeasonIndicator
import com.agrotech.oviapp.analytics.domain.UnproductiveFemale
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(AnalyticsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class, RanchAccessAuthorizer::class)
class AnalyticsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getAnalyticsOverviewUseCase: GetAnalyticsOverviewUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val ownerPrincipal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")

    @BeforeEach
    fun setUpMembership() {
        given(ranchMembershipPort.resolveAccess("producer_1", ranchId)).willReturn(RanchAccess(Role.OWNER))
        given(ranchMembershipPort.resolveAccess("producer_1", otherRanchId)).willReturn(null)
    }

    @Test
    fun `overview is computed for the X-Active-Ranch-Id and exposes alerts, weanings and cull-sell suggestions`() {
        given(getAnalyticsOverviewUseCase.execute(ranchId)).willReturn(sampleOverview())

        mockMvc.get("/api/v1/analytics/overview") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.herd.activeAnimals") { value(10) }
            jsonPath("$.seasons[0].pregnancyPct") { value(80.0) }
            jsonPath("$.seasons[0].mortalityAlert") { value(true) }
            jsonPath("$.pendingWeanings[0].overdue") { value(true) }
            jsonPath("$.overdueWeanings") { value(1) }
            jsonPath("$.unproductiveFemales[0].suggestion") { value("CULL") }
        }
        verify(getAnalyticsOverviewUseCase).execute(ranchId)
    }

    @Test
    fun `a ranch the user does not belong to is rejected before reaching the use case`() {
        mockMvc.get("/api/v1/analytics/overview") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }
        verifyNoInteractions(getAnalyticsOverviewUseCase)
    }

    @Test
    fun `WORKER_BRANCH without lectura permission receives 403 before invoking the use case`() {
        val noPermWorker = TestingAuthenticationToken("worker_none", null, "ROLE_WORKER_BRANCH")
        given(ranchMembershipPort.resolveAccess("worker_none", ranchId))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, emptyMap()))

        mockMvc.get("/api/v1/analytics/overview") {
            principal = noPermWorker
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }
        verifyNoInteractions(getAnalyticsOverviewUseCase)
    }

    private fun sampleOverview() = AnalyticsOverview(
        herd = HerdSummary(
            ranchId = ranchId,
            activeAnimals = 10,
            females = 6,
            pregnant = 2,
            lambs = 2,
            males = 2,
            studRams = 1,
            animalsWithoutDiet = 0,
        ),
        herdSizeSeries = emptyList(),
        seasons = listOf(
            SeasonIndicator(
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
                mortalityPct = BigDecimal("5.0"),
            ),
        ),
        growthRanking = emptyList(),
        weightByStage = emptyList(),
        pendingWeanings = listOf(PendingWeaning(UUID.randomUUID(), "CORD-1", ageDays = 80, weaningTargetDays = 75)),
        unproductiveFemales = listOf(
            UnproductiveFemale(
                animalId = UUID.randomUUID(),
                folioInterno = "OV-9",
                ageDays = 900,
                pregnancyPct = null,
                births = 0,
                mortalityPct = null,
                unproductive = true,
                suggestion = CullSellSuggestion.CULL,
            ),
        ),
    )
}
