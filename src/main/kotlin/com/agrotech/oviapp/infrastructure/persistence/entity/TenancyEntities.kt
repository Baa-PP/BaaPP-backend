package com.agrotech.oviapp.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "asociaciones")
class AsociacionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "nombre", length = 150, nullable = false)
    var nombre: String,

    @Column(name = "clave", length = 40, unique = true)
    var clave: String? = null,

    @Column(name = "alcance", length = 20, nullable = false)
    var alcance: String = "STATE",

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "ranchos")
class RanchoEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "nombre", length = 150, nullable = false)
    var nombre: String,

    @Column(name = "direccion", columnDefinition = "text", nullable = false)
    var direccion: String,

    @Column(name = "propietario", length = 150, nullable = false)
    var propietario: String,

    @Column(name = "dias_meta_destete", nullable = false)
    var diasMetaDestete: Int = 75,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Embeddable
data class AsociacionRanchoId(
    @Column(name = "asociacion_id", nullable = false)
    val asociacionId: UUID,

    @Column(name = "rancho_id", nullable = false)
    val ranchoId: UUID,
) : Serializable

@Entity
@Table(name = "asociaciones_ranchos")
class AsociacionRanchoEntity(
    @EmbeddedId
    val id: AsociacionRanchoId,

    @Column(name = "afiliado_en", nullable = false, updatable = false)
    val afiliadoEn: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "usuarios")
class UsuarioEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "rancho_id")
    var ranchoId: UUID? = null,

    @Column(name = "asociacion_id")
    var asociacionId: UUID? = null,

    @Column(name = "usuario", length = 100, nullable = false, unique = true)
    var usuario: String,

    @Column(name = "contrasena_hash", length = 255, nullable = false)
    var contrasenaHash: String,

    @Column(name = "rol", length = 40, nullable = false)
    var rol: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permisos", columnDefinition = "jsonb", nullable = false)
    var permisos: Map<String, Any?> = emptyMap(),

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),
)
