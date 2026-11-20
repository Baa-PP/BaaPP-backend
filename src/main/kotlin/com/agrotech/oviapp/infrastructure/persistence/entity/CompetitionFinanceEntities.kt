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
@Table(name = "eventos_competencia")
class EventoCompetenciaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "nombre", length = 160, nullable = false)
    var nombre: String,

    @Column(name = "lugar", length = 160, nullable = false)
    var lugar: String,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDate,

    @Column(name = "juez", length = 120)
    var juez: String? = null,
)

@Entity
@Table(name = "participaciones")
class ParticipacionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "animal_id", nullable = false)
    var animalId: UUID,

    @Column(name = "evento_competencia_id", nullable = false)
    var eventoCompetenciaId: UUID,

    @Column(name = "resultado_competencia_id", columnDefinition = "smallint", nullable = false)
    var resultadoCompetenciaId: Short,

    @Column(name = "categoria", length = 60, nullable = false)
    var categoria: String,

    @Column(name = "foto_s3_key", columnDefinition = "text")
    var fotoS3Key: String? = null,

    @Column(name = "nota", length = 200)
    var nota: String? = null,
)

@Entity
@Table(name = "movimientos")
class MovimientoEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "animal_id")
    var animalId: UUID? = null,

    @Column(name = "tipo", length = 10, nullable = false)
    var tipo: String,

    @Column(name = "concepto", length = 160, nullable = false)
    var concepto: String,

    @Column(name = "monto", precision = 12, scale = 2, nullable = false)
    var monto: BigDecimal,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDate,

    @Column(name = "registrado_por_usuario_id")
    var registradoPorUsuarioId: UUID? = null,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)
