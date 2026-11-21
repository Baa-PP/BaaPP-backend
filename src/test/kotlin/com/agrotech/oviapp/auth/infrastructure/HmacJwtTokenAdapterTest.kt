package com.agrotech.oviapp.auth.infrastructure

import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.domain.model.Role
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class HmacJwtTokenAdapterTest {

    private val objectMapper = jacksonObjectMapper()
    private val secret = "0123456789abcdef0123456789abcdef" // 32 bytes for test only
    private val baseTime = Instant.parse("2026-11-20T18:00:00Z")

    @Test
    fun `issues and verifies valid HS256 JWT with expected claims`() {
        val adapter = HmacJwtTokenAdapter(
            secret = secret,
            accessTtlSeconds = 900,
            objectMapper = objectMapper,
            clock = Clock.fixed(baseTime, ZoneOffset.UTC),
        )
        val user = AuthUser(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            username = "rancho_owner",
            passwordHash = "hash",
            role = Role.OWNER,
            ranchId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            associationId = null,
            permissions = emptyMap(),
        )

        val token = adapter.issueAccessToken(user)
        val claims = adapter.verifyAccessToken(token)

        assertNotNull(claims)
        assertEquals("rancho_owner", claims?.username)
        assertEquals(user.id, claims?.userId)
        assertEquals(Role.OWNER, claims?.role)
        assertEquals(user.ranchId, claims?.ranchId)
    }

    @Test
    fun `rejects tampered or expired JWT`() {
        val issuer = HmacJwtTokenAdapter(
            secret = secret,
            accessTtlSeconds = 60,
            objectMapper = objectMapper,
            clock = Clock.fixed(baseTime, ZoneOffset.UTC),
        )
        val user = AuthUser(
            id = UUID.randomUUID(),
            username = "rancho_owner",
            passwordHash = "hash",
            role = Role.OWNER,
            ranchId = UUID.randomUUID(),
            associationId = null,
            permissions = emptyMap(),
        )
        val token = issuer.issueAccessToken(user)

        val tampered = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'
        assertNull(issuer.verifyAccessToken(tampered))
        assertNull(issuer.verifyAccessToken("not.a.jwt"))

        val laterVerifier = HmacJwtTokenAdapter(
            secret = secret,
            accessTtlSeconds = 60,
            objectMapper = objectMapper,
            clock = Clock.fixed(baseTime.plusSeconds(61), ZoneOffset.UTC),
        )
        assertNull(laterVerifier.verifyAccessToken(token))
    }

    @Test
    fun `rejects missing or short JWT secret`() {
        assertThrows(IllegalStateException::class.java) {
            HmacJwtTokenAdapter(
                secret = "too-short",
                accessTtlSeconds = 900,
                objectMapper = objectMapper,
                clock = Clock.systemUTC(),
            )
        }
    }
}
