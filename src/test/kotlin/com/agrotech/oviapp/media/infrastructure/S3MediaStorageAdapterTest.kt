package com.agrotech.oviapp.media.infrastructure

import com.agrotech.oviapp.media.domain.MediaContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

class S3MediaStorageAdapterTest {

    private val s3Presigner = mock(S3Presigner::class.java)
    private val adapter = S3MediaStorageAdapter(
        s3Presigner = s3Presigner,
        bucket = "agrotech-oviapp-media",
        putTtlSeconds = 300L,
        getTtlSeconds = 180L,
    )

    @Test
    fun `generatePresignedUpload sets bucket, key, contentType, contentLength and short TTL`() {
        val presignedPut = mock(PresignedPutObjectRequest::class.java)
        val expectedUrl = "https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/ranch/births/photo.webp?X-Amz-Signature=abc"
        given(presignedPut.url()).willReturn(URI.create(expectedUrl).toURL())
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest::class.java))).willReturn(presignedPut)

        val result = adapter.generatePresignedUpload(
            s3Key = "ranch/births/photo.webp",
            contentType = MediaContentType.WEBP,
            contentLengthBytes = 150_000L,
        )

        val captor = ArgumentCaptor.forClass(PutObjectPresignRequest::class.java)
        verify(s3Presigner).presignPutObject(captor.capture())
        val captured = captor.value
        assertEquals(Duration.ofSeconds(300L), captured.signatureDuration())
        assertEquals("agrotech-oviapp-media", captured.putObjectRequest().bucket())
        assertEquals("ranch/births/photo.webp", captured.putObjectRequest().key())
        assertEquals("image/webp", captured.putObjectRequest().contentType())
        assertEquals(150_000L, captured.putObjectRequest().contentLength())

        assertEquals(expectedUrl, result.uploadUrl)
        assertEquals("ranch/births/photo.webp", result.s3Key)
        assertEquals(300L, result.expiresInSeconds)
    }

    @Test
    fun `generatePresignedDownload sets bucket, key and short TTL`() {
        val presignedGet = mock(PresignedGetObjectRequest::class.java)
        val expectedUrl = "https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/ranch/births/photo.webp?X-Amz-Signature=xyz"
        given(presignedGet.url()).willReturn(URI.create(expectedUrl).toURL())
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest::class.java))).willReturn(presignedGet)

        val result = adapter.generatePresignedDownload(s3Key = "ranch/births/photo.webp")

        val captor = ArgumentCaptor.forClass(GetObjectPresignRequest::class.java)
        verify(s3Presigner).presignGetObject(captor.capture())
        val captured = captor.value
        assertEquals(Duration.ofSeconds(180L), captured.signatureDuration())
        assertEquals("agrotech-oviapp-media", captured.getObjectRequest().bucket())
        assertEquals("ranch/births/photo.webp", captured.getObjectRequest().key())

        assertEquals(expectedUrl, result.downloadUrl)
        assertEquals("ranch/births/photo.webp", result.s3Key)
        assertEquals(180L, result.expiresInSeconds)
    }
}
