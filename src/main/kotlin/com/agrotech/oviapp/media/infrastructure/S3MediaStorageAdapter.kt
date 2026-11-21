package com.agrotech.oviapp.media.infrastructure

import com.agrotech.oviapp.media.domain.MediaContentType
import com.agrotech.oviapp.media.domain.MediaStoragePort
import com.agrotech.oviapp.media.domain.PresignedDownloadResult
import com.agrotech.oviapp.media.domain.PresignedUploadResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

@Configuration
class S3MediaConfig {

    @Bean(destroyMethod = "close")
    fun s3Presigner(
        @Value("\${app.s3.region:us-east-2}")
        region: String,
    ): S3Presigner =
        S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
}

@Component
class S3MediaStorageAdapter(
    private val s3Presigner: S3Presigner,
    @Value("\${app.s3.bucket:agrotech-oviapp-media}")
    private val bucket: String,
    @Value("\${app.s3.put-ttl-seconds:300}")
    private val putTtlSeconds: Long = 300L,
    @Value("\${app.s3.get-ttl-seconds:300}")
    private val getTtlSeconds: Long = 300L,
) : MediaStoragePort {

    init {
        require(bucket.isNotBlank()) { "app.s3.bucket no puede estar vacío." }
        require(putTtlSeconds > 0L) { "app.s3.put-ttl-seconds debe ser positivo." }
        require(getTtlSeconds > 0L) { "app.s3.get-ttl-seconds debe ser positivo." }
    }

    override fun generatePresignedUpload(
        s3Key: String,
        contentType: MediaContentType,
        contentLengthBytes: Long,
    ): PresignedUploadResult {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .contentType(contentType.mimeType)
            .contentLength(contentLengthBytes)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(putTtlSeconds))
            .putObjectRequest(putObjectRequest)
            .build()

        val presigned = s3Presigner.presignPutObject(presignRequest)
        return PresignedUploadResult(
            uploadUrl = presigned.url().toString(),
            s3Key = s3Key,
            expiresInSeconds = putTtlSeconds,
        )
    }

    override fun generatePresignedDownload(s3Key: String): PresignedDownloadResult {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(getTtlSeconds))
            .getObjectRequest(getObjectRequest)
            .build()

        val presigned = s3Presigner.presignGetObject(presignRequest)
        return PresignedDownloadResult(
            downloadUrl = presigned.url().toString(),
            s3Key = s3Key,
            expiresInSeconds = getTtlSeconds,
        )
    }
}
