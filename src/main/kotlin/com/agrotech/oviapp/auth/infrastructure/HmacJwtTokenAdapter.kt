package com.agrotech.oviapp.auth.infrastructure

import com.agrotech.oviapp.auth.domain.AuthUser
import com.agrotech.oviapp.auth.domain.JwtClaims
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.model.Role
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacJwtTokenAdapter(
    @Value("\${app.jwt.secret:}")
    secret: String,
    @Value("\${app.jwt.access-ttl-seconds:900}")
    override val accessTtlSeconds: Long,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : JwtTokenPort {

    private val secretBytes = secret.toByteArray(StandardCharsets.UTF_8)
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    init {
        check(secretBytes.size >= MIN_SECRET_BYTES) {
            "app.jwt.secret (JWT_SECRET) must be configured with at least $MIN_SECRET_BYTES bytes."
        }
        check(accessTtlSeconds > 0) {
            "app.jwt.access-ttl-seconds must be > 0."
        }
    }

    override fun issueAccessToken(user: AuthUser): String {
        val now = clock.instant()
        val exp = now.plusSeconds(accessTtlSeconds)

        val headerJson = objectMapper.writeValueAsBytes(mapOf("alg" to "HS256", "typ" to "JWT"))
        val payload = buildMap<String, Any?> {
            put("sub", user.username)
            put("uid", user.id.toString())
            put("role", user.role.name)
            user.ranchId?.let { put("ranchId", it.toString()) }
            user.associationId?.let { put("associationId", it.toString()) }
            put("iat", now.epochSecond)
            put("exp", exp.epochSecond)
        }
        val payloadJson = objectMapper.writeValueAsBytes(payload)

        val signingInput = "${encoder.encodeToString(headerJson)}.${encoder.encodeToString(payloadJson)}"
        val signature = sign(signingInput.toByteArray(StandardCharsets.UTF_8))
        return "$signingInput.${encoder.encodeToString(signature)}"
    }

    override fun verifyAccessToken(token: String): JwtClaims? {
        val parts = token.split('.')
        if (parts.size != 3) return null

        val headerBytes = runCatching { decoder.decode(parts[0]) }.getOrNull() ?: return null
        val payloadBytes = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        val signatureBytes = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return null

        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
        val expectedSignature = sign(signingInput)
        if (!MessageDigest.isEqual(expectedSignature, signatureBytes)) return null

        val header = runCatching { objectMapper.readValue(headerBytes, mapType) }.getOrNull() ?: return null
        if (header["alg"] != "HS256") return null

        val payload = runCatching { objectMapper.readValue(payloadBytes, mapType) }.getOrNull() ?: return null
        val expEpoch = (payload["exp"] as? Number)?.toLong() ?: return null
        val expiresAt = Instant.ofEpochSecond(expEpoch)
        if (!expiresAt.isAfter(clock.instant())) return null

        val username = (payload["sub"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val userId = (payload["uid"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val role = (payload["role"] as? String)?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: return null
        val ranchId = (payload["ranchId"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val associationId = (payload["associationId"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        return JwtClaims(
            userId = userId,
            username = username,
            role = role,
            ranchId = ranchId,
            associationId = associationId,
            expiresAt = expiresAt,
        )
    }

    private fun sign(data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secretBytes, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val MIN_SECRET_BYTES = 32
    }
}
