package com.agrotech.oviapp.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "animales")
class AnimalEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "folio_interno", length = 60, nullable = false)
    var folioInterno: String,

    @Column(name = "chip_siniiga", length = 20)
    var chipSiniiga: String? = null,

    @Column(name = "registro_uno", length = 60)
    var registroUno: String? = null,

    @Column(name = "adn", length = 60)
    var adn: String? = null,

    @Column(name = "raza", length = 80, nullable = false)
    var raza: String,

    @Column(name = "color", length = 60, nullable = false)
    var color: String,

    @Column(name = "sexo", length = 10, nullable = false)
    var sexo: String,

    @Column(name = "fecha_nacimiento", nullable = false)
    var fechaNacimiento: LocalDate,

    @Column(name = "fecha_destete")
    var fechaDestete: LocalDate? = null,

    @Column(name = "tipo_parto", length = 20, nullable = false)
    var tipoParto: String = "SIMPLE",

    @Column(name = "metodo_concepcion", length = 10, nullable = false)
    var metodoConcepcion: String = "MN",

    @Column(name = "padre_id")
    var padreId: UUID? = null,

    @Column(name = "madre_id")
    var madreId: UUID? = null,

    @Column(name = "folio_padre_externo", length = 60)
    var folioPadreExterno: String? = null,

    @Column(name = "folio_madre_externo", length = 60)
    var folioMadreExterno: String? = null,

    @Column(name = "etapa_vida", length = 30, nullable = false)
    var etapaVida: String,

    @Column(name = "estado_reproductivo", length = 30, nullable = false)
    var estadoReproductivo: String,

    @Column(name = "estado", length = 20, nullable = false)
    var estado: String = "ACTIVE",

    @Column(name = "fecha_muerte")
    var fechaMuerte: LocalDate? = null,

    @Column(name = "causa_muerte", columnDefinition = "text")
    var causaMuerte: String? = null,

    @Column(name = "etapa_al_morir", length = 30)
    var etapaAlMorir: String? = null,

    @Column(name = "motivo_alta", length = 20, nullable = false)
    var motivoAlta: String = "BIRTH",

    @Column(name = "criador", length = 150)
    var criador: String? = null,

    @Column(name = "foto_s3_key", columnDefinition = "text")
    var fotoS3Key: String? = null,

    @Column(name = "parto_id")
    var partoId: UUID? = null,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "partos")
class PartoEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "madre_id", nullable = false)
    var madreId: UUID,

    @Column(name = "padre_id")
    var padreId: UUID? = null,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDate,

    @Column(name = "nacidos_vivos", nullable = false)
    var nacidosVivos: Int,

    @Column(name = "nacidos_muertos", nullable = false)
    var nacidosMuertos: Int = 0,

    @Column(name = "foto_evidencia_s3_key", columnDefinition = "text", nullable = false)
    var fotoEvidenciaS3Key: String,

    @Column(name = "registrado_por_usuario_id", nullable = false)
    var registradoPorUsuarioId: UUID,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "auditorias_parto")
class AuditoriaPartoEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "parto_id", nullable = false)
    val partoId: UUID,

    @Column(name = "rancho_id", nullable = false)
    val ranchoId: UUID,

    @Column(name = "aprobado_por_usuario_id", nullable = false)
    val aprobadoPorUsuarioId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estado_anterior", columnDefinition = "jsonb", nullable = false)
    val estadoAnterior: Map<String, Any?>,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estado_nuevo", columnDefinition = "jsonb", nullable = false)
    val estadoNuevo: Map<String, Any?>,

    @Column(name = "foto_evidencia_s3_key", columnDefinition = "text", nullable = false)
    val fotoEvidenciaS3Key: String,

    @Column(name = "motivo", columnDefinition = "text", nullable = false)
    val motivo: String,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "pesajes")
class PesajeEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "animal_id", nullable = false)
    var animalId: UUID,

    @Column(name = "rancho_id", nullable = false)
    var ranchoId: UUID,

    @Column(name = "hito", length = 30, nullable = false)
    var hito: String,

    @Column(name = "peso_kg", precision = 6, scale = 2, nullable = false)
    var pesoKg: BigDecimal,

    @Column(name = "circunferencia_escrotal_cm", precision = 5, scale = 2)
    var circunferenciaEscrotalCm: BigDecimal? = null,

    @Column(name = "fecha", nullable = false)
    var fecha: LocalDate,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "mutaciones_procesadas")
class MutacionProcesadaEntity(
    @Id
    @Column(name = "mutacion_id", nullable = false, updatable = false)
    val mutacionId: UUID,

    @Column(name = "rancho_id", nullable = false)
    val ranchoId: UUID,

    @Column(name = "procesado_en", nullable = false, updatable = false)
    val procesadoEn: OffsetDateTime = OffsetDateTime.now(),
)
