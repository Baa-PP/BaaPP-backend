package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.animals.presentation.AnimalsController
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.media.presentation.MediaController
import com.agrotech.oviapp.weights.presentation.WeightsController
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping

class RanchAuthorizationArchitectureTest {

    private val productionClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.agrotech.oviapp")

    @Test
    fun `authorization domain types stay pure Kotlin inside domain packages`() {
        classes()
            .that().haveFullyQualifiedName(RanchPermission::class.java.name)
            .or().haveFullyQualifiedName(RanchAccess::class.java.name)
            .should().resideInAPackage("..domain.model..")
            .check(productionClasses)

        classes()
            .that().haveFullyQualifiedName(RanchMembershipPort::class.java.name)
            .should().resideInAPackage("..domain.port..")
            .check(productionClasses)

        noClasses()
            .that().haveFullyQualifiedName(RanchPermission::class.java.name)
            .or().haveFullyQualifiedName(RanchAccess::class.java.name)
            .or().haveFullyQualifiedName(RanchMembershipPort::class.java.name)
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..application..",
                "..infrastructure..",
                "..presentation..",
                "org.springframework..",
                "jakarta.persistence..",
                "com.fasterxml.jackson..",
            )
            .check(productionClasses)
    }

    @Test
    fun `presentation security layer depends on domain ports and never imports infrastructure`() {
        classes()
            .that().haveSimpleName("RanchContextInterceptor")
            .or().haveSimpleName("RanchAccessAuthorizer")
            .or().haveSimpleName("RequireRanchPermission")
            .should().resideInAPackage("..presentation.security..")
            .check(productionClasses)

        noClasses()
            .that().resideInAPackage("..presentation.security..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "jakarta.persistence..")
            .check(productionClasses)
    }

    @Test
    fun `RanchMembershipJpaAdapter resides in infrastructure persistence and never imports presentation`() {
        classes()
            .that().haveSimpleName("RanchMembershipJpaAdapter")
            .should().resideInAPackage("..infrastructure.persistence..")
            .andShould().implement(RanchMembershipPort::class.java)
            .check(productionClasses)

        noClasses()
            .that().haveSimpleName("RanchMembershipJpaAdapter")
            .should().dependOnClassesThat()
            .resideInAPackage("..presentation..")
            .check(productionClasses)
    }

    @Test
    fun `ranch-scoped endpoints in AnimalsController, WeightsController, and MediaController declare RequireRanchPermission`() {
        val targetControllers = setOf(
            AnimalsController::class.java.name,
            WeightsController::class.java.name,
            MediaController::class.java.name,
        )
        val httpMappingAnnotations = listOf(
            GetMapping::class.java,
            PostMapping::class.java,
            PutMapping::class.java,
            PatchMapping::class.java,
            DeleteMapping::class.java,
        )
        val ranchScopedHandlerMethod = object : DescribedPredicate<JavaMethod>("ranch-scoped controller handler method") {
            override fun test(method: JavaMethod): Boolean =
                method.owner.name in targetControllers &&
                    httpMappingAnnotations.any { annotation -> method.isAnnotatedWith(annotation) }
        }

        methods()
            .that(ranchScopedHandlerMethod)
            .should().beAnnotatedWith(RequireRanchPermission::class.java)
            .check(productionClasses)
    }
}
