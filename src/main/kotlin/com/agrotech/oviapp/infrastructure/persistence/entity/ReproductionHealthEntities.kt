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
@Table(name = "empadres")
class EmpadreEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "hembra_id", nullable = false)
    var hembraId: UUID,

    @Column(name = "semental_id")
    var sementalId: UUID? = null,

    @Column(name = "semental_externo_folio", length = 60)
    var sementalExternoFolio: String? = null,

    @Column(name = "metodo_concepcion", length = 10, nullable = false)
    var metodoConcepcion: String = "MN",

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDate,

    @Column(name = "diagnostico", length = 10)
    var diagnostico: String? = null,

    @Column(name = "fecha_diagnostico")
    var fechaDiagnostico: LocalDate? = null,

    @Column(name = "parto_id", unique = true)
    var partoId: UUID? = null,

    @Column(name = "cerrado", nullable = false)
    var cerrado: Boolean = false,

    @Column(name = "registrado_por_usuario_id")
    var registradoPorUsuarioId: UUID? = null,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "esquemas_vacuna")
class EsquemaVacunaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "tipo_tratamiento_id", columnDefinition = "smallint", nullable = false)
    var tipoTratamientoId: Short,

    @Column(name = "nombre", length = 120, nullable = false)
    var nombre: String,

    @Column(name = "edad_inicio_dias", columnDefinition = "smallint", nullable = false)
    var edadInicioDias: Short,

    @Column(name = "cada_dias", columnDefinition = "smallint", nullable = false)
    var cadaDias: Short,

    @Column(name = "aplica_a", length = 10, nullable = false)
    var aplicaA: String = "ALL",

    @Column(name = "nota", length = 300)
    var nota: String? = null,

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "tratamientos")
class TratamientoEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "tipo_tratamiento_id", columnDefinition = "smallint", nullable = false)
    var tipoTratamientoId: Short,

    @Column(name = "esquema_vacuna_id")
    var esquemaVacunaId: UUID? = null,

    @Column(name = "producto", length = 160, nullable = false)
    var producto: String,

    @Column(name = "alcance", length = 12, nullable = false)
    var alcance: String,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDate,

    @Column(name = "proxima_fecha")
    var proximaFecha: LocalDate? = null,

    @Column(name = "notas", length = 500)
    var notas: String? = null,

    @Column(name = "registrado_por_usuario_id")
    var registradoPorUsuarioId: UUID? = null,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "tratamientos_animal")
class TratamientoAnimalEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "tratamiento_id", nullable = false)
    var tratamientoId: UUID,

    @Column(name = "animal_id", nullable = false)
    var animalId: UUID,

    @Column(name = "dosis_ml", precision = 5, scale = 2)
    var dosisMl: BigDecimal? = null,

    @Column(name = "observacion", length = 200)
    var observacion: String? = null,
)
