package com.agrotech.oviapp

import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.service.BirthAntiFraudPolicy
import com.agrotech.oviapp.domain.service.ZootechnicalCalculator
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ArchitectureAndDomainTest {

    @Test
    fun `domain ring never imports infrastructure or presentation`() {
        val classes = ClassFileImporter().importPackages("com.agrotech.oviapp")
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..presentation..", "org.springframework..")
            .check(classes)
    }

    @Test
    fun `Melendez Excel formulas match baseline values`() {
        assertEquals(275, ZootechnicalCalculator.cycleIntervalDays(35, 150, 60, 30))
        assertEquals(
            112,
            ZootechnicalCalculator.fatteningDays(BigDecimal("45.0"), BigDecimal("17.0"), BigDecimal("0.250"))
        )
        assertEquals(
            BigDecimal("0.8800"),
            ZootechnicalCalculator.pregnancyRate(pregnantEwes = 88, exposedEwes = 100)
        )
    }

    @Test
    fun `BirthAntiFraudPolicy blocks births older than 30 days and producer edits`() {
        val today = LocalDate.of(2026, 10, 1)
        assertThrows(IllegalArgumentException::class.java) {
            BirthAntiFraudPolicy.validateInitialRegistration(LocalDate.of(2026, 8, 30), today, "s3://photo.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BirthAntiFraudPolicy.validateLockedCorrection(Role.OWNER, "s3://photo.jpg", "Error de dedo")
        }
        BirthAntiFraudPolicy.validateLockedCorrection(Role.ASSOCIATION_SUPERUSER, "s3://photo.jpg", "Parto gemelar verificado")
    }
}
