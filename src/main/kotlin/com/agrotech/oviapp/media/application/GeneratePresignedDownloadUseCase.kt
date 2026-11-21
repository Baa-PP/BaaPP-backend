package com.agrotech.oviapp.media.application

import com.agrotech.oviapp.media.domain.MediaPolicy
import com.agrotech.oviapp.media.domain.MediaStoragePort
import com.agrotech.oviapp.media.domain.PresignedDownloadResult
import org.springframework.stereotype.Service
import java.util.UUID

data class GeneratePresignedDownloadCommand(
    val ranchId: UUID,
    val s3Key: String,
)

@Service
class GeneratePresignedDownloadUseCase(
    private val mediaStoragePort: MediaStoragePort,
) {
    fun execute(command: GeneratePresignedDownloadCommand): PresignedDownloadResult {
        val validatedKey = MediaPolicy.validateRanchScopedKey(command.s3Key, command.ranchId)
        return mediaStoragePort.generatePresignedDownload(validatedKey)
    }
}
