package com.agrotech.oviapp.pedigree.infrastructure

import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.infrastructure.persistence.entity.AnimalEntity
import com.agrotech.oviapp.pedigree.domain.AncestorRow
import com.agrotech.oviapp.pedigree.domain.PedigreeAnimal
import com.agrotech.oviapp.pedigree.domain.PedigreeOffspring
import com.agrotech.oviapp.pedigree.domain.PedigreeReadPort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

interface PedigreeAnimalRow {
    fun getId(): UUID
    fun getFolioInterno(): String
    fun getRegistroUno(): String?
    fun getSexo(): String
    fun getRaza(): String
    fun getColor(): String
    fun getCriador(): String?
    fun getFechaNacimiento(): LocalDate
    fun getEstado(): String
    fun getFolioPadreExterno(): String?
    fun getFolioMadreExterno(): String?
}

interface PedigreeAncestorRow : PedigreeAnimalRow {
    fun getGeneracion(): Int
    fun getRuta(): String
}

interface PedigreeOffspringRow {
    fun getId(): UUID
    fun getFolioInterno(): String
    fun getRegistroUno(): String?
    fun getSexo(): String
    fun getFechaNacimiento(): LocalDate
    fun getEstado(): String
    fun getFechaMuerte(): LocalDate?
}

private const val ANIMAL_COLUMNS = """
    an.id AS id, an.folio_interno AS folioInterno, an.registro_uno AS registroUno, an.sexo AS sexo,
    an.raza AS raza, an.color AS color, an.criador AS criador, an.fecha_nacimiento AS fechaNacimiento,
    an.estado AS estado, an.folio_padre_externo AS folioPadreExterno, an.folio_madre_externo AS folioMadreExterno
"""

interface PedigreeJpaRepository : JpaRepository<AnimalEntity, UUID> {

    @Query(
        value = "SELECT " + ANIMAL_COLUMNS + " FROM animales an WHERE an.id = :animalId AND an.rancho_id = :ranchId",
        nativeQuery = true,
    )
    fun findSubject(@Param("animalId") animalId: UUID, @Param("ranchId") ranchId: UUID): PedigreeAnimalRow?

    /**
     * Misma lógica que la vista v_genealogia (ruta M/P por generación), pero arranca en el ejemplar pedido
     * en lugar de calcular el árbol de todo el rebaño, y solo sigue progenitores del rancho activo.
     */
    @Query(
        value = """
            WITH RECURSIVE arbol (ancestro_id, generacion, ruta) AS (
                SELECT x.pid, 1, x.lado::TEXT
                FROM animales a
                CROSS JOIN LATERAL (VALUES (a.madre_id, 'M'), (a.padre_id, 'P')) AS x (pid, lado)
                WHERE a.id = :animalId AND a.rancho_id = :ranchId AND x.pid IS NOT NULL
                UNION ALL
                SELECT x.pid, ar.generacion + 1, ar.ruta || x.lado
                FROM arbol ar
                JOIN animales p ON p.id = ar.ancestro_id AND p.rancho_id = :ranchId
                CROSS JOIN LATERAL (VALUES (p.madre_id, 'M'), (p.padre_id, 'P')) AS x (pid, lado)
                WHERE x.pid IS NOT NULL AND ar.generacion < :maxGenerations
            )
            SELECT """ + ANIMAL_COLUMNS + """, ar.generacion AS generacion, ar.ruta AS ruta
            FROM arbol ar
            JOIN animales an ON an.id = ar.ancestro_id AND an.rancho_id = :ranchId
            ORDER BY ar.generacion, ar.ruta
        """,
        nativeQuery = true,
    )
    fun findAncestors(
        @Param("animalId") animalId: UUID,
        @Param("ranchId") ranchId: UUID,
        @Param("maxGenerations") maxGenerations: Int,
    ): List<PedigreeAncestorRow>

    @Query(
        value = """
            SELECT c.id AS id, c.folio_interno AS folioInterno, c.registro_uno AS registroUno, c.sexo AS sexo,
                   c.fecha_nacimiento AS fechaNacimiento, c.estado AS estado, c.fecha_muerte AS fechaMuerte
            FROM animales c
            WHERE c.rancho_id = :ranchId AND (c.padre_id = :animalId OR c.madre_id = :animalId)
            ORDER BY c.fecha_nacimiento DESC, c.folio_interno
        """,
        nativeQuery = true,
    )
    fun findOffspring(@Param("animalId") animalId: UUID, @Param("ranchId") ranchId: UUID): List<PedigreeOffspringRow>
}

@Component
class PedigreeJpaAdapter(
    private val repository: PedigreeJpaRepository,
) : PedigreeReadPort {

    override fun findSubject(animalId: UUID, ranchId: UUID): PedigreeAnimal? =
        repository.findSubject(animalId, ranchId)?.toDomain()

    override fun findAncestors(animalId: UUID, ranchId: UUID, maxGenerations: Int): List<AncestorRow> =
        repository.findAncestors(animalId, ranchId, maxGenerations).map {
            AncestorRow(animal = it.toDomain(), generation = it.getGeneracion(), path = it.getRuta())
        }

    override fun findOffspring(animalId: UUID, ranchId: UUID): List<PedigreeOffspring> =
        repository.findOffspring(animalId, ranchId).map {
            PedigreeOffspring(
                id = it.getId(),
                folioInterno = it.getFolioInterno(),
                registroUno = it.getRegistroUno(),
                sex = Sex.valueOf(it.getSexo()),
                birthDate = it.getFechaNacimiento(),
                status = AnimalStatus.valueOf(it.getEstado()),
                deathDate = it.getFechaMuerte(),
            )
        }

    private fun PedigreeAnimalRow.toDomain() = PedigreeAnimal(
        id = getId(),
        folioInterno = getFolioInterno(),
        registroUno = getRegistroUno(),
        sex = Sex.valueOf(getSexo()),
        breed = getRaza(),
        color = getColor(),
        breeder = getCriador(),
        birthDate = getFechaNacimiento(),
        status = AnimalStatus.valueOf(getEstado()),
        externalSireFolio = getFolioPadreExterno(),
        externalDamFolio = getFolioMadreExterno(),
    )
}
