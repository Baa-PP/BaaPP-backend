package com.agrotech.oviapp.weights.presentation

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RequireRanchPermission
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
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class RegisterWeightRequest(
    val id: UUID? = null,
    @field:NotNull
    val animalId: UUID?,
    @field:NotNull
    val milestone: WeightMilestone?,
    @field:NotNull
    @field:DecimalMin("0.01")
    @field:DecimalMax("999.99")
    @field:Digits(integer = 4, fraction = 2)
    val weightKg: BigDecimal?,
    @field:DecimalMin("0.01")
    @field:DecimalMax("99.99")
    @field:Digits(integer = 3, fraction = 2)
    val scrotalCircumferenceCm: BigDecimal? = null,
    @field:NotNull
    val date: LocalDate?,
)

data class WeightMeasurementResponse(
    val id: UUID,
    val animalId: UUID,
    val ranchId: UUID,
    val milestone: WeightMilestone,
    val weightKg: BigDecimal,
    val scrotalCircumferenceCm: BigDecimal?,
    val date: LocalDate,
)

data class WeightHistoryItemResponse(
    val id: UUID,
    val milestone: WeightMilestone,
    val date: LocalDate,
    val ageDays: Int,
    val weightKg: BigDecimal,
    val previousWeightKg: BigDecimal?,
    val daysSincePrevious: Int?,
    val gainKg: BigDecimal?,
    val dailyGainKg: BigDecimal?,
    val dailyGainGrams: Long?,
)

data class AnimalWeightHistoryResponse(
    val animalId: UUID,
    val folioInterno: String,
    val latestWeightKg: BigDecimal?,
    val overallDailyGainKg: BigDecimal?,
    val overallDailyGainGrams: Long?,
    val weights: List<WeightHistoryItemResponse>,
)

data class ConfirmWeaningRequest(
    val weightId: UUID? = null,
    @field:NotNull
    val weaningDate: LocalDate?,
    @field:NotNull
    @field:DecimalMin("0.01")
    @field:DecimalMax("999.99")
    @field:Digits(integer = 4, fraction = 2)
    val weaningWeightKg: BigDecimal?,
    @field:NotNull
    val destination: WeaningDestination?,
    @field:DecimalMin("0.01")
    @field:DecimalMax("99.99")
    @field:Digits(integer = 3, fraction = 2)
    val scrotalCircumferenceCm: BigDecimal? = null,
)

data class ConfirmWeaningResponse(
    val animalId: UUID,
    val weaningDate: LocalDate,
    val destination: WeaningDestination,
    val lifeStage: LifeStage,
    val animalStatus: AnimalStatus,
    val weaningWeight: WeightMeasurementResponse,
    val motherTransitionedToEmpty: Boolean,
)

@RestController
@RequestMapping("/api/v1")
class WeightsController(
    private val ranchAccessAuthorizer: RanchAccessAuthorizer,
    private val registerWeightUseCase: RegisterWeightUseCase,
    private val getAnimalWeightsUseCase: GetAnimalWeightsUseCase,
    private val confirmWeaningUseCase: ConfirmWeaningUseCase,
) {

    @PostMapping("/weights")
    @RequireRanchPermission(RanchPermission.CAPTURA)
    fun registerWeight(
        authentication: Authentication?,
        @Valid @RequestBody request: RegisterWeightRequest,
    ): ResponseEntity<WeightMeasurementResponse> {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        val measurement = registerWeightUseCase.execute(
            RegisterWeightCommand(
                id = request.id,
                ranchId = ranchId,
                animalId = request.animalId ?: throw invalidPayload("animalId"),
                milestone = request.milestone ?: throw invalidPayload("milestone"),
                weightKg = request.weightKg ?: throw invalidPayload("weightKg"),
                scrotalCircumferenceCm = request.scrotalCircumferenceCm,
                date = request.date ?: throw invalidPayload("date"),
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(measurement.toResponse())
    }

    @GetMapping("/animals/{id}/weights")
    @RequireRanchPermission(RanchPermission.LECTURA)
    fun getAnimalWeights(
        authentication: Authentication?,
        @PathVariable("id") animalId: UUID,
    ): AnimalWeightHistoryResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return getAnimalWeightsUseCase.execute(ranchId = ranchId, animalId = animalId).toResponse()
    }

    @PostMapping("/animals/{id}/wean")
    @RequireRanchPermission(RanchPermission.CAPTURA)
    fun confirmWeaning(
        authentication: Authentication?,
        @PathVariable("id") animalId: UUID,
        @Valid @RequestBody request: ConfirmWeaningRequest,
    ): ConfirmWeaningResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return confirmWeaningUseCase.execute(
            ConfirmWeaningCommand(
                ranchId = ranchId,
                animalId = animalId,
                weaningDate = request.weaningDate ?: throw invalidPayload("weaningDate"),
                weaningWeightKg = request.weaningWeightKg ?: throw invalidPayload("weaningWeightKg"),
                destination = request.destination ?: throw invalidPayload("destination"),
                scrotalCircumferenceCm = request.scrotalCircumferenceCm,
                weightId = request.weightId,
            ),
        ).toResponse()
    }

    private fun invalidPayload(field: String) =
        DomainValidationException("VALIDATION_ERROR", "Campo obligatorio faltante: $field.")

    private fun WeightMeasurement.toResponse() = WeightMeasurementResponse(
        id = id,
        animalId = animalId,
        ranchId = ranchId,
        milestone = milestone,
        weightKg = weightKg,
        scrotalCircumferenceCm = scrotalCircumferenceCm,
        date = date,
    )

    private fun AnimalWeightHistoryResult.toResponse() = AnimalWeightHistoryResponse(
        animalId = animalId,
        folioInterno = folioInterno,
        latestWeightKg = latestWeightKg,
        overallDailyGainKg = overallDailyGainKg,
        overallDailyGainGrams = overallDailyGainGrams,
        weights = weights.map { it.toResponse() },
    )

    private fun WeightHistoryEntry.toResponse() = WeightHistoryItemResponse(
        id = id,
        milestone = milestone,
        date = date,
        ageDays = ageDays,
        weightKg = weightKg,
        previousWeightKg = previousWeightKg,
        daysSincePrevious = daysSincePrevious,
        gainKg = gainKg,
        dailyGainKg = dailyGainKg,
        dailyGainGrams = dailyGainGrams,
    )

    private fun WeaningResult.toResponse() = ConfirmWeaningResponse(
        animalId = animalId,
        weaningDate = weaningDate,
        destination = destination,
        lifeStage = lifeStage,
        animalStatus = animalStatus,
        weaningWeight = weaningWeight.toResponse(),
        motherTransitionedToEmpty = motherTransitionedToEmpty,
    )
}
