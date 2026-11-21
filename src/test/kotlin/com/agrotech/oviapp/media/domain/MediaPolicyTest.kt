package com.agrotech.oviapp.media.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class MediaPolicyTest {

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")

    @Test
    fun `accepts only image webp, jpeg and png content types`() {
        assertEquals(MediaContentType.WEBP, MediaContentType.fromMimeType("image/webp"))
        assertEquals(MediaContentType.JPEG, MediaContentType.fromMimeType("IMAGE/JPEG"))
        assertEquals(MediaContentType.PNG, MediaContentType.fromMimeType(" image/png "))
    }

    @ParameterizedTest
    @ValueSource(strings = ["image/gif", "image/svg+xml", "application/pdf", "text/plain", "", "image/jpg"])
    fun `rejects unsupported content types with DomainValidationException`(mimeType: String) {
        val ex = assertThrows(DomainValidationException::class.java) {
            MediaContentType.fromMimeType(mimeType)
        }
        assertEquals("UNSUPPORTED_MEDIA_TYPE", ex.code)
    }

    @Test
    fun `validates file size strictly greater than zero and at most maxSizeBytes`() {
        MediaPolicy.validateSize(1L, maxSizeBytes = 10_485_760L)
        MediaPolicy.validateSize(10_485_760L, maxSizeBytes = 10_485_760L)

        assertEquals("INVALID_MEDIA_SIZE", assertThrows(DomainValidationException::class.java) {
            MediaPolicy.validateSize(0L, maxSizeBytes = 10_485_760L)
        }.code)

        assertEquals("INVALID_MEDIA_SIZE", assertThrows(DomainValidationException::class.java) {
            MediaPolicy.validateSize(-50L, maxSizeBytes = 10_485_760L)
        }.code)

        assertEquals("INVALID_MEDIA_SIZE", assertThrows(DomainValidationException::class.java) {
            MediaPolicy.validateSize(10_485_761L, maxSizeBytes = 10_485_760L)
        }.code)
    }

    @Test
    fun `generates path-traversal-safe s3Key prefixed with active ranchId`() {
        val objectId = UUID.fromString("22222222-2222-2222-2222-222222222222")

        val cleanKey = MediaPolicy.generateSafeS3Key(
            ranchId = ranchId,
            category = "births",
            contentType = MediaContentType.WEBP,
            objectId = objectId,
        )
        assertEquals("$ranchId/births/$objectId.webp", cleanKey)

        val traversalKey = MediaPolicy.generateSafeS3Key(
            ranchId = ranchId,
            category = "../../etc/passwd\\..\\secret",
            contentType = MediaContentType.JPEG,
            objectId = objectId,
        )
        assertTrue(traversalKey.startsWith("$ranchId/"))
        assertFalse(traversalKey.contains(".."))
        assertFalse(traversalKey.contains("\\"))
        assertTrue(traversalKey.matches(Regex("^$ranchId/[a-z0-9_-]+/$objectId\\.jpg$")))

        val fallbackKey = MediaPolicy.generateSafeS3Key(
            ranchId = ranchId,
            category = "../../../",
            contentType = MediaContentType.PNG,
            objectId = objectId,
        )
        assertEquals("$ranchId/general/$objectId.png", fallbackKey)
    }

    @Test
    fun `validateRanchScopedKey allows valid key under active ranch and blocks traversal or cross-ranch keys`() {
        val validKey = "$ranchId/births/22222222-2222-2222-2222-222222222222.webp"
        assertEquals(validKey, MediaPolicy.validateRanchScopedKey(validKey, ranchId))

        assertThrows(ForbiddenAccessException::class.java) {
            MediaPolicy.validateRanchScopedKey("$otherRanchId/births/photo.webp", ranchId)
        }

        assertThrows(DomainValidationException::class.java) {
            MediaPolicy.validateRanchScopedKey("$ranchId/../$otherRanchId/births/photo.webp", ranchId)
        }

        assertThrows(DomainValidationException::class.java) {
            MediaPolicy.validateRanchScopedKey("$ranchId\\births\\photo.webp", ranchId)
        }

        assertThrows(DomainValidationException::class.java) {
            MediaPolicy.validateRanchScopedKey("$ranchId/", ranchId)
        }

        assertThrows(DomainValidationException::class.java) {
            MediaPolicy.validateRanchScopedKey("   ", ranchId)
        }
    }
}
