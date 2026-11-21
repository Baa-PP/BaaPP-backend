package com.agrotech.oviapp.media.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import java.util.UUID

enum class MediaContentType(
    val mimeType: String,
    val extension: String,
) {
    WEBP("image/webp", "webp"),
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    ;

    companion object {
        fun fromMimeType(rawMimeType: String): MediaContentType {
            val normalized = rawMimeType.trim().lowercase()
            return entries.firstOrNull { it.mimeType == normalized }
                ?: throw DomainValidationException(
                    code = "UNSUPPORTED_MEDIA_TYPE",
                    message = "Tipo de archivo no soportado. Permitidos: image/webp, image/jpeg, image/png.",
                )
        }
    }
}

data class PresignedUploadResult(
    val uploadUrl: String,
    val s3Key: String,
    val expiresInSeconds: Long,
)

data class PresignedDownloadResult(
    val downloadUrl: String,
    val s3Key: String,
    val expiresInSeconds: Long,
)

object MediaPolicy {
    const val DEFAULT_MAX_SIZE_BYTES: Long = 10_485_760L
    const val DEFAULT_CATEGORY: String = "general"
    private const val MAX_CATEGORY_LENGTH = 64
    private val UNSAFE_CATEGORY_CHARS = Regex("[^a-z0-9_-]+")

    fun validateSize(
        sizeBytes: Long,
        maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    ) {
        require(maxSizeBytes > 0L) { "maxSizeBytes debe ser positivo." }
        if (sizeBytes <= 0L || sizeBytes > maxSizeBytes) {
            throw DomainValidationException(
                code = "INVALID_MEDIA_SIZE",
                message = "El tamaño del archivo debe ser mayor a 0 y menor o igual a $maxSizeBytes bytes.",
            )
        }
    }

    fun sanitizeCategory(rawCategory: String?): String {
        if (rawCategory.isNullOrBlank()) return DEFAULT_CATEGORY
        val sanitized = rawCategory
            .trim()
            .lowercase()
            .replace(UNSAFE_CATEGORY_CHARS, "-")
            .trim('-')
            .take(MAX_CATEGORY_LENGTH)
            .trim('-')
        return sanitized.ifEmpty { DEFAULT_CATEGORY }
    }

    fun generateSafeS3Key(
        ranchId: UUID,
        category: String?,
        contentType: MediaContentType,
        objectId: UUID = UUID.randomUUID(),
    ): String {
        val safeCategory = sanitizeCategory(category)
        return "$ranchId/$safeCategory/$objectId.${contentType.extension}"
    }

    fun validateRanchScopedKey(
        s3Key: String,
        ranchId: UUID,
    ): String {
        if (s3Key.isBlank()) {
            throw DomainValidationException("INVALID_S3_KEY", "La clave S3 es obligatoria.")
        }
        if (s3Key != s3Key.trim() ||
            s3Key.contains("..") ||
            s3Key.contains("\\") ||
            s3Key.contains("\u0000") ||
            s3Key.contains("//")
        ) {
            throw DomainValidationException(
                code = "INVALID_S3_KEY",
                message = "La clave S3 contiene caracteres o secuencias inválidas.",
            )
        }
        val expectedPrefix = "$ranchId/"
        if (!s3Key.startsWith(expectedPrefix)) {
            throw ForbiddenAccessException(
                code = "MEDIA_ACCESS_DENIED",
                message = "La clave S3 no pertenece al rancho activo.",
            )
        }
        val relativeKey = s3Key.removePrefix(expectedPrefix)
        if (relativeKey.isBlank() || relativeKey.endsWith("/")) {
            throw DomainValidationException("INVALID_S3_KEY", "La clave S3 es inválida.")
        }
        return s3Key
    }
}

interface MediaStoragePort {
    fun generatePresignedUpload(
        s3Key: String,
        contentType: MediaContentType,
        contentLengthBytes: Long,
    ): PresignedUploadResult

    fun generatePresignedDownload(s3Key: String): PresignedDownloadResult
}
