package com.agrotech.oviapp.weights.presentation

import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.SecurityConfig
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.agrotech.oviapp.weights.application.ConfirmWeaningUseCase
import com.agrotech.oviapp.weights.application.GetAnimalWeightsUseCase
import com.agrotech.oviapp.weights.application.RegisterWeightUseCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@WebMvcTest(WeightsController::class)
@Import(SecurityConfig::class, RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class)
class WeightsSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var registerWeightUseCase: RegisterWeightUseCase

    @MockitoBean
    private lateinit var getAnimalWeightsUseCase: GetAnimalWeightsUseCase

    @MockitoBean
    private lateinit var confirmWeaningUseCase: ConfirmWeaningUseCase

    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `unauthenticated weight and weaning endpoints are blocked with 401`() {
        mockMvc.get("/api/v1/animals/$animalId/weights")
            .andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/v1/weights") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/v1/animals/$animalId/wean") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }
}
