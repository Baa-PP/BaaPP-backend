package com.agrotech.oviapp.domain.service

import java.math.BigDecimal
import java.math.RoundingMode

object ZootechnicalCalculator {

    fun pregnancyRate(pregnantEwes: Int, exposedEwes: Int): BigDecimal {
        require(exposedEwes > 0) { "exposedEwes must be > 0" }
        return BigDecimal(pregnantEwes).divide(BigDecimal(exposedEwes), 4, RoundingMode.HALF_UP)
    }

    fun parturitionRate(lambingEwes: Int, pregnantEwes: Int): BigDecimal {
        require(pregnantEwes > 0) { "pregnantEwes must be > 0" }
        return BigDecimal(lambingEwes).divide(BigDecimal(pregnantEwes), 4, RoundingMode.HALF_UP)
    }

    fun prolificacy(totalLambsBorn: Int, lambingEwes: Int): BigDecimal {
        require(lambingEwes > 0) { "lambingEwes must be > 0" }
        return BigDecimal(totalLambsBorn).divide(BigDecimal(lambingEwes), 2, RoundingMode.HALF_UP)
    }

    fun cycleIntervalDays(breedingDays: Int, gestationDays: Int, lactationDays: Int, restDays: Int): Int =
        breedingDays + gestationDays + lactationDays + restDays

    fun fatteningDays(targetFinalWeightKg: BigDecimal, weaningWeightKg: BigDecimal, dailyWeightGainKg: BigDecimal): Int {
        require(dailyWeightGainKg > BigDecimal.ZERO) { "dailyWeightGainKg must be > 0" }
        return targetFinalWeightKg.subtract(weaningWeightKg)
            .divide(dailyWeightGainKg, 0, RoundingMode.HALF_UP)
            .toInt()
    }

    fun asFedKgPerDay(dryMatterKgPerDay: BigDecimal, dietDryMatterFraction: BigDecimal): BigDecimal {
        require(dietDryMatterFraction > BigDecimal.ZERO) { "dietDryMatterFraction must be > 0" }
        return dryMatterKgPerDay.divide(dietDryMatterFraction, 4, RoundingMode.HALF_UP)
    }

    fun dailyFeedCost(dryMatterKgPerDay: BigDecimal, dietDryMatterFraction: BigDecimal, pricePerKgAsFed: BigDecimal): BigDecimal =
        asFedKgPerDay(dryMatterKgPerDay, dietDryMatterFraction)
            .multiply(pricePerKgAsFed)
            .setScale(2, RoundingMode.HALF_UP)
}
