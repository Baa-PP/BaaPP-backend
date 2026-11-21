package com.agrotech.oviapp.media.application

import com.agrotech.oviapp.media.domain.MediaContentType
import com.agrotech.oviapp.media.domain.MediaPolicy
import com.agrotech.oviapp.media.domain.MediaStoragePort
import com.agrotech.oviapp.media.domain.PresignedUploadResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

data class GeneratePresignedUploadCommand(
    val ranchId: UUID,
    val contentType: String,
    val sizeBytes: Long,
    val category: String? = null,
)

@Service
class GeneratePresignedUploadUseCase(
    private val mediaStoragePort: MediaStoragePort,
    @Value("\${app.s3.max-size-bytes:10485760}")
    private val maxSizeBytes: Long = MediaPolicy.DEFAULT_MAX_SIZE_BYTES,
) {
    fun execute(command: GeneratePresignedUploadCommand): PresignedUploadResult {
        val contentType = MediaContentType.fromMimeType(command.contentType)
        MediaPolicy.validateSize(command.sizeBytes, maxSizeBytes)
        val s3Key = MediaPolicy.generateSafeS3Key(
            ranchId = command.ranchId,
            category = command.category,
            contentType = contentType,
        )
        return mediaStoragePort.generatePresignedUpload(
            s3Key = s3Key,
            contentType = contentType,
            contentLengthBytes = command.sizeBytes,
        )
    }
}
