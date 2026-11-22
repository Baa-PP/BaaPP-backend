package com.agrotech.oviapp.presentation

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.RestControllerAdvice

class GlobalExceptionHandlerArchitectureTest {

    private val productionClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.agrotech.oviapp")

    @Test
    fun `application has exactly one RestControllerAdvice or ControllerAdvice`() {
        val adviceClasses = productionClasses.filter { javaClass ->
            javaClass.isAnnotatedWith(RestControllerAdvice::class.java) ||
                javaClass.isAnnotatedWith(ControllerAdvice::class.java) ||
                javaClass.isMetaAnnotatedWith(ControllerAdvice::class.java)
        }

        assertEquals(
            1,
            adviceClasses.size,
            "Expected exactly one global controller advice, found: ${adviceClasses.map { it.name }}",
        )
        val singleAdvice = adviceClasses.single()
        assertEquals(GlobalExceptionHandler::class.java.name, singleAdvice.name)
        assertTrue(
            singleAdvice.isAnnotatedWith(RestControllerAdvice::class.java),
            "GlobalExceptionHandler must be annotated with @RestControllerAdvice",
        )
    }

    @Test
    fun `domain exception package never depends on Spring, JPA, Jackson, infrastructure, or presentation`() {
        noClasses()
            .that().resideInAPackage("..domain.exception..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.validation..",
                "com.fasterxml.jackson..",
                "..infrastructure..",
                "..presentation..",
            )
            .check(productionClasses)
    }
}
