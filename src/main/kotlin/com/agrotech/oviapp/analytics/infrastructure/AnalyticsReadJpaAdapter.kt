package com.agrotech.oviapp.analytics.infrastructure

import com.agrotech.oviapp.analytics.domain.AnalyticsReadPort
import com.agrotech.oviapp.analytics.domain.CullSellSuggestion
import com.agrotech.oviapp.analytics.domain.GrowthRankEntry
import com.agrotech.oviapp.analytics.domain.HerdSizePoint
import com.agrotech.oviapp.analytics.domain.HerdSummary
import com.agrotech.oviapp.analytics.domain.PendingWeaning
import com.agrotech.oviapp.analytics.domain.SeasonIndicator
import com.agrotech.oviapp.analytics.domain.StageWeightPoint
import com.agrotech.oviapp.analytics.domain.UnproductiveFemale
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.infrastructure.persistence.entity.RanchoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

interface HerdSummaryRow {
    fun getRanchId(): UUID
    fun getAnimalesActivos(): Long
    fun getHembras(): Long
    fun getCargadas(): Long
    fun getCorderos(): Long
    fun getMachos(): Long
    fun getSementales(): Long
    fun getAnimalesSinDieta(): Long
}

interface HerdSizeRow {
    fun getMes(): LocalDate
    fun getAnimales(): Long
    fun getNacimientos(): Long
    fun getMuertes(): Long
}

interface SeasonRow {
    fun getTemporada(): LocalDate
    fun getExpuestas(): Long
    fun getDiagnosticadas(): Long
    fun getPrenadas(): Long
    fun getPctPrenez(): BigDecimal?
    fun getPartos(): Long
    fun getPctParto(): BigDecimal?
    fun getCriasNacidas(): Long
    fun getCriasPorParto(): BigDecimal?
    fun getCriasMuertas(): Long
    fun getPctMortalidad(): BigDecimal?
}

interface GrowthRow {
    fun getAnimalId(): UUID
    fun getFolioInterno(): String
    fun getEdadDias(): Int
    fun getGananciaDiariaG(): BigDecimal
    fun getPesoEstimadoHoyKg(): BigDecimal?
    fun getPercentilRancho(): BigDecimal
    fun getCreceLento(): Boolean
    fun getDestinoSugerido(): String
}

interface StageWeightRow {
    fun getSexo(): String
    fun getHito(): String
    fun getPromedioKg(): BigDecimal
    fun getAnimales(): Long
}

interface WeaningCandidateRow {
    fun getAnimalId(): UUID
    fun getFolioInterno(): String
    fun getEdadDias(): Int
    fun getDiasMetaDestete(): Int
}

interface FemaleDispositionRow {
    fun getAnimalId(): UUID
    fun getFolioInterno(): String
    fun getEdadDias(): Int
    fun getPctPrenez(): BigDecimal?
    fun getPartos(): Int
    fun getPctMortalidad(): BigDecimal?
    fun getImproductiva(): Boolean
    fun getDestinoSugerido(): String
}

// Todas las consultas filtran por rancho_id: el aislamiento multi-rancho se resuelve aquí, en el único punto de acceso a datos.
interface AnalyticsJpaRepository : JpaRepository<RanchoEntity, UUID> {
    @Query(
        value = """
            SELECT rancho_id            AS ranchId,
                   animales_activos     AS animalesActivos,
                   hembras              AS hembras,
                   cargadas             AS cargadas,
                   corderos             AS corderos,
                   machos               AS machos,
                   sementales           AS sementales,
                   animales_sin_dieta   AS animalesSinDieta
            FROM v_resumen_rancho
            WHERE rancho_id = :ranchId
        """,
        nativeQuery = true,
    )
    fun findHerdSummary(@Param("ranchId") ranchId: UUID): HerdSummaryRow?

    @Query(
        value = """
            SELECT mes AS mes, animales AS animales, nacimientos AS nacimientos, muertes AS muertes
            FROM v_tamano_rebano_mensual
            WHERE rancho_id = :ranchId
            ORDER BY mes
        """,
        nativeQuery = true,
    )
    fun findHerdSizeSeries(@Param("ranchId") ranchId: UUID): List<HerdSizeRow>

    @Query(
        value = """
            SELECT temporada          AS temporada,
                   expuestas          AS expuestas,
                   diagnosticadas     AS diagnosticadas,
                   prenadas           AS prenadas,
                   pct_prenez         AS pctPrenez,
                   partos             AS partos,
                   pct_parto          AS pctParto,
                   crias_nacidas      AS criasNacidas,
                   crias_por_parto    AS criasPorParto,
                   crias_muertas      AS criasMuertas,
                   pct_mortalidad     AS pctMortalidad
            FROM v_reproduccion_temporada
            WHERE rancho_id = :ranchId
            ORDER BY temporada
        """,
        nativeQuery = true,
    )
    fun findSeasons(@Param("ranchId") ranchId: UUID): List<SeasonRow>

    @Query(
        value = """
            SELECT animal_id             AS animalId,
                   folio_interno         AS folioInterno,
                   edad_dias             AS edadDias,
                   ganancia_diaria_g     AS gananciaDiariaG,
                   peso_estimado_hoy_kg  AS pesoEstimadoHoyKg,
                   percentil_rancho      AS percentilRancho,
                   crece_lento           AS creceLento,
                   destino_sugerido      AS destinoSugerido
            FROM v_ranking_crecimiento
            WHERE rancho_id = :ranchId
            ORDER BY ganancia_diaria_g DESC, folio_interno
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findGrowthRanking(@Param("ranchId") ranchId: UUID, @Param("limit") limit: Int): List<GrowthRow>

    @Query(
        value = """
            SELECT sexo AS sexo, hito AS hito, MAX(promedio_rancho_kg) AS promedioKg, COUNT(*) AS animales
            FROM v_peso_etapa
            WHERE rancho_id = :ranchId
            GROUP BY sexo, hito
            ORDER BY sexo,
                     CASE hito WHEN 'BIRTH' THEN 0 WHEN 'WEANING' THEN 1 WHEN 'DAY_120' THEN 2
                               WHEN 'DAY_150' THEN 3 ELSE 4 END
        """,
        nativeQuery = true,
    )
    fun findWeightByStage(@Param("ranchId") ranchId: UUID): List<StageWeightRow>

    @Query(
        value = """
            SELECT a.id                              AS animalId,
                   a.folio_interno                   AS folioInterno,
                   CURRENT_DATE - a.fecha_nacimiento AS edadDias,
                   r.dias_meta_destete               AS diasMetaDestete
            FROM animales a
            JOIN ranchos r ON r.id = a.rancho_id
            WHERE a.rancho_id = :ranchId
              AND a.estado = 'ACTIVE'
              AND a.etapa_vida = 'LAMB_LACTATING'
              AND a.fecha_destete IS NULL
            ORDER BY CURRENT_DATE - a.fecha_nacimiento DESC, a.folio_interno
        """,
        nativeQuery = true,
    )
    fun findPendingWeanings(@Param("ranchId") ranchId: UUID): List<WeaningCandidateRow>

    @Query(
        value = """
            SELECT animal_id         AS animalId,
                   folio_interno     AS folioInterno,
                   edad_dias         AS edadDias,
                   pct_prenez        AS pctPrenez,
                   partos            AS partos,
                   pct_mortalidad    AS pctMortalidad,
                   improductiva      AS improductiva,
                   destino_sugerido  AS destinoSugerido
            FROM v_historial_reproductivo_hembra
            WHERE rancho_id = :ranchId
              AND destino_sugerido IN ('CULL', 'SELL')
            ORDER BY CASE destino_sugerido WHEN 'CULL' THEN 0 ELSE 1 END, folio_interno
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findCullSellFemales(@Param("ranchId") ranchId: UUID, @Param("limit") limit: Int): List<FemaleDispositionRow>
}

@Component
class AnalyticsReadJpaAdapter(
    private val repository: AnalyticsJpaRepository,
) : AnalyticsReadPort {

    override fun findHerdSummary(ranchId: UUID): HerdSummary? =
        repository.findHerdSummary(ranchId)?.let {
            HerdSummary(
                ranchId = it.getRanchId(),
                activeAnimals = it.getAnimalesActivos(),
                females = it.getHembras(),
                pregnant = it.getCargadas(),
                lambs = it.getCorderos(),
                males = it.getMachos(),
                studRams = it.getSementales(),
                animalsWithoutDiet = it.getAnimalesSinDieta(),
            )
        }

    override fun findHerdSizeSeries(ranchId: UUID): List<HerdSizePoint> =
        repository.findHerdSizeSeries(ranchId).map {
            HerdSizePoint(month = it.getMes(), animals = it.getAnimales(), births = it.getNacimientos(), deaths = it.getMuertes())
        }

    override fun findSeasonIndicators(ranchId: UUID): List<SeasonIndicator> =
        repository.findSeasons(ranchId).map {
            SeasonIndicator(
                season = it.getTemporada(),
                exposedFemales = it.getExpuestas(),
                diagnosed = it.getDiagnosticadas(),
                pregnant = it.getPrenadas(),
                pregnancyPct = it.getPctPrenez(),
                births = it.getPartos(),
                birthPct = it.getPctParto(),
                offspringBorn = it.getCriasNacidas(),
                prolificity = it.getCriasPorParto(),
                offspringDead = it.getCriasMuertas(),
                mortalityPct = it.getPctMortalidad(),
            )
        }

    override fun findGrowthRanking(ranchId: UUID, limit: Int): List<GrowthRankEntry> =
        repository.findGrowthRanking(ranchId, limit).map {
            GrowthRankEntry(
                animalId = it.getAnimalId(),
                folioInterno = it.getFolioInterno(),
                ageDays = it.getEdadDias(),
                dailyGainG = it.getGananciaDiariaG(),
                estimatedWeightKg = it.getPesoEstimadoHoyKg(),
                percentile = it.getPercentilRancho(),
                slowGrowth = it.getCreceLento(),
                suggestedDestination = it.getDestinoSugerido(),
            )
        }

    override fun findWeightByStage(ranchId: UUID): List<StageWeightPoint> =
        repository.findWeightByStage(ranchId).map {
            StageWeightPoint(
                sex = Sex.valueOf(it.getSexo()),
                milestone = it.getHito(),
                averageKg = it.getPromedioKg(),
                animals = it.getAnimales(),
            )
        }

    override fun findPendingWeanings(ranchId: UUID): List<PendingWeaning> =
        repository.findPendingWeanings(ranchId).map {
            PendingWeaning(
                animalId = it.getAnimalId(),
                folioInterno = it.getFolioInterno(),
                ageDays = it.getEdadDias(),
                weaningTargetDays = it.getDiasMetaDestete(),
            )
        }

    override fun findCullSellFemales(ranchId: UUID, limit: Int): List<UnproductiveFemale> =
        repository.findCullSellFemales(ranchId, limit).map {
            UnproductiveFemale(
                animalId = it.getAnimalId(),
                folioInterno = it.getFolioInterno(),
                ageDays = it.getEdadDias(),
                pregnancyPct = it.getPctPrenez(),
                births = it.getPartos(),
                mortalityPct = it.getPctMortalidad(),
                unproductive = it.getImproductiva(),
                suggestion = CullSellSuggestion.valueOf(it.getDestinoSugerido()),
            )
        }
}
