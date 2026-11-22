package com.agrotech.oviapp.pedigree.presentation

import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.pedigree.application.GetPedigreeUseCase
import com.agrotech.oviapp.pedigree.domain.Lineage
import com.agrotech.oviapp.pedigree.domain.Pedigree
import com.agrotech.oviapp.pedigree.domain.PedigreeAncestor
import com.agrotech.oviapp.pedigree.domain.PedigreeAnimal
import com.agrotech.oviapp.pedigree.domain.PedigreeOffspring
import com.agrotech.oviapp.pedigree.domain.PedigreePolicy
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RequireRanchPermission
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

data class PedigreeSubjectResponse(
    val id: UUID,
    val folioInterno: String,
    val registroUno: String?,
    val sex: Sex,
    val breed: String,
    val color: String,
    val breeder: String?,
    val birthDate: LocalDate,
    val status: AnimalStatus,
    val externalSireFolio: String?,
    val externalDamFolio: String?,
)

data class PedigreeAncestorResponse(
    val generation: Int,
    /** Camino desde el ejemplar: M = madre, P = padre (ej. "MP" = abuelo materno). */
    val path: String,
    val relationship: String,
    val lineage: Lineage,
    /** false = progenitor externo: solo se conoce su folio. */
    val registered: Boolean,
    val animalId: UUID?,
    val folioInterno: String,
    val registroUno: String?,
    val sex: Sex,
    val breed: String?,
    val color: String?,
    val breeder: String?,
    val birthDate: LocalDate?,
    val status: AnimalStatus?,
)

data class PedigreeOffspringResponse(
    val id: UUID,
    val folioInterno: String,
    val registroUno: String?,
    val sex: Sex,
    val birthDate: LocalDate,
    val status: AnimalStatus,
    val deathDate: LocalDate?,
)

data class PedigreeResponse(
    val subject: PedigreeSubjectResponse,
    val generations: Int,
    val ancestors: List<PedigreeAncestorResponse>,
    val offspring: List<PedigreeOffspringResponse>,
)

@RestController
@RequestMapping("/api/v1/animals")
class PedigreeController(
    private val ranchAccessAuthorizer: RanchAccessAuthorizer,
    private val getPedigreeUseCase: GetPedigreeUseCase,
) {

    @GetMapping("/{id}/pedigree")
    @RequireRanchPermission(RanchPermission.LECTURA)
    fun getPedigree(
        authentication: Authentication?,
        @PathVariable("id") animalId: UUID,
        @RequestParam(defaultValue = "${PedigreePolicy.DEFAULT_GENERATIONS}") generations: Int,
    ): PedigreeResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication, RanchPermission.LECTURA)
        return getPedigreeUseCase.execute(ranchId, animalId, generations).toResponse()
    }

    private fun Pedigree.toResponse() = PedigreeResponse(
        subject = subject.toResponse(),
        generations = generations,
        ancestors = ancestors.map { it.toResponse() },
        offspring = offspring.map { it.toResponse() },
    )

    private fun PedigreeAnimal.toResponse() = PedigreeSubjectResponse(
        id = id,
        folioInterno = folioInterno,
        registroUno = registroUno,
        sex = sex,
        breed = breed,
        color = color,
        breeder = breeder,
        birthDate = birthDate,
        status = status,
        externalSireFolio = externalSireFolio,
        externalDamFolio = externalDamFolio,
    )

    private fun PedigreeAncestor.toResponse() = PedigreeAncestorResponse(
        generation = generation,
        path = path,
        relationship = relationship,
        lineage = lineage,
        registered = registered,
        animalId = animalId,
        folioInterno = folioInterno,
        registroUno = registroUno,
        sex = sex,
        breed = breed,
        color = color,
        breeder = breeder,
        birthDate = birthDate,
        status = status,
    )

    private fun PedigreeOffspring.toResponse() = PedigreeOffspringResponse(
        id = id,
        folioInterno = folioInterno,
        registroUno = registroUno,
        sex = sex,
        birthDate = birthDate,
        status = status,
        deathDate = deathDate,
    )
}
