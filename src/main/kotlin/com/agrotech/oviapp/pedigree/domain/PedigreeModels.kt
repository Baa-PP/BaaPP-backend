package com.agrotech.oviapp.pedigree.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.Sex
import java.time.LocalDate
import java.util.UUID

enum class Lineage { MATERNAL, PATERNAL }

/** Ejemplar registrado en el rancho, con sus progenitores externos (fuera del sistema) si los hay. */
data class PedigreeAnimal(
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

/**
 * Ancestro registrado tal como lo devuelve la consulta recursiva.
 * [path] describe el camino desde el ejemplar: M = madre, P = padre (ej. "MP" = padre de la madre).
 */
data class AncestorRow(
    val animal: PedigreeAnimal,
    val generation: Int,
    val path: String,
)

/** Nodo del árbol: registrado en el rancho ([registered]) o externo, del que solo se conoce el folio. */
data class PedigreeAncestor(
    val generation: Int,
    val path: String,
    val relationship: String,
    val lineage: Lineage,
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

data class PedigreeOffspring(
    val id: UUID,
    val folioInterno: String,
    val registroUno: String?,
    val sex: Sex,
    val birthDate: LocalDate,
    val status: AnimalStatus,
    val deathDate: LocalDate?,
)

data class Pedigree(
    val subject: PedigreeAnimal,
    val generations: Int,
    val ancestors: List<PedigreeAncestor>,
    val offspring: List<PedigreeOffspring>,
)

object PedigreePolicy {
    const val DEFAULT_GENERATIONS = 3
    const val MAX_GENERATIONS = 4

    fun validateGenerations(generations: Int) {
        if (generations !in 1..MAX_GENERATIONS) {
            throw DomainValidationException(
                code = "INVALID_PEDIGREE_GENERATIONS",
                message = "El árbol genealógico admite de 1 a $MAX_GENERATIONS generaciones.",
            )
        }
    }
}

object PedigreeAssembler {
    private const val DAM = 'M'
    private const val SIRE = 'P'

    fun assemble(
        subject: PedigreeAnimal,
        ancestorRows: List<AncestorRow>,
        offspring: List<PedigreeOffspring>,
        generations: Int,
    ): Pedigree {
        val registered = ancestorRows.filter { it.generation <= generations }
        val registeredPaths = registered.mapTo(mutableSetOf()) { it.path }

        val externals = (listOf(AncestorRow(subject, generation = 0, path = "")) + registered)
            .filter { it.generation < generations }
            .flatMap { child ->
                listOf(SIRE to child.animal.externalSireFolio, DAM to child.animal.externalDamFolio)
                    .mapNotNull { (side, folio) ->
                        val path = child.path + side
                        folio?.takeIf { path !in registeredPaths }?.let { externalAncestor(child.generation + 1, path, it) }
                    }
            }

        val ancestors = (registered.map { registeredAncestor(it) } + externals)
            .sortedWith(compareBy({ it.generation }, { it.path }))

        return Pedigree(subject = subject, generations = generations, ancestors = ancestors, offspring = offspring)
    }

    private fun registeredAncestor(row: AncestorRow) = PedigreeAncestor(
        generation = row.generation,
        path = row.path,
        relationship = relationship(row.generation, row.animal.sex),
        lineage = lineage(row.path),
        registered = true,
        animalId = row.animal.id,
        folioInterno = row.animal.folioInterno,
        registroUno = row.animal.registroUno,
        sex = row.animal.sex,
        breed = row.animal.breed,
        color = row.animal.color,
        breeder = row.animal.breeder,
        birthDate = row.animal.birthDate,
        status = row.animal.status,
    )

    private fun externalAncestor(generation: Int, path: String, folio: String): PedigreeAncestor {
        val sex = if (path.last() == SIRE) Sex.MALE else Sex.FEMALE
        return PedigreeAncestor(
            generation = generation,
            path = path,
            relationship = relationship(generation, sex),
            lineage = lineage(path),
            registered = false,
            animalId = null,
            folioInterno = folio,
            registroUno = null,
            sex = sex,
            breed = null,
            color = null,
            breeder = null,
            birthDate = null,
            status = null,
        )
    }

    private fun lineage(path: String): Lineage = if (path.first() == DAM) Lineage.MATERNAL else Lineage.PATERNAL

    private fun relationship(generation: Int, sex: Sex): String {
        val female = sex == Sex.FEMALE
        return when (generation) {
            1 -> if (female) "Madre" else "Padre"
            2 -> if (female) "Abuela" else "Abuelo"
            3 -> if (female) "Bisabuela" else "Bisabuelo"
            else -> if (female) "Tatarabuela" else "Tatarabuelo"
        }
    }
}

interface PedigreeReadPort {
    fun findSubject(animalId: UUID, ranchId: UUID): PedigreeAnimal?

    /** Ancestros registrados en el mismo rancho, hasta [maxGenerations]; no sigue progenitores de otros ranchos. */
    fun findAncestors(animalId: UUID, ranchId: UUID, maxGenerations: Int): List<AncestorRow>

    /** Crías directas (como padre o madre), incluidas las fallecidas o vendidas. */
    fun findOffspring(animalId: UUID, ranchId: UUID): List<PedigreeOffspring>
}
