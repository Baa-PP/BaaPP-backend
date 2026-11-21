package com.agrotech.oviapp.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "tipos_tratamiento")
class TipoTratamientoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "smallint", nullable = false, updatable = false)
    val id: Short? = null,

    @Column(name = "nombre", length = 30, nullable = false, unique = true)
    var nombre: String,

    @Column(name = "es_preventivo", nullable = false)
    var esPreventivo: Boolean = false,
)

@Entity
@Table(name = "tipos_alimento")
class TipoAlimentoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "smallint", nullable = false, updatable = false)
    val id: Short? = null,

    @Column(name = "nombre", length = 30, nullable = false, unique = true)
    var nombre: String,
)

@Entity
@Table(name = "etapas_productivas")
class EtapaProductivaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "smallint", nullable = false, updatable = false)
    val id: Short? = null,

    @Column(name = "nombre", length = 40, nullable = false, unique = true)
    var nombre: String,

    @Column(name = "proteina_min_pct", precision = 4, scale = 1, nullable = false)
    var proteinaMinPct: BigDecimal,

    @Column(name = "proteina_max_pct", precision = 4, scale = 1, nullable = false)
    var proteinaMaxPct: BigDecimal,
)

@Entity
@Table(name = "resultados_competencia")
class ResultadoCompetenciaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "smallint", nullable = false, updatable = false)
    val id: Short? = null,

    @Column(name = "nombre", length = 40, nullable = false, unique = true)
    var nombre: String,

    @Column(name = "puntos", columnDefinition = "smallint", nullable = false)
    var puntos: Short,
)
