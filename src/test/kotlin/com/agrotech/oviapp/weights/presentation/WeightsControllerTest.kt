package com.agrotech.oviapp.weights.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.agrotech.oviapp.weights.application.AnimalWeightHistoryResult
import com.agrotech.oviapp.weights.application.ConfirmWeaningCommand
import com.agrotech.oviapp.weights.application.ConfirmWeaningUseCase
import com.agrotech.oviapp.weights.application.GetAnimalWeightsUseCase
import com.agrotech.oviapp.weights.application.RegisterWeightCommand
import com.agrotech.oviapp.weights.application.RegisterWeightUseCase
import com.agrotech.oviapp.weights.application.WeaningResult
import com.agrotech.oviapp.weights.application.WeightHistoryEntry
import com.agrotech.oviapp.weights.domain.WeaningDestination
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(WeightsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class, RanchAccessAuthorizer::class)
class WeightsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var registerWeightUseCase: RegisterWeightUseCase

    @MockitoBean
    private lateinit var getAnimalWeightsUseCase: GetAnimalWeightsUseCase

    @MockitoBean
    private lateinit var confirmWeaningUseCase: ConfirmWeaningUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val ownerPrincipal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")

    @BeforeEach
    fun setUpMembership() {
        given(ranchMembershipPort.hasAccess("producer_1", ranchId)).willReturn(true)
        given(ranchMembershipPort.hasAccess("producer_1", otherRanchId)).willReturn(false)
    }

    @Test
    fun `POST weights rejects missing X-Active-Ranch-Id header`() {
        mockMvc.post("/api/v1/weights") {
            principal = ownerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "animalId": "$animalId",
                  "milestone": "WEANING",
                  "weightKg": 20.5,
                  "date": "2026-10-15"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_RANCH_CONTEXT") }
        }
    }

    @Test
    fun `rejects caller who does not belong to active ranch with 403 before calling use cases`() {
        mockMvc.get("/api/v1/animals/$animalId/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.post("/api/v1/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "animalId": "$animalId",
                  "milestone": "WEANING",
                  "weightKg": 20.5,
                  "date": "2026-10-15"
                }
            """.trimIndent()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.post("/api/v1/animals/$animalId/wean") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "weaningDate": "2026-09-14",
                  "weaningWeightKg": 20.50,
                  "destination": "LAMB_FATTENING"
                }
            """.trimIndent()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        verifyNoInteractions(registerWeightUseCase, getAnimalWeightsUseCase, confirmWeaningUseCase)
    }

    @Test
    fun `POST weights rejects more than two decimal places via Bean Validation`() {
        mockMvc.post("/api/v1/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "animalId": "$animalId",
                  "milestone": "DAY_120",
                  "weightKg": 12.345,
                  "date": "2026-11-01"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }

        verifyNoInteractions(registerWeightUseCase)
    }

    @Test
    fun `preserves standard 415 and 405 HTTP responses instead of converting to 500`() {
        mockMvc.post("/api/v1/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.TEXT_PLAIN
            content = "invalid"
        }.andExpect {
            status { isUnsupportedMediaType() }
        }

        mockMvc.delete("/api/v1/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isMethodNotAllowed() }
        }
    }

    @Test
    fun `POST weights maps domain validation error to 400`() {
        val command = RegisterWeightCommand(
            ranchId = ranchId,
            animalId = animalId,
            milestone = WeightMilestone.DAY_120,
            weightKg = BigDecimal("26.00"),
            scrotalCircumferenceCm = BigDecimal("22.00"),
            date = LocalDate.of(2026, 11, 1),
        )
        given(registerWeightUseCase.execute(command))
            .willThrow(DomainValidationException("SCROTAL_FEMALE_NOT_ALLOWED", "Circunferencia escrotal solo aplica a machos."))

        mockMvc.post("/api/v1/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "animalId": "$animalId",
                  "milestone": "DAY_120",
                  "weightKg": 26.00,
                  "scrotalCircumferenceCm": 22.00,
                  "date": "2026-11-01"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("SCROTAL_FEMALE_NOT_ALLOWED") }
        }
    }

    @Test
    fun `POST weights creates measurement and returns 201`() {
        val weightId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val command = RegisterWeightCommand(
            ranchId = ranchId,
            animalId = animalId,
            milestone = WeightMilestone.DAY_120,
            weightKg = BigDecimal("29.40"),
            scrotalCircumferenceCm = BigDecimal("24.50"),
            date = LocalDate.of(2026, 11, 1),
        )
        given(registerWeightUseCase.execute(command)).willReturn(
            WeightMeasurement(
                id = weightId,
                animalId = animalId,
                ranchId = ranchId,
                milestone = WeightMilestone.DAY_120,
                weightKg = BigDecimal("29.40"),
                scrotalCircumferenceCm = BigDecimal("24.50"),
                date = LocalDate.of(2026, 11, 1),
            ),
        )

        mockMvc.post("/api/v1/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "animalId": "$animalId",
                  "milestone": "DAY_120",
                  "weightKg": 29.40,
                  "scrotalCircumferenceCm": 24.50,
                  "date": "2026-11-01"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(weightId.toString()) }
            jsonPath("$.milestone") { value("DAY_120") }
            jsonPath("$.weightKg") { value(29.40) }
        }
    }

    @Test
    fun `GET animal weights returns ordered weights and GDP`() {
        val weightId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        given(getAnimalWeightsUseCase.execute(ranchId, animalId)).willReturn(
            AnimalWeightHistoryResult(
                animalId = animalId,
                folioInterno = "OV-101",
                latestWeightKg = BigDecimal("22.75"),
                overallDailyGainKg = BigDecimal("0.2500"),
                overallDailyGainGrams = 250L,
                weights = listOf(
                    WeightHistoryEntry(
                        id = weightId,
                        milestone = WeightMilestone.WEANING,
                        date = LocalDate.of(2026, 9, 14),
                        ageDays = 75,
                        weightKg = BigDecimal("22.75"),
                        previousWeightKg = BigDecimal("4.00"),
                        daysSincePrevious = 75,
                        gainKg = BigDecimal("18.75"),
                        dailyGainKg = BigDecimal("0.2500"),
                        dailyGainGrams = 250L,
                    ),
                ),
            ),
        )

        mockMvc.get("/api/v1/animals/$animalId/weights") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.folioInterno") { value("OV-101") }
            jsonPath("$.overallDailyGainGrams") { value(250) }
            jsonPath("$.weights[0].milestone") { value("WEANING") }
            jsonPath("$.weights[0].dailyGainGrams") { value(250) }
        }
    }

    @Test
    fun `POST wean confirms weaning and returns updated lamb and mother status`() {
        val weightId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val command = ConfirmWeaningCommand(
            ranchId = ranchId,
            animalId = animalId,
            weaningDate = LocalDate.of(2026, 9, 14),
            weaningWeightKg = BigDecimal("20.50"),
            destination = WeaningDestination.LAMB_FATTENING,
        )
        given(confirmWeaningUseCase.execute(command)).willReturn(
            WeaningResult(
                animalId = animalId,
                weaningDate = LocalDate.of(2026, 9, 14),
                destination = WeaningDestination.LAMB_FATTENING,
                lifeStage = LifeStage.LAMB_FATTENING,
                animalStatus = AnimalStatus.ACTIVE,
                weaningWeight = WeightMeasurement(
                    id = weightId,
                    animalId = animalId,
                    ranchId = ranchId,
                    milestone = WeightMilestone.WEANING,
                    weightKg = BigDecimal("20.50"),
                    scrotalCircumferenceCm = null,
                    date = LocalDate.of(2026, 9, 14),
                ),
                motherTransitionedToEmpty = true,
            ),
        )

        mockMvc.post("/api/v1/animals/$animalId/wean") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "weaningDate": "2026-09-14",
                  "weaningWeightKg": 20.50,
                  "destination": "LAMB_FATTENING"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.destination") { value("LAMB_FATTENING") }
            jsonPath("$.motherTransitionedToEmpty") { value(true) }
            jsonPath("$.weaningWeight.milestone") { value("WEANING") }
        }
    }
}
