package com.agrotech.oviapp.weights.infrastructure

import com.agrotech.oviapp.domain.model.Animal
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.infrastructure.persistence.entity.AnimalEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.PesajeEntity
import com.agrotech.oviapp.weights.domain.AnimalWeightingPort
import com.agrotech.oviapp.weights.domain.WeightGainProjection
import com.agrotech.oviapp.weights.domain.WeightMeasurement
import com.agrotech.oviapp.weights.domain.WeightMilestone
import com.agrotech.oviapp.weights.domain.WeightRepositoryPort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

interface AnimalWeightingJpaRepository : JpaRepository<AnimalEntity, UUID> {
    fun findByIdAndRanchoId(id: UUID, ranchoId: UUID): AnimalEntity?
}

interface PesajeGananciaViewRow {
    fun getPesajeId(): UUID
    fun getAnimalId(): UUID
    fun getRanchId(): UUID
    fun getFolioInterno(): String
    fun getHito(): String
    fun getFecha(): LocalDate
    fun getEdadDias(): Int
    fun getPesoKg(): BigDecimal
    fun getPesoAnteriorKg(): BigDecimal?
    fun getDiasDesdeAnterior(): Int?
    fun getGananciaKg(): BigDecimal?
    fun getGananciaDiariaG(): Long?
}

interface PesajeJpaRepository : JpaRepository<PesajeEntity, UUID> {
    @Query(
        value = """
            SELECT pesaje_id AS pesajeId,
                   animal_id AS animalId,
                   rancho_id AS ranchId,
                   folio_interno AS folioInterno,
                   hito AS hito,
                   fecha AS fecha,
                   edad_dias AS edadDias,
                   peso_kg AS pesoKg,
                   peso_anterior_kg AS pesoAnteriorKg,
                   dias_desde_anterior AS diasDesdeAnterior,
                   ganancia_kg AS gananciaKg,
                   ganancia_diaria_g AS gananciaDiariaG
            FROM v_pesaje_ganancia
            WHERE animal_id = :animalId AND rancho_id = :ranchId
            ORDER BY fecha ASC, pesaje_id ASC
        """,
        nativeQuery = true,
    )
    fun findWeightHistoryFromView(
        @Param("animalId") animalId: UUID,
        @Param("ranchId") ranchId: UUID,
    ): List<PesajeGananciaViewRow>
}

@Component
class AnimalWeightingJpaAdapter(
    private val repository: AnimalWeightingJpaRepository,
) : AnimalWeightingPort {

    override fun findByIdAndRanchId(animalId: UUID, ranchId: UUID): Animal? =
        repository.findByIdAndRanchoId(animalId, ranchId)?.toDomain()

    override fun save(animal: Animal): Animal {
        val existing = repository.findByIdAndRanchoId(animal.id, animal.ranchId)
        val entity = if (existing != null) {
            existing.apply {
                fechaDestete = animal.weaningDate
                etapaVida = animal.lifeStage.name
                estadoReproductivo = animal.reproductiveStatus.name
                estado = animal.status.name
                actualizadoEn = OffsetDateTime.now()
            }
        } else {
            animal.toEntity()
        }
        return repository.save(entity).toDomain()
    }

    private fun AnimalEntity.toDomain() = Animal(
        id = id,
        ranchId = ranchoId,
        folioInterno = folioInterno,
        siniigaChip = chipSiniiga,
        registroUno = registroUno,
        adn = adn,
        breed = raza,
        color = color,
        sex = Sex.valueOf(sexo),
        birthDate = fechaNacimiento,
        weaningDate = fechaDestete,
        parturitionType = ParturitionType.valueOf(tipoParto),
        conceptionMethod = ConceptionMethod.valueOf(metodoConcepcion),
        sireId = padreId,
        damId = madreId,
        externalSireFolio = folioPadreExterno,
        externalDamFolio = folioMadreExterno,
        lifeStage = LifeStage.valueOf(etapaVida),
        reproductiveStatus = ReproductiveStatus.valueOf(estadoReproductivo),
        status = AnimalStatus.valueOf(estado),
    )

    private fun Animal.toEntity() = AnimalEntity(
        id = id,
        ranchoId = ranchId,
        folioInterno = folioInterno,
        chipSiniiga = siniigaChip,
        registroUno = registroUno,
        adn = adn,
        raza = breed,
        color = color,
        sexo = sex.name,
        fechaNacimiento = birthDate,
        fechaDestete = weaningDate,
        tipoParto = parturitionType.name,
        metodoConcepcion = conceptionMethod.name,
        padreId = sireId,
        madreId = damId,
        folioPadreExterno = externalSireFolio,
        folioMadreExterno = externalDamFolio,
        etapaVida = lifeStage.name,
        estadoReproductivo = reproductiveStatus.name,
        estado = status.name,
    )
}

@Component
class WeightRepositoryJpaAdapter(
    private val repository: PesajeJpaRepository,
) : WeightRepositoryPort {

    override fun save(measurement: WeightMeasurement): WeightMeasurement {
        val saved = repository.save(
            PesajeEntity(
                id = measurement.id,
                animalId = measurement.animalId,
                ranchoId = measurement.ranchId,
                hito = measurement.milestone.name,
                pesoKg = measurement.weightKg,
                circunferenciaEscrotalCm = measurement.scrotalCircumferenceCm,
                fecha = measurement.date,
            ),
        )
        return WeightMeasurement(
            id = saved.id,
            animalId = saved.animalId,
            ranchId = saved.ranchoId,
            milestone = WeightMilestone.valueOf(saved.hito),
            weightKg = saved.pesoKg,
            scrotalCircumferenceCm = saved.circunferenciaEscrotalCm,
            date = saved.fecha,
        )
    }

    override fun findHistoryByAnimalAndRanch(animalId: UUID, ranchId: UUID): List<WeightGainProjection> =
        repository.findWeightHistoryFromView(animalId, ranchId).map { row ->
            WeightGainProjection(
                weightId = row.getPesajeId(),
                animalId = row.getAnimalId(),
                ranchId = row.getRanchId(),
                folioInterno = row.getFolioInterno(),
                milestone = WeightMilestone.valueOf(row.getHito()),
                date = row.getFecha(),
                ageDays = row.getEdadDias(),
                weightKg = row.getPesoKg(),
                previousWeightKg = row.getPesoAnteriorKg(),
                daysSincePrevious = row.getDiasDesdeAnterior(),
                gainKg = row.getGananciaKg(),
                dailyGainGrams = row.getGananciaDiariaG(),
            )
        }
}
