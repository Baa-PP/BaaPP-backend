package com.agrotech.oviapp.auth.infrastructure

import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.auth.domain.AuthUserPort
import com.agrotech.oviapp.auth.domain.RanchCatalogPort
import com.agrotech.oviapp.auth.domain.RanchSummary
import com.agrotech.oviapp.auth.domain.RefreshTokenPort
import com.agrotech.oviapp.auth.domain.StoredRefreshToken
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.infrastructure.persistence.UsuarioJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.entity.RanchoEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.RefreshTokenEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.UsuarioEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

interface RanchoJpaRepository : JpaRepository<RanchoEntity, UUID> {
    @Query(
        value = """
            SELECT r.*
            FROM ranchos r
            JOIN asociaciones_ranchos ar ON ar.rancho_id = r.id
            WHERE ar.asociacion_id = :asociacionId
            ORDER BY r.nombre ASC
        """,
        nativeQuery = true,
    )
    fun findAllByAssociationId(@Param("asociacionId") associationId: UUID): List<RanchoEntity>
}

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshTokenEntity rt WHERE rt.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): RefreshTokenEntity?

    @Modifying
    @Query(
        "UPDATE RefreshTokenEntity rt SET rt.revocadoEn = :revokedAt WHERE rt.usuarioId = :userId AND rt.revocadoEn IS NULL",
    )
    fun revokeAllActiveByUsuarioId(
        @Param("userId") userId: UUID,
        @Param("revokedAt") revokedAt: OffsetDateTime,
    ): Int
}

@Component
class AuthUserJpaAdapter(
    private val usuarioRepository: UsuarioJpaRepository,
) : AuthUserPort {

    override fun findByUsername(username: String): AuthUser? =
        usuarioRepository.findByUsuario(username)?.toDomain()

    override fun findById(userId: UUID): AuthUser? =
        usuarioRepository.findById(userId).orElse(null)?.toDomain()

    private fun UsuarioEntity.toDomain() = AuthUser(
        id = id,
        username = usuario,
        passwordHash = contrasenaHash,
        role = Role.valueOf(rol),
        ranchId = ranchoId,
        associationId = asociacionId,
        permissions = permisos,
        active = activo,
    )
}

@Component
class RanchCatalogJpaAdapter(
    private val ranchoRepository: RanchoJpaRepository,
) : RanchCatalogPort {

    override fun findById(ranchId: UUID): RanchSummary? =
        ranchoRepository.findById(ranchId).orElse(null)?.toSummary()

    override fun findByAssociationId(associationId: UUID): List<RanchSummary> =
        ranchoRepository.findAllByAssociationId(associationId).map { it.toSummary() }

    private fun RanchoEntity.toSummary() = RanchSummary(
        id = id,
        name = nombre,
        owner = propietario,
        weaningTargetDays = diasMetaDestete,
    )
}

@Component
class RefreshTokenJpaAdapter(
    private val repository: RefreshTokenJpaRepository,
) : RefreshTokenPort {

    override fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun save(token: StoredRefreshToken) {
        repository.save(
            RefreshTokenEntity(
                id = token.id,
                usuarioId = token.userId,
                tokenHash = token.tokenHash,
                expiraEn = OffsetDateTime.ofInstant(token.expiresAt, ZoneOffset.UTC),
                revocadoEn = token.revokedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
            ),
        )
    }

    override fun findByTokenHashForUpdate(tokenHash: String): StoredRefreshToken? =
        repository.findByTokenHashForUpdate(tokenHash)?.let { entity ->
            StoredRefreshToken(
                id = entity.getId(),
                userId = entity.usuarioId,
                tokenHash = entity.tokenHash,
                expiresAt = entity.expiraEn.toInstant(),
                revokedAt = entity.revocadoEn?.toInstant(),
            )
        }

    override fun revoke(tokenId: UUID, revokedAt: Instant) {
        val entity = repository.findById(tokenId).orElse(null) ?: return
        entity.revocadoEn = OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC)
        repository.save(entity)
    }

    override fun revokeAllForUser(userId: UUID, revokedAt: Instant) {
        repository.revokeAllActiveByUsuarioId(userId, OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC))
    }
}
