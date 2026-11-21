package com.agrotech.oviapp.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
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
@DynamicUpdate
@Table(name = "usuarios")
class UsuarioEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private val id: UUID,

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

    @Column(name = "activo", nullable = false)
    var activo: Boolean = true,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),
) : Persistable<UUID> {

    @Transient
    private var isNewRecord: Boolean = true

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewRecord

    @PostPersist
    @PostLoad
    fun markNotNew() {
        isNewRecord = false
    }
}

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private val id: UUID,

    @Column(name = "usuario_id", nullable = false)
    val usuarioId: UUID,

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    val tokenHash: String,

    @Column(name = "expira_en", nullable = false)
    val expiraEn: OffsetDateTime,

    @Column(name = "revocado_en")
    var revocadoEn: OffsetDateTime? = null,

    @Column(name = "creado_en", nullable = false, updatable = false)
    val creadoEn: OffsetDateTime = OffsetDateTime.now(),
) : Persistable<UUID> {

    @Transient
    private var isNewRecord: Boolean = true

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewRecord

    @PostPersist
    @PostLoad
    fun markNotNew() {
        isNewRecord = false
    }
}
