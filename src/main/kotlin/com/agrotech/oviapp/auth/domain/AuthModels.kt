package com.agrotech.oviapp.auth.domain

import com.agrotech.oviapp.domain.model.Role
import java.time.Instant
import java.util.UUID

data class AuthUser(
    val id: UUID,
    val username: String,
    val passwordHash: String,
    val role: Role,
    val ranchId: UUID?,
    val associationId: UUID?,
    val permissions: Map<String, Any?>,
)

data class RanchSummary(
    val id: UUID,
    val name: String,
    val owner: String,
    val weaningTargetDays: Int,
)

data class JwtClaims(
    val userId: UUID,
    val username: String,
    val role: Role,
    val ranchId: UUID?,
    val associationId: UUID?,
    val expiresAt: Instant,
)

data class StoredRefreshToken(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
) {
    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)
    fun isRevoked(): Boolean = revokedAt != null
}

interface AuthUserPort {
    fun findByUsername(username: String): AuthUser?
    fun findById(userId: UUID): AuthUser?
}

interface PasswordHasherPort {
    fun matches(rawPassword: String, encodedHash: String): Boolean
}

interface RanchCatalogPort {
    fun findById(ranchId: UUID): RanchSummary?
    fun findByAssociationId(associationId: UUID): List<RanchSummary>
}

interface JwtTokenPort {
    val accessTtlSeconds: Long
    fun issueAccessToken(user: AuthUser): String
    fun verifyAccessToken(token: String): JwtClaims?
}

interface RefreshTokenPort {
    fun hashToken(rawToken: String): String
    fun save(token: StoredRefreshToken)
    fun findByTokenHashForUpdate(tokenHash: String): StoredRefreshToken?
    fun revoke(tokenId: UUID, revokedAt: Instant)
    fun revokeAllForUser(userId: UUID, revokedAt: Instant)
}
