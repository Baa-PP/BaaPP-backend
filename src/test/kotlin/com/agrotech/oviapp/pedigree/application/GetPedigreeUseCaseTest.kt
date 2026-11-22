package com.agrotech.oviapp.pedigree.application

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.pedigree.domain.AncestorRow
import com.agrotech.oviapp.pedigree.domain.PedigreeAnimal
import com.agrotech.oviapp.pedigree.domain.PedigreeOffspring
import com.agrotech.oviapp.pedigree.domain.PedigreeReadPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class GetPedigreeUseCaseTest {

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val subject = animal("79")

    @Test
    fun `returns ancestors up to the requested generations and every offspring including DECEASED`() {
        val port = FakePedigreeReadPort(
            subjects = mapOf(ranchId to subject),
            ancestors = listOf(AncestorRow(animal("10", Sex.FEMALE), generation = 1, path = "M")),
            offspring = listOf(
                offspring("101", AnimalStatus.ACTIVE),
                offspring("102", AnimalStatus.DECEASED),
            ),
        )

        val pedigree = GetPedigreeUseCase(port).execute(ranchId, subject.id, generations = 4)

        assertEquals(4, port.requestedGenerations)
        assertEquals(ranchId, port.requestedRanch)
        assertEquals("79", pedigree.subject.folioInterno)
        assertEquals(listOf("M"), pedigree.ancestors.map { it.path })
        assertEquals(listOf(AnimalStatus.ACTIVE, AnimalStatus.DECEASED), pedigree.offspring.map { it.status })
    }

    @Test
    fun `an animal from another ranch is not found`() {
        val port = FakePedigreeReadPort(subjects = mapOf(otherRanchId to subject))

        val ex = assertThrows(ResourceNotFoundException::class.java) {
            GetPedigreeUseCase(port).execute(ranchId, subject.id, generations = 3)
        }
        assertEquals("ANIMAL_NOT_FOUND", ex.code)
        assertNull(port.requestedGenerations, "No se consultan ancestros de otro rancho")
    }

    @Test
    fun `rejects more than 4 generations before reading the database`() {
        val port = FakePedigreeReadPort(subjects = mapOf(ranchId to subject))

        assertThrows(DomainValidationException::class.java) {
            GetPedigreeUseCase(port).execute(ranchId, subject.id, generations = 5)
        }
        assertNull(port.requestedGenerations)
    }

    private fun animal(folio: String, sex: Sex = Sex.FEMALE) = PedigreeAnimal(
        id = UUID.randomUUID(),
        folioInterno = folio,
        registroUno = null,
        sex = sex,
        breed = "Dorper",
        color = "Blanco",
        breeder = null,
        birthDate = LocalDate.of(2022, 5, 1),
        status = AnimalStatus.ACTIVE,
        externalSireFolio = null,
        externalDamFolio = null,
    )

    private fun offspring(folio: String, status: AnimalStatus) = PedigreeOffspring(
        id = UUID.randomUUID(),
        folioInterno = folio,
        registroUno = null,
        sex = Sex.MALE,
        birthDate = LocalDate.of(2026, 8, 1),
        status = status,
        deathDate = if (status == AnimalStatus.DECEASED) LocalDate.of(2026, 9, 1) else null,
    )

    private class FakePedigreeReadPort(
        private val subjects: Map<UUID, PedigreeAnimal> = emptyMap(),
        private val ancestors: List<AncestorRow> = emptyList(),
        private val offspring: List<PedigreeOffspring> = emptyList(),
    ) : PedigreeReadPort {
        var requestedGenerations: Int? = null
        var requestedRanch: UUID? = null

        override fun findSubject(animalId: UUID, ranchId: UUID): PedigreeAnimal? =
            subjects[ranchId]?.takeIf { it.id == animalId }

        override fun findAncestors(animalId: UUID, ranchId: UUID, maxGenerations: Int): List<AncestorRow> {
            requestedGenerations = maxGenerations
            requestedRanch = ranchId
            return ancestors
        }

        override fun findOffspring(animalId: UUID, ranchId: UUID): List<PedigreeOffspring> = offspring
    }
}
