package com.agrotech.oviapp.pedigree.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.Sex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.util.UUID

class PedigreeAssemblerTest {

    @Test
    fun `labels each ancestor by generation, sex and lineage`() {
        val pedigree = PedigreeAssembler.assemble(
            subject = animal("79"),
            ancestorRows = listOf(
                row(animal("10", Sex.FEMALE), generation = 1, path = "M"),
                row(animal("11", Sex.MALE), generation = 1, path = "P"),
                row(animal("20", Sex.MALE), generation = 2, path = "MP"),
                row(animal("30", Sex.FEMALE), generation = 3, path = "PMM"),
                row(animal("40", Sex.MALE), generation = 4, path = "PMMP"),
            ),
            offspring = emptyList(),
            generations = 4,
        )

        val byPath = pedigree.ancestors.associateBy { it.path }
        assertEquals("Madre", byPath.getValue("M").relationship)
        assertEquals("Padre", byPath.getValue("P").relationship)
        assertEquals("Abuelo", byPath.getValue("MP").relationship)
        assertEquals(Lineage.MATERNAL, byPath.getValue("MP").lineage)
        assertEquals("Bisabuela", byPath.getValue("PMM").relationship)
        assertEquals(Lineage.PATERNAL, byPath.getValue("PMM").lineage)
        assertEquals("Tatarabuelo", byPath.getValue("PMMP").relationship)
        assertTrue(pedigree.ancestors.all { it.registered })
    }

    @Test
    fun `adds external parents where there is no registered animal`() {
        val pedigree = PedigreeAssembler.assemble(
            subject = animal("79", externalSireFolio = "SEM-EXT-7"),
            ancestorRows = listOf(
                row(animal("10", Sex.FEMALE, externalDamFolio = "ABU-EXT-3"), generation = 1, path = "M"),
            ),
            offspring = emptyList(),
            generations = 3,
        )

        val sire = pedigree.ancestors.single { it.path == "P" }
        assertFalse(sire.registered)
        assertNull(sire.animalId)
        assertEquals("SEM-EXT-7", sire.folioInterno)
        assertEquals(Sex.MALE, sire.sex)
        assertEquals("Padre", sire.relationship)

        val grandDam = pedigree.ancestors.single { it.path == "MM" }
        assertFalse(grandDam.registered)
        assertEquals("ABU-EXT-3", grandDam.folioInterno)
        assertEquals(Sex.FEMALE, grandDam.sex)
        assertEquals("Abuela", grandDam.relationship)
        assertEquals(Lineage.MATERNAL, grandDam.lineage)
    }

    @Test
    fun `a registered parent wins over its external folio`() {
        val pedigree = PedigreeAssembler.assemble(
            subject = animal("79", externalSireFolio = "SEM-EXT-7"),
            ancestorRows = listOf(row(animal("11", Sex.MALE), generation = 1, path = "P")),
            offspring = emptyList(),
            generations = 3,
        )

        val sire = pedigree.ancestors.single { it.path == "P" }
        assertTrue(sire.registered)
        assertEquals("11", sire.folioInterno)
    }

    @Test
    fun `external ancestors beyond the requested generations are not added`() {
        val pedigree = PedigreeAssembler.assemble(
            subject = animal("79"),
            ancestorRows = listOf(row(animal("11", Sex.MALE, externalSireFolio = "ABU-EXT"), generation = 1, path = "P")),
            offspring = emptyList(),
            generations = 1,
        )

        assertEquals(listOf("P"), pedigree.ancestors.map { it.path })
    }

    @Test
    fun `ancestors come sorted by generation and then by path`() {
        val pedigree = PedigreeAssembler.assemble(
            subject = animal("79"),
            ancestorRows = listOf(
                row(animal("20", Sex.MALE), generation = 2, path = "PP"),
                row(animal("11", Sex.MALE), generation = 1, path = "P"),
                row(animal("10", Sex.FEMALE), generation = 1, path = "M"),
            ),
            offspring = emptyList(),
            generations = 3,
        )

        assertEquals(listOf("M", "P", "PP"), pedigree.ancestors.map { it.path })
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 5, -1])
    fun `only 1 to 4 generations are allowed`(generations: Int) {
        val ex = assertThrows(DomainValidationException::class.java) {
            PedigreePolicy.validateGenerations(generations)
        }
        assertEquals("INVALID_PEDIGREE_GENERATIONS", ex.code)
    }

    private fun row(animal: PedigreeAnimal, generation: Int, path: String) =
        AncestorRow(animal = animal, generation = generation, path = path)

    private fun animal(
        folio: String,
        sex: Sex = Sex.FEMALE,
        externalSireFolio: String? = null,
        externalDamFolio: String? = null,
    ) = PedigreeAnimal(
        id = UUID.randomUUID(),
        folioInterno = folio,
        registroUno = "R-$folio",
        sex = sex,
        breed = "Dorper",
        color = "Blanco",
        breeder = "Cabaña Los Altos",
        birthDate = LocalDate.of(2020, 1, 1),
        status = AnimalStatus.ACTIVE,
        externalSireFolio = externalSireFolio,
        externalDamFolio = externalDamFolio,
    )
}
