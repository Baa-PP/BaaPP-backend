package com.agrotech.oviapp.animals.presentation

import com.agrotech.oviapp.animals.application.CreateAnimalUseCase
import com.agrotech.oviapp.animals.application.GetAnimalDetailUseCase
import com.agrotech.oviapp.animals.application.ListAnimalsUseCase
import com.agrotech.oviapp.animals.application.UpdateAnimalUseCase
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
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
import org.springframework.test.web.servlet.put
import java.util.UUID

@WebMvcTest(AnimalsController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    RanchAccessAuthorizer::class,
)
class AnimalsSecurityTest {

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var listAnimalsUseCase: ListAnimalsUseCase

    @MockitoBean
    private lateinit var getAnimalDetailUseCase: GetAnimalDetailUseCase

    @MockitoBean
    private lateinit var createAnimalUseCase: CreateAnimalUseCase

    @MockitoBean
    private lateinit var updateAnimalUseCase: UpdateAnimalUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `unauthenticated animal inventory endpoints are blocked with 401`() {
        mockMvc.get("/api/v1/animals")
            .andExpect { status { isUnauthorized() } }

        mockMvc.get("/api/v1/animals/$animalId")
            .andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/v1/animals") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }

        mockMvc.put("/api/v1/animals/$animalId") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }
}
