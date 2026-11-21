package com.agrotech.oviapp.media.application

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.media.domain.MediaContentType
import com.agrotech.oviapp.media.domain.MediaStoragePort
import com.agrotech.oviapp.media.domain.PresignedDownloadResult
import com.agrotech.oviapp.media.domain.PresignedUploadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class MediaUseCasesTest {

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")

    @Test
    fun `GeneratePresignedUploadUseCase delegates safe ranch-prefixed key and returns presigned URL`() {
        val port = FakeMediaStoragePort()
        val useCase = GeneratePresignedUploadUseCase(port, maxSizeBytes = 10_485_760L)

        val result = useCase.execute(
            GeneratePresignedUploadCommand(
                ranchId = ranchId,
                contentType = "image/webp",
                sizeBytes = 204_800L,
                category = "births",
            ),
        )

        assertTrue(result.s3Key.startsWith("$ranchId/births/"))
        assertTrue(result.s3Key.endsWith(".webp"))
        assertEquals(300L, result.expiresInSeconds)
        assertEquals(MediaContentType.WEBP, port.lastUploadContentType)
        assertEquals(204_800L, port.lastUploadSizeBytes)
    }

    @Test
    fun `GeneratePresignedUploadUseCase rejects invalid MIME or oversized payload before calling port`() {
        val port = FakeMediaStoragePort()
        val useCase = GeneratePresignedUploadUseCase(port, maxSizeBytes = 1_000_000L)

        assertThrows(DomainValidationException::class.java) {
            useCase.execute(
                GeneratePresignedUploadCommand(
                    ranchId = ranchId,
                    contentType = "image/gif",
                    sizeBytes = 50_000L,
                    category = "births",
                ),
            )
        }

        assertThrows(DomainValidationException::class.java) {
            useCase.execute(
                GeneratePresignedUploadCommand(
                    ranchId = ranchId,
                    contentType = "image/jpeg",
                    sizeBytes = 1_000_001L,
                    category = "births",
                ),
            )
        }

        assertNull(port.lastUploadKey)
    }

    @Test
    fun `GeneratePresignedDownloadUseCase allows active ranch key and blocks cross-ranch or traversal keys`() {
        val port = FakeMediaStoragePort()
        val useCase = GeneratePresignedDownloadUseCase(port)
        val validKey = "$ranchId/births/22222222-2222-2222-2222-222222222222.webp"

        val result = useCase.execute(GeneratePresignedDownloadCommand(ranchId = ranchId, s3Key = validKey))
        assertEquals(validKey, result.s3Key)
        assertEquals(300L, result.expiresInSeconds)
        assertEquals(validKey, port.lastDownloadKey)

        port.lastDownloadKey = null
        assertThrows(ForbiddenAccessException::class.java) {
            useCase.execute(
                GeneratePresignedDownloadCommand(
                    ranchId = ranchId,
                    s3Key = "$otherRanchId/births/22222222-2222-2222-2222-222222222222.webp",
                ),
            )
        }
        assertThrows(DomainValidationException::class.java) {
            useCase.execute(
                GeneratePresignedDownloadCommand(
                    ranchId = ranchId,
                    s3Key = "$ranchId/../$otherRanchId/births/photo.webp",
                ),
            )
        }
        assertNull(port.lastDownloadKey)
    }

    private class FakeMediaStoragePort : MediaStoragePort {
        var lastUploadKey: String? = null
        var lastUploadContentType: MediaContentType? = null
        var lastUploadSizeBytes: Long? = null
        var lastDownloadKey: String? = null

        override fun generatePresignedUpload(
            s3Key: String,
            contentType: MediaContentType,
            contentLengthBytes: Long,
        ): PresignedUploadResult {
            lastUploadKey = s3Key
            lastUploadContentType = contentType
            lastUploadSizeBytes = contentLengthBytes
            return PresignedUploadResult(
                uploadUrl = "https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/$s3Key?X-Amz-Signature=fake",
                s3Key = s3Key,
                expiresInSeconds = 300L,
            )
        }

        override fun generatePresignedDownload(s3Key: String): PresignedDownloadResult {
            lastDownloadKey = s3Key
            return PresignedDownloadResult(
                downloadUrl = "https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/$s3Key?X-Amz-Signature=fake",
                s3Key = s3Key,
                expiresInSeconds = 300L,
            )
        }
    }
}
