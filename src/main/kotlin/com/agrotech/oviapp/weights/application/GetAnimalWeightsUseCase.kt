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

        val sortedProjections = weightPort.findHistoryByAnimalAndRanch(animal.id, ranchId)
            .sortedWith(compareBy({ it.date }, { it.weightId }))

        // Recompute predecessor and gain fields in the final (date, weightId) order so
        // same-day measurements always reference the immediately preceding displayed row.
        val entries = sortedProjections.mapIndexed { index, row ->
            val previous = if (index > 0) sortedProjections[index - 1] else null
            val previousWeightKg = previous?.weightKg
            val daysSincePrevious = previous?.let { ChronoUnit.DAYS.between(it.date, row.date).toInt() }
            val gainKg = previousWeightKg?.let { row.weightKg.subtract(it) }
            val daysLong = daysSincePrevious?.toLong()

            val dailyKg = if (previousWeightKg != null && daysLong != null) {
                ZootechnicalCalculator.dailyWeightGainKg(row.weightKg, previousWeightKg, daysLong)
            } else {
                null
            }
            val dailyGrams = if (previousWeightKg != null && daysLong != null) {
                ZootechnicalCalculator.dailyWeightGainGrams(row.weightKg, previousWeightKg, daysLong)
            } else {
                null
            }

            WeightHistoryEntry(
                id = row.weightId,
                milestone = row.milestone,
                date = row.date,
                ageDays = row.ageDays,
                weightKg = row.weightKg,
                previousWeightKg = previousWeightKg,
                daysSincePrevious = daysSincePrevious,
                gainKg = gainKg,
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
