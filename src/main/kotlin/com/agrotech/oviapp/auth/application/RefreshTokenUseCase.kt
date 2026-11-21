package com.agrotech.oviapp.auth.application

import com.agrotech.oviapp.auth.domain.AuthUserPort
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.auth.domain.RefreshTokenPort
import com.agrotech.oviapp.auth.domain.StoredRefreshToken
import com.agrotech.oviapp.domain.exception.InvalidCredentialsException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID

data class RefreshResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

@Service
class RefreshTokenUseCase(
    private val authUserPort: AuthUserPort,
    private val jwtTokenPort: JwtTokenPort,
    private val refreshTokenPort: RefreshTokenPort,
    private val clock: Clock,
    @Value("\${app.jwt.refresh-ttl-seconds:2592000}")
    private val refreshTtlSeconds: Long,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun execute(rawRefreshToken: String): RefreshResult {
        if (rawRefreshToken.isBlank()) {
            throw InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado.")
        }

        val now = clock.instant()
        val tokenHash = refreshTokenPort.hashToken(rawRefreshToken.trim())
        val stored = refreshTokenPort.findByTokenHashForUpdate(tokenHash)
            ?: throw InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado.")

        if (stored.isRevoked()) {
            // Reuse of a rotated/revoked token indicates potential theft; revoke all active tokens for this user.
            refreshTokenPort.revokeAllForUser(stored.userId, now)
            throw InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado.")
        }

        if (stored.isExpired(now)) {
            refreshTokenPort.revoke(stored.id, now)
            throw InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado.")
        }

        val user = authUserPort.findById(stored.userId)
            ?: throw InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado.")

        refreshTokenPort.revoke(stored.id, now)

        val newRefreshToken = generateOpaqueToken()
        refreshTokenPort.save(
            StoredRefreshToken(
                id = UUID.randomUUID(),
                userId = user.id,
                tokenHash = refreshTokenPort.hashToken(newRefreshToken),
                expiresAt = now.plusSeconds(refreshTtlSeconds),
                revokedAt = null,
            ),
        )

        return RefreshResult(
            accessToken = jwtTokenPort.issueAccessToken(user),
            refreshToken = newRefreshToken,
            expiresInSeconds = jwtTokenPort.accessTtlSeconds,
        )
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
