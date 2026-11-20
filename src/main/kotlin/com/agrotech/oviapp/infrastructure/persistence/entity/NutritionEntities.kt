package com.agrotech.oviapp.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "alimentos")
class AlimentoEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id")
    var ranchoId: UUID? = null,

    @Column(name = "tipo_alimento_id", columnDefinition = "smallint", nullable = false)
    var tipoAlimentoId: Short,

    @Column(name = "marca", length = 80, nullable = false)
    var marca: String,

    @Column(name = "nombre", length = 120, nullable = false)
    var nombre: String,

    @Column(name = "proteina_pct", precision = 4, scale = 1)
    var proteinaPct: BigDecimal? = null,

    @Column(name = "energia_mcal_kg", precision = 3, scale = 2)
    var energiaMcalKg: BigDecimal? = null,

    @Column(name = "kg_presentacion", precision = 7, scale = 2)
    var kgPresentacion: BigDecimal? = null,

    @Column(name = "precio_presentacion", precision = 10, scale = 2)
    var precioPresentacion: BigDecimal? = null,

    @Column(name = "uso", length = 300)
    var uso: String? = null,

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "raciones")
class RacionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "animal_id", nullable = false)
    var animalId: UUID,

    @Column(name = "fecha_inicio", nullable = false)
    var fechaInicio: LocalDate,

    @Column(name = "nota", length = 200)
    var nota: String? = null,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "raciones_alimento")
class RacionAlimentoEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "racion_id", nullable = false)
    var racionId: UUID,

    @Column(name = "alimento_id", nullable = false)
    var alimentoId: UUID,

    @Column(name = "kg_dia", precision = 6, scale = 3, nullable = false)
    var kgDia: BigDecimal,
)

@Entity
@Table(name = "mezclas")
class MezclaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "etapa_productiva_id", columnDefinition = "smallint", nullable = false)
    var etapaProductivaId: Short,

    @Column(name = "nombre", length = 80, nullable = false)
    var nombre: String,

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "mezclas_ingrediente")
class MezclaIngredienteEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "mezcla_id", nullable = false)
    var mezclaId: UUID,

    @Column(name = "alimento_id", nullable = false)
    var alimentoId: UUID,

    @Column(name = "porcentaje", precision = 5, scale = 2, nullable = false)
    var porcentaje: BigDecimal,
)
