package com.agrotech.oviapp.weights.application

import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.service.ZootechnicalCalculator
import com.agrotech.oviapp.weights.domain.AnimalWeightingPort
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.domain.WeightRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class WeightHistoryEntry(
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

data class AnimalWeightHistoryResult(
    val animalId: UUID,
    val folioInterno: String,
    val latestWeightKg: BigDecimal?,
    val overallDailyGainKg: BigDecimal?,
    val overallDailyGainGrams: Long?,
    val weights: List<WeightHistoryEntry>,
)

@Service
class GetAnimalWeightsUseCase(
    private val animalPort: AnimalWeightingPort,
    private val weightPort: WeightRepositoryPort,
) {
    @Transactional(readOnly = true)
    fun execute(ranchId: UUID, animalId: UUID): AnimalWeightHistoryResult {
        val animal = animalPort.findByIdAndRanchId(animalId, ranchId)
            ?: throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo.")

        val projections = weightPort.findHistoryByAnimalAndRanch(animal.id, ranchId)
            .sortedWith(compareBy({ it.date }, { it.weightId }))

        val entries = projections.map { row ->
            val days = row.daysSincePrevious?.toLong()
            val prev = row.previousWeightKg
            val dailyKg = if (prev != null && days != null) {
                ZootechnicalCalculator.dailyWeightGainKg(row.weightKg, prev, days)
            } else {
                null
            }
            val dailyGrams = row.dailyGainGrams ?: if (prev != null && days != null) {
                ZootechnicalCalculator.dailyWeightGainGrams(row.weightKg, prev, days)
            } else {
                null
            }

            WeightHistoryEntry(
                id = row.weightId,
                milestone = row.milestone,
                date = row.date,
                ageDays = row.ageDays,
                weightKg = row.weightKg,
                previousWeightKg = prev,
                daysSincePrevious = row.daysSincePrevious,
                gainKg = row.gainKg,
                dailyGainKg = dailyKg,
                dailyGainGrams = dailyGrams,
            )
        }

        val first = entries.firstOrNull()
        val last = entries.lastOrNull()
        val totalDays = if (first != null && last != null) ChronoUnit.DAYS.between(first.date, last.date) else 0L

        val overallGdpKg = if (first != null && last != null && totalDays > 0L) {
            ZootechnicalCalculator.dailyWeightGainKg(last.weightKg, first.weightKg, totalDays)
        } else {
            null
        }
        val overallGdpGrams = if (first != null && last != null && totalDays > 0L) {
            ZootechnicalCalculator.dailyWeightGainGrams(last.weightKg, first.weightKg, totalDays)
        } else {
            null
        }

        return AnimalWeightHistoryResult(
            animalId = animal.id,
            folioInterno = animal.folioInterno,
            latestWeightKg = last?.weightKg,
            overallDailyGainKg = overallGdpKg,
            overallDailyGainGrams = overallGdpGrams,
            weights = entries,
        )
    }
}
