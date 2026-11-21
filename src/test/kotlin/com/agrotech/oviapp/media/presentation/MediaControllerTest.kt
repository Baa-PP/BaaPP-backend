package com.agrotech.oviapp.media.presentation

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadUseCase
import com.agrotech.oviapp.media.application.GeneratePresignedUploadCommand
import com.agrotech.oviapp.media.application.GeneratePresignedUploadUseCase
import com.agrotech.oviapp.media.domain.PresignedDownloadResult
import com.agrotech.oviapp.media.domain.PresignedUploadResult
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@WebMvcTest(MediaController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class, RanchAccessAuthorizer::class)
class MediaControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var generatePresignedUploadUseCase: GeneratePresignedUploadUseCase

    @MockitoBean
    private lateinit var generatePresignedDownloadUseCase: GeneratePresignedDownloadUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val ownerPrincipal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")

    @BeforeEach
    fun setUpMembership() {
        given(ranchMembershipPort.hasAccess("producer_1", ranchId)).willReturn(true)
        given(ranchMembershipPort.hasAccess("producer_1", otherRanchId)).willReturn(false)
    }

    @Test
    fun `rejects caller who does not belong to active ranch with 403 before calling use cases`() {
        mockMvc.post("/api/v1/media/presigned-upload") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "contentType": "image/webp",
                  "sizeBytes": 120000,
                  "category": "births"
                }
            """.trimIndent()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.get("/api/v1/media/presigned-download") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
            param("s3Key", "$otherRanchId/births/photo.webp")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        verifyNoInteractions(generatePresignedUploadUseCase, generatePresignedDownloadUseCase)
    }

    @Test
    fun `POST presigned-upload rejects missing ranch header or invalid DTO fields with 400`() {
        mockMvc.post("/api/v1/media/presigned-upload") {
            principal = ownerPrincipal
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "contentType": "image/webp",
                  "sizeBytes": 120000
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_RANCH_CONTEXT") }
        }

        mockMvc.post("/api/v1/media/presigned-upload") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "contentType": "",
                  "sizeBytes": 0
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }

        verifyNoInteractions(generatePresignedUploadUseCase)
    }

    @Test
    fun `POST presigned-upload maps domain validation errors for unsupported MIME or size to 400`() {
        val command = GeneratePresignedUploadCommand(
            ranchId = ranchId,
            contentType = "image/gif",
            sizeBytes = 1024L,
            category = "births",
        )
        given(generatePresignedUploadUseCase.execute(command))
            .willThrow(DomainValidationException("UNSUPPORTED_MEDIA_TYPE", "Tipo de archivo no soportado."))

        mockMvc.post("/api/v1/media/presigned-upload") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "contentType": "image/gif",
                  "sizeBytes": 1024,
                  "category": "births"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("UNSUPPORTED_MEDIA_TYPE") }
        }
    }

    @Test
    fun `POST presigned-upload returns 200 with uploadUrl, s3Key prefixed by active ranch and expiresInSeconds`() {
        val s3Key = "$ranchId/births/22222222-2222-2222-2222-222222222222.webp"
        val uploadUrl = "https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/$s3Key?X-Amz-Signature=sig"
        val command = GeneratePresignedUploadCommand(
            ranchId = ranchId,
            contentType = "image/webp",
            sizeBytes = 180_000L,
            category = "births",
        )
        given(generatePresignedUploadUseCase.execute(command)).willReturn(
            PresignedUploadResult(
                uploadUrl = uploadUrl,
                s3Key = s3Key,
                expiresInSeconds = 300L,
            ),
        )

        mockMvc.post("/api/v1/media/presigned-upload") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "contentType": "image/webp",
                  "sizeBytes": 180000,
                  "category": "births"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.uploadUrl") { value(uploadUrl) }
            jsonPath("$.s3Key") { value(s3Key) }
            jsonPath("$.expiresInSeconds") { value(300) }
        }
    }

    @Test
    fun `GET presigned-download returns 200 for active ranch key and 403 for foreign key`() {
        val ownKey = "$ranchId/births/22222222-2222-2222-2222-222222222222.webp"
        val downloadUrl = "https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/$ownKey?X-Amz-Signature=getsig"
        given(
            generatePresignedDownloadUseCase.execute(
                GeneratePresignedDownloadCommand(ranchId = ranchId, s3Key = ownKey),
            ),
        ).willReturn(
            PresignedDownloadResult(
                downloadUrl = downloadUrl,
                s3Key = ownKey,
                expiresInSeconds = 300L,
            ),
        )

        mockMvc.get("/api/v1/media/presigned-download") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            param("s3Key", ownKey)
        }.andExpect {
            status { isOk() }
            jsonPath("$.downloadUrl") { value(downloadUrl) }
            jsonPath("$.s3Key") { value(ownKey) }
            jsonPath("$.expiresInSeconds") { value(300) }
        }

        val foreignKey = "$otherRanchId/births/photo.webp"
        given(
            generatePresignedDownloadUseCase.execute(
                GeneratePresignedDownloadCommand(ranchId = ranchId, s3Key = foreignKey),
            ),
        ).willThrow(ForbiddenAccessException("MEDIA_ACCESS_DENIED", "La clave S3 no pertenece al rancho activo."))

        mockMvc.get("/api/v1/media/presigned-download") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            param("s3Key", foreignKey)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("MEDIA_ACCESS_DENIED") }
        }
    }
}
