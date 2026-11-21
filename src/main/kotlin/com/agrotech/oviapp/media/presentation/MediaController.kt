package com.agrotech.oviapp.media.presentation

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadUseCase
import com.agrotech.oviapp.media.application.GeneratePresignedUploadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedUploadUseCase
import com.agrotech.oviapp.media.domain.PresignedDownloadResult
import com.agrotech.oviapp.media.domain.PresignedUploadResult
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class PresignedUploadRequest(
    @field:NotBlank
    val contentType: String?,
    @field:NotNull
    @field:Positive
    val sizeBytes: Long?,
    val category: String? = null,
)

data class PresignedUploadResponse(
    val uploadUrl: String,
    val s3Key: String,
    val expiresInSeconds: Long,
)

data class PresignedDownloadResponse(
    val downloadUrl: String,
    val s3Key: String,
    val expiresInSeconds: Long,
)

@RestController
@RequestMapping("/api/v1/media")
class MediaController(
    private val ranchAccessAuthorizer: RanchAccessAuthorizer,
    private val generatePresignedUploadUseCase: GeneratePresignedUploadUseCase,
    private val generatePresignedDownloadUseCase: GeneratePresignedDownloadUseCase,
) {

    @PostMapping("/presigned-upload")
    fun generatePresignedUpload(
        authentication: Authentication?,
        @Valid @RequestBody request: PresignedUploadRequest,
    ): PresignedUploadResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return generatePresignedUploadUseCase.execute(
            GeneratePresignedUploadCommand(
                ranchId = ranchId,
                contentType = request.contentType ?: throw invalidPayload("contentType"),
                sizeBytes = request.sizeBytes ?: throw invalidPayload("sizeBytes"),
                category = request.category,
            ),
        ).toResponse()
    }

    @GetMapping("/presigned-download")
    fun generatePresignedDownload(
        authentication: Authentication?,
        @RequestParam("s3Key") s3Key: String,
    ): PresignedDownloadResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return generatePresignedDownloadUseCase.execute(
            GeneratePresignedDownloadCommand(
                ranchId = ranchId,
                s3Key = s3Key,
            ),
        ).toResponse()
    }

    private fun invalidPayload(field: String) =
        DomainValidationException("VALIDATION_ERROR", "Campo obligatorio faltante: $field.")

    private fun PresignedUploadResult.toResponse() = PresignedUploadResponse(
        uploadUrl = uploadUrl,
        s3Key = s3Key,
        expiresInSeconds = expiresInSeconds,
    )

    private fun PresignedDownloadResult.toResponse() = PresignedDownloadResponse(
        downloadUrl = downloadUrl,
        s3Key = s3Key,
        expiresInSeconds = expiresInSeconds,
    )
}
