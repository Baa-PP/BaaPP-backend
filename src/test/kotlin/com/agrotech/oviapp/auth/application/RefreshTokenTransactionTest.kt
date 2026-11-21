package com.agrotech.oviapp.auth.application

import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.auth.domain.AuthUserPort
import com.agrotech.oviapp.auth.domain.JwtClaims
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.auth.domain.RefreshTokenPort
import com.agrotech.oviapp.auth.domain.StoredRefreshToken
import com.agrotech.oviapp.domain.exception.InvalidCredentialsException
import com.agrotech.oviapp.domain.model.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@SpringJUnitConfig(RefreshTokenTransactionTest.TestConfig::class)
class RefreshTokenTransactionTest {

    @Configuration
    @EnableTransactionManagement
    class TestConfig {
        @Bean
        fun store(): TransactionalTokenStore = TransactionalTokenStore()

        @Bean
        fun transactionManager(store: TransactionalTokenStore): PlatformTransactionManager =
            InMemoryTransactionManager(store)

        @Bean
        fun authUserPort(): AuthUserPort = StubAuthUserPort()

        @Bean
        fun jwtTokenPort(): JwtTokenPort = ControllableJwtTokenPort()

        @Bean
        fun refreshTokenPort(store: TransactionalTokenStore): RefreshTokenPort = store

        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-11-20T18:00:00Z"), ZoneOffset.UTC)

        @Bean
        fun refreshTokenUseCase(
            authUserPort: AuthUserPort,
            jwtTokenPort: JwtTokenPort,
            refreshTokenPort: RefreshTokenPort,
            clock: Clock,
        ): RefreshTokenUseCase = RefreshTokenUseCase(
            authUserPort = authUserPort,
            jwtTokenPort = jwtTokenPort,
            refreshTokenPort = refreshTokenPort,
            clock = clock,
            refreshTtlSeconds = 3600L,
        )
    }

    @Autowired
    private lateinit var useCase: RefreshTokenUseCase

    @Autowired
    private lateinit var store: TransactionalTokenStore

    @Autowired
    private lateinit var jwtTokenPort: ControllableJwtTokenPort

    @Autowired
    private lateinit var authUserPort: StubAuthUserPort

    private val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @BeforeEach
    fun resetState() {
        store.reset()
        jwtTokenPort.failOnIssue = false
        authUserPort.user = AuthUser(
            id = userId,
            username = "owner_tx",
            passwordHash = "hash",
            role = Role.OWNER,
            ranchId = UUID.randomUUID(),
            associationId = null,
            permissions = emptyMap(),
        )
    }

    @Test
    fun `reuse rejection commits revokeAllForUser so replacement token cannot be used`() {
        val rawR0 = "token-r0"
        store.seed(
            StoredRefreshToken(
                id = UUID.randomUUID(),
                userId = userId,
                tokenHash = store.hashToken(rawR0),
                expiresAt = Instant.parse("2026-11-21T18:00:00Z"),
                revokedAt = null,
            ),
        )

        // Attacker rotates R0 -> R1
        val rotated = useCase.execute(rawR0)
        val rawR1 = rotated.refreshToken

        // Legitimate user replays R0 -> rejected with InvalidCredentialsException, but revocation MUST commit
        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute(rawR0)
        }

        val committedR1 = store.committed[store.hashToken(rawR1)]
        assertNotNull(committedR1?.revokedAt)

        // Attacker's R1 is now unusable
        assertThrows(InvalidCredentialsException::class.java) {
            useCase.execute(rawR1)
        }
    }

    @Test
    fun `unexpected failure during replacement rolls back revocation of current token`() {
        val rawR0 = "token-r0"
        store.seed(
            StoredRefreshToken(
                id = UUID.randomUUID(),
                userId = userId,
                tokenHash = store.hashToken(rawR0),
                expiresAt = Instant.parse("2026-11-21T18:00:00Z"),
                revokedAt = null,
            ),
        )
        jwtTokenPort.failOnIssue = true

        assertThrows(IllegalStateException::class.java) {
            useCase.execute(rawR0)
        }

        val committedR0 = store.committed[store.hashToken(rawR0)]
        assertNull(committedR0?.revokedAt)
        assertEquals(1, store.committed.size)
    }

    class StubAuthUserPort : AuthUserPort {
        var user: AuthUser? = null
        override fun findByUsername(username: String): AuthUser? = user?.takeIf { it.username == username }
        override fun findById(userId: UUID): AuthUser? = user?.takeIf { it.id == userId }
    }

    class ControllableJwtTokenPort : JwtTokenPort {
        override val accessTtlSeconds: Long = 900L
        var failOnIssue: Boolean = false

        override fun issueAccessToken(user: AuthUser): String {
            if (failOnIssue) throw IllegalStateException("signing failure")
            return "jwt:${user.username}"
        }

        override fun verifyAccessToken(token: String): JwtClaims? = null
    }

    class TransactionalTokenStore : RefreshTokenPort {
        val committed = mutableMapOf<String, StoredRefreshToken>()
        private var staged: MutableMap<String, StoredRefreshToken>? = null

        fun seed(token: StoredRefreshToken) {
            committed[token.tokenHash] = token
        }

        fun reset() {
            committed.clear()
            staged = null
        }

        fun beginTx() {
            staged = committed.toMutableMap()
        }

        fun commitTx() {
            val buffer = staged ?: return
            committed.clear()
            committed.putAll(buffer)
            staged = null
        }

        fun rollbackTx() {
            staged = null
        }

        private fun activeMap(): MutableMap<String, StoredRefreshToken> = staged ?: committed

        override fun hashToken(rawToken: String): String = "hash:$rawToken"

        override fun save(token: StoredRefreshToken) {
            activeMap()[token.tokenHash] = token
        }

        override fun findByTokenHashForUpdate(tokenHash: String): StoredRefreshToken? = activeMap()[tokenHash]

        override fun revoke(tokenId: UUID, revokedAt: Instant) {
            val map = activeMap()
            val entry = map.values.firstOrNull { it.id == tokenId } ?: return
            map[entry.tokenHash] = entry.copy(revokedAt = revokedAt)
        }

        override fun revokeAllForUser(userId: UUID, revokedAt: Instant) {
            val map = activeMap()
            map.replaceAll { _, token ->
                if (token.userId == userId && token.revokedAt == null) token.copy(revokedAt = revokedAt) else token
            }
        }
    }

    class InMemoryTransactionManager(
        private val store: TransactionalTokenStore,
    ) : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = store.beginTx()
        override fun doCommit(status: DefaultTransactionStatus) = store.commitTx()
        override fun doRollback(status: DefaultTransactionStatus) = store.rollbackTx()
    }
}
