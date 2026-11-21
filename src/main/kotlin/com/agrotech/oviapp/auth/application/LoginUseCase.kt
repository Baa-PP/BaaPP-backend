package com.agrotech.oviapp.auth.application

import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.auth.domain.AuthUserPort
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.auth.domain.PasswordHasherPort
import com.agrotech.oviapp.auth.domain.RanchCatalogPort
import com.agrotech.oviapp.auth.domain.RanchSummary
import com.agrotech.oviapp.auth.domain.RefreshTokenPort
import com.agrotech.oviapp.auth.domain.StoredRefreshToken
import com.agrotech.oviapp.domain.exception.InvalidCredentialsException
import com.agrotech.oviapp.domain.model.Role
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID

data class LoginCommand(
    val username: String,
    val password: String,
)

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val username: String,
    val role: Role,
    val ranchId: UUID?,
    val associationId: UUID?,
    val permissions: Map<String, Any?>,
    val ranches: List<RanchSummary>,
)

@Service
class LoginUseCase(
    private val authUserPort: AuthUserPort,
    private val passwordHasher: PasswordHasherPort,
    private val ranchCatalogPort: RanchCatalogPort,
    private val jwtTokenPort: JwtTokenPort,
    private val refreshTokenPort: RefreshTokenPort,
    private val clock: Clock,
    @Value("\${app.jwt.refresh-ttl-seconds:2592000}")
    private val refreshTtlSeconds: Long,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun execute(command: LoginCommand): AuthResult {
        val normalizedUsername = command.username.trim()
        val user = authUserPort.findByUsername(normalizedUsername)
        if (user == null || !user.active || !passwordHasher.matches(command.password, user.passwordHash)) {
            throw InvalidCredentialsException("INVALID_CREDENTIALS", "Usuario o contraseña inválidos.")
        }

        val accessToken = jwtTokenPort.issueAccessToken(user)
        val rawRefreshToken = generateOpaqueToken()
        val now = clock.instant()
        refreshTokenPort.save(
            StoredRefreshToken(
                id = UUID.randomUUID(),
                userId = user.id,
                tokenHash = refreshTokenPort.hashToken(rawRefreshToken),
                expiresAt = now.plusSeconds(refreshTtlSeconds),
                revokedAt = null,
            ),
        )

        return AuthResult(
            accessToken = accessToken,
            refreshToken = rawRefreshToken,
            expiresInSeconds = jwtTokenPort.accessTtlSeconds,
            username = user.username,
            role = user.role,
            ranchId = user.ranchId,
            associationId = user.associationId,
            permissions = user.permissions,
            ranches = resolveAccessibleRanches(user),
        )
    }

    private fun resolveAccessibleRanches(user: AuthUser): List<RanchSummary> = when (user.role) {
        Role.OWNER, Role.WORKER_BRANCH -> listOfNotNull(user.ranchId?.let { ranchCatalogPort.findById(it) })
        Role.VET_TECHNICIAN, Role.ASSOCIATION_SUPERUSER -> {
            val byAssociation = user.associationId?.let { ranchCatalogPort.findByAssociationId(it) }.orEmpty()
            val direct = user.ranchId?.let { ranchCatalogPort.findById(it) }
            (byAssociation + listOfNotNull(direct))
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
        }
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
