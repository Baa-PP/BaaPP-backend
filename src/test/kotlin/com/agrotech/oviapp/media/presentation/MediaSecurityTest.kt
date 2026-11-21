package com.agrotech.oviapp.media.presentation

import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.media.application.GeneratePresignedDownloadUseCase
import com.agrotech.oviapp.media.application.GeneratePresignedUploadUseCase
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.SecurityConfig
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(MediaController::class)
@Import(
    SecurityConfig::class,
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    RanchAccessAuthorizer::class,
)
class MediaSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var generatePresignedUploadUseCase: GeneratePresignedUploadUseCase

    @MockitoBean
    private lateinit var generatePresignedDownloadUseCase: GeneratePresignedDownloadUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @Test
    fun `unauthenticated media endpoints are blocked with 401`() {
        mockMvc.post("/api/v1/media/presigned-upload") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/webp","sizeBytes":1024}"""
        }.andExpect { status { isUnauthorized() } }

        mockMvc.get("/api/v1/media/presigned-download") {
            param("s3Key", "11111111-1111-1111-1111-111111111111/births/photo.webp")
        }.andExpect { status { isUnauthorized() } }
    }
}
