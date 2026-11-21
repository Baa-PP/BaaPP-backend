package com.agrotech.oviapp.auth.application

import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.auth.domain.AuthUserPort
import com.agrotech.oviapp.auth.domain.JwtClaims
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.auth.domain.PasswordHasherPort
import com.agrotech.oviapp.auth.domain.RanchCatalogPort
import com.agrotech.oviapp.auth.domain.RanchSummary
import com.agrotech.oviapp.auth.domain.RefreshTokenPort
import com.agrotech.oviapp.auth.domain.StoredRefreshToken
import com.agrotech.oviapp.domain.exception.InvalidCredentialsException
import com.agrotech.oviapp.domain.model.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AuthUseCasesTest {

    private val ranchA = RanchSummary(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        name = "Rancho El Dorado",
        owner = "Don Juan",
        weaningTargetDays = 75,
    )
    private val ranchB = RanchSummary(
        id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        name = "Rancho La Loma",
        owner = "María Pérez",
        weaningTargetDays = 60,
    )
    private val associationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val fixedNow = Instant.parse("2026-11-20T18:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `LoginUseCase returns JWT, refresh token, permissions and own ranch for OWNER`() {
        val owner = AuthUser(
            id = UUID.randomUUID(),
            username = "juan_dorper",
            passwordHash = "hashed:secret123",
            role = Role.OWNER,
            ranchId = ranchA.id,
            associationId = null,
            permissions = mapOf("captura" to true),
        )
        val refreshStore = FakeRefreshTokenPort()
        val useCase = LoginUseCase(
            authUserPort = FakeAuthUserPort(owner),
            passwordHasher = FakePasswordHasherPort(),
            ranchCatalogPort = FakeRanchCatalogPort(mapOf(ranchA.id to ranchA, ranchB.id to ranchB), mapOf(associationId to listOf(ranchA, ranchB))),
            jwtTokenPort = FakeJwtTokenPort(),
            refreshTokenPort = refreshStore,
            clock = clock,
            refreshTtlSeconds = 3600,
        )

        val result = useCase.execute(LoginCommand("  juan_dorper ", "secret123"))

        assertEquals("jwt:${owner.username}", result.accessToken)
        assertTrue(result.refreshToken.isNotBlank())
        assertEquals(Role.OWNER, result.role)
        assertEquals(ranchA.id, result.ranchId)
        assertEquals(mapOf("captura" to true), result.permissions)
        assertEquals(listOf(ranchA), result.ranches)
        assertEquals(1, refreshStore.tokensByHash.size)
        assertEquals(fixedNow.plusSeconds(3600), refreshStore.tokensByHash.values.first().expiresAt)
    }

    @Test
    fun `LoginUseCase returns all affiliated ranches for VET_TECHNICIAN`() {
        val vet = AuthUser(
            id = UUID.randomUUID(),
            username = "vet_aglot",
            passwordHash = "hashed:vetpass",
            role = Role.VET_TECHNICIAN,
            ranchId = null,
            associationId = associationId,
            permissions = emptyMap(),
        )
        val useCase = LoginUseCase(
            authUserPort = FakeAuthUserPort(vet),
            passwordHasher = FakePasswordHasherPort(),
            ranchCatalogPort = FakeRanchCatalogPort(mapOf(ranchA.id to ranchA, ranchB.id to ranchB), mapOf(associationId to listOf(ranchA, ranchB))),
            jwtTokenPort = FakeJwtTokenPort(),
            refreshTokenPort = FakeRefreshTokenPort(),
            clock = clock,
            refreshTtlSeconds = 3600,
        )

        val result = useCase.execute(LoginCommand("vet_aglot", "vetpass"))

        assertEquals(Role.VET_TECHNICIAN, result.role)
        assertEquals(associationId, result.associationId)
        assertEquals(listOf(ranchA, ranchB), result.ranches)
    }

    @Test
    fun `LoginUseCase throws InvalidCredentialsException on wrong password or unknown user`() {
        val owner = AuthUser(
            id = UUID.randomUUID(),
            username = "juan_dorper",
            passwordHash = "hashed:secret123",
            role = Role.OWNER,
            ranchId = ranchA.id,
            associationId = null,
            permissions = emptyMap(),
        )
        val refreshStore = FakeRefreshTokenPort()
        val useCase = LoginUseCase(
            authUserPort = FakeAuthUserPort(owner),
            passwordHasher = FakePasswordHasherPort(),
            ranchCatalogPort = FakeRanchCatalogPort(mapOf(ranchA.id to ranchA), emptyMap()),
            jwtTokenPort = FakeJwtTokenPort(),
            refreshTokenPort = refreshStore,
            clock = clock,
            refreshTtlSeconds = 3600,
        )

        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute(LoginCommand("juan_dorper", "wrong"))
        }
        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute(LoginCommand("unknown", "secret123"))
        }
        assertTrue(refreshStore.tokensByHash.isEmpty())
    }

    @Test
    fun `RefreshTokenUseCase rotates active token and rejects reused, revoked, or expired tokens`() {
        val user = AuthUser(
            id = UUID.randomUUID(),
            username = "juan_dorper",
            passwordHash = "hashed:secret123",
            role = Role.OWNER,
            ranchId = ranchA.id,
            associationId = null,
            permissions = emptyMap(),
        )
        val refreshStore = FakeRefreshTokenPort()
        val loginUseCase = LoginUseCase(
            authUserPort = FakeAuthUserPort(user),
            passwordHasher = FakePasswordHasherPort(),
            ranchCatalogPort = FakeRanchCatalogPort(mapOf(ranchA.id to ranchA), emptyMap()),
            jwtTokenPort = FakeJwtTokenPort(),
            refreshTokenPort = refreshStore,
            clock = clock,
            refreshTtlSeconds = 3600,
        )
        val refreshUseCase = RefreshTokenUseCase(
            authUserPort = FakeAuthUserPort(user),
            jwtTokenPort = FakeJwtTokenPort(),
            refreshTokenPort = refreshStore,
            clock = clock,
            refreshTtlSeconds = 3600,
        )

        val initial = loginUseCase.execute(LoginCommand("juan_dorper", "secret123"))
        val rotated = refreshUseCase.execute(initial.refreshToken)

        assertEquals("jwt:${user.username}", rotated.accessToken)
        assertNotEquals(initial.refreshToken, rotated.refreshToken)

        // Old refresh token is now revoked and must fail with 401
        assertThrows(InvalidCredentialsException::class.java) {
            refreshUseCase.execute(initial.refreshToken)
        }

        // Expired token must fail with 401
        val expiredHash = "expired-hash"
        refreshStore.tokensByHash[expiredHash] = StoredRefreshToken(
            id = UUID.randomUUID(),
            userId = user.id,
            tokenHash = expiredHash,
            expiresAt = fixedNow.minusSeconds(1),
            revokedAt = null,
        )
        assertThrows(InvalidCredentialsException::class.java) {
            refreshUseCase.execute("raw-expired-token")
        }
    }

    private class FakeAuthUserPort(vararg users: AuthUser) : AuthUserPort {
        private val byUsername = users.associateBy { it.username }
        private val byId = users.associateBy { it.id }

        override fun findByUsername(username: String): AuthUser? = byUsername[username]
        override fun findById(userId: UUID): AuthUser? = byId[userId]
    }

    private class FakePasswordHasherPort : PasswordHasherPort {
        override fun matches(rawPassword: String, encodedHash: String): Boolean = encodedHash == "hashed:$rawPassword"
    }

    private class FakeRanchCatalogPort(
        private val ranchesById: Map<UUID, RanchSummary>,
        private val ranchesByAssociation: Map<UUID, List<RanchSummary>>,
    ) : RanchCatalogPort {
        override fun findById(ranchId: UUID): RanchSummary? = ranchesById[ranchId]
        override fun findByAssociationId(associationId: UUID): List<RanchSummary> =
            ranchesByAssociation[associationId].orEmpty()
    }

    private class FakeJwtTokenPort : JwtTokenPort {
        override val accessTtlSeconds: Long = 900L

        override fun issueAccessToken(user: AuthUser): String = "jwt:${user.username}"

        override fun verifyAccessToken(token: String): JwtClaims? = null
    }

    private class FakeRefreshTokenPort : RefreshTokenPort {
        val tokensByHash = mutableMapOf<String, StoredRefreshToken>()

        override fun hashToken(rawToken: String): String =
            if (rawToken == "raw-expired-token") "expired-hash" else "hash:$rawToken"

        override fun save(token: StoredRefreshToken) {
            tokensByHash[token.tokenHash] = token
        }

        override fun findByTokenHashForUpdate(tokenHash: String): StoredRefreshToken? = tokensByHash[tokenHash]

        override fun revoke(tokenId: UUID, revokedAt: Instant) {
            val existing = tokensByHash.values.firstOrNull { it.id == tokenId } ?: return
            tokensByHash[existing.tokenHash] = existing.copy(revokedAt = revokedAt)
        }

        override fun revokeAllForUser(userId: UUID, revokedAt: Instant) {
            tokensByHash.replaceAll { _, v ->
                if (v.userId == userId && v.revokedAt == null) v.copy(revokedAt = revokedAt) else v
            }
        }
    }
}
