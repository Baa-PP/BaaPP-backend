package com.agrotech.oviapp.presentation

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.BusinessRuleViolationException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.exception.InvalidCredentialsException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
import jakarta.validation.Validation
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.sql.SQLException
import java.util.UUID

@WebMvcTest(
    controllers = [GlobalExceptionHandlerTest.ProbeController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                JwtAuthenticationFilter::class,
                WebMvcConfig::class,
                RanchContextInterceptor::class,
            ],
        ),
    ],
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class, GlobalExceptionHandlerTest.ProbeController::class)
class GlobalExceptionHandlerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    data class ProbePayload(
        @field:NotBlank
        val folio: String?,
        @field:NotNull
        @field:Positive
        val weightKg: BigDecimal?,
    )

    data class CustomMessagePayload(
        @field:NotBlank(message = "El arete interno es obligatorio.")
        val folio: String?,
    )

    @RestController
    @RequestMapping("/probe")
    class ProbeController {
        private val validator = Validation.buildDefaultValidatorFactory().validator

        @GetMapping("/domain-validation")
        fun domainValidation(): Nothing =
            throw DomainValidationException("INVALID_SINIIGA", "El chip SINIIGA debe tener entre 10 y 15 dígitos.")

        @GetMapping("/invalid-credentials")
        fun invalidCredentials(): Nothing =
            throw InvalidCredentialsException("INVALID_CREDENTIALS", "Usuario o contraseña incorrectos.")

        @GetMapping("/forbidden")
        fun forbidden(): Nothing =
            throw ForbiddenAccessException("RANCH_ACCESS_DENIED", "No tiene acceso al rancho activo.")

        @GetMapping("/not-found")
        fun notFound(): Nothing =
            throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "No se encontró el ejemplar solicitado.")

        @GetMapping("/duplicate-folio")
        fun duplicateFolio(): Nothing =
            throw BusinessConflictException.duplicateFolio("OV-042")

        @GetMapping("/birth-window-closed")
        fun birthWindowClosed(): Nothing =
            throw BusinessRuleViolationException.birthRegistrationWindowClosed(daysSinceBirth = 35L)

        @GetMapping("/birth-window-closed-default")
        fun birthWindowClosedDefault(): Nothing =
            throw BusinessRuleViolationException.birthRegistrationWindowClosed()

        @GetMapping("/auth-error")
        fun authError(): Nothing =
            throw BadCredentialsException("RAW_INTERNAL_AUTH_FAILURE secret_token_123")

        @GetMapping("/access-denied")
        fun accessDenied(): Nothing =
            throw AccessDeniedException("RAW_INTERNAL_SPRING_ACCESS_DENIED role=ADMIN")

        @PostMapping("/validate-body")
        fun validateBody(@Valid @RequestBody payload: ProbePayload): Map<String, String> =
            mapOf("status" to "ok")

        @PostMapping("/validate-custom-message")
        fun validateCustomMessage(@Valid @RequestBody payload: CustomMessagePayload): Map<String, String> =
            mapOf("status" to "ok")

        @GetMapping("/constraint-violation")
        fun constraintViolation(): Nothing {
            val violations = validator.validate(ProbePayload(folio = "", weightKg = BigDecimal("-3.50")))
            throw ConstraintViolationException("RAW_CONSTRAINT_VIOLATION_TEXT", violations)
        }

        @GetMapping("/method-validation")
        fun methodValidation(@RequestParam("limit") @Min(1) limit: Int): Map<String, Int> =
            mapOf("limit" to limit)

        @PostMapping("/validate-combined")
        fun validateCombined(
            @RequestParam("limit") @Min(1) limit: Int,
            @Valid @RequestBody payload: ProbePayload,
        ): Map<String, Any> =
            mapOf("limit" to limit, "folio" to payload.folio.orEmpty())

        @GetMapping("/invalid-return-value")
        @Valid
        fun invalidReturnValue(): ProbePayload =
            ProbePayload(folio = "", weightKg = BigDecimal("-1.0"))

        @GetMapping("/type-mismatch/{id}")
        fun typeMismatch(@PathVariable("id") id: UUID): Map<String, String> =
            mapOf("id" to id.toString())

        @GetMapping("/missing-param")
        fun missingParam(@RequestParam("folio") folio: String): Map<String, String> =
            mapOf("folio" to folio)

        @GetMapping("/missing-header")
        fun missingHeader(@RequestHeader("X-Active-Ranch-Id") ranchId: String): Map<String, String> =
            mapOf("ranchId" to ranchId)

        @GetMapping("/data-integrity")
        fun dataIntegrity(): Nothing {
            val sqlEx = SQLException(
                "ERROR: duplicate key value violates unique constraint \"animales_rancho_id_folio_interno_key\"",
                "23505",
            )
            throw DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \"animales_rancho_id_folio_interno_key\"] [INSERT INTO animales ...]",
                sqlEx,
            )
        }

        @GetMapping("/unexpected")
        fun unexpected(): Nothing =
            throw RuntimeException("Sensitive DB credential=secret_pass_999 at com.agrotech.oviapp.InternalRepo:42")
    }

    @Test
    fun `maps all six DomainException types to 400, 401, 403, 404, 409, and 422 with uniform JSON`() {
        val r400 = mockMvc.get("/probe/domain-validation")
            .andExpect { status { isBadRequest() } }
            .andReturn()
        assertUniformError(r400, expectedCode = "INVALID_SINIIGA")
        assertTrue(readError(r400).message.contains("SINIIGA"))

        val r401 = mockMvc.get("/probe/invalid-credentials")
            .andExpect { status { isUnauthorized() } }
            .andReturn()
        assertUniformError(r401, expectedCode = "INVALID_CREDENTIALS")

        val r403 = mockMvc.get("/probe/forbidden")
            .andExpect { status { isForbidden() } }
            .andReturn()
        assertUniformError(r403, expectedCode = "RANCH_ACCESS_DENIED")

        val r404 = mockMvc.get("/probe/not-found")
            .andExpect { status { isNotFound() } }
            .andReturn()
        assertUniformError(r404, expectedCode = "ANIMAL_NOT_FOUND")

        val r409 = mockMvc.get("/probe/duplicate-folio")
            .andExpect { status { isConflict() } }
            .andReturn()
        assertUniformError(r409, expectedCode = "FOLIO_DUPLICADO")
        assertTrue(readError(r409).message.contains("OV-042"))

        val r422 = mockMvc.get("/probe/birth-window-closed")
            .andExpect { status { isUnprocessableEntity() } }
            .andReturn()
        assertUniformError(r422, expectedCode = "VENTANA_PARTO_CERRADA")
        assertTrue(readError(r422).message.contains("30 días"))
        assertTrue(readError(r422).message.contains("35 días"))

        val r422Default = mockMvc.get("/probe/birth-window-closed-default")
            .andExpect { status { isUnprocessableEntity() } }
            .andReturn()
        assertUniformError(r422Default, expectedCode = "VENTANA_PARTO_CERRADA")
        assertTrue(readError(r422Default).message.contains("30 días"))
    }

    @Test
    fun `maps AuthenticationException to 401 and AccessDeniedException to 403 without leaking raw messages`() {
        val r401 = mockMvc.get("/probe/auth-error")
            .andExpect { status { isUnauthorized() } }
            .andReturn()
        assertUniformError(r401, expectedCode = "UNAUTHORIZED")
        assertNoInternalLeaks(r401, "RAW_INTERNAL_AUTH_FAILURE", "secret_token_123", "BadCredentialsException")

        val r403 = mockMvc.get("/probe/access-denied")
            .andExpect { status { isForbidden() } }
            .andReturn()
        assertUniformError(r403, expectedCode = "FORBIDDEN")
        assertNoInternalLeaks(r403, "RAW_INTERNAL_SPRING_ACCESS_DENIED", "role=ADMIN", "AccessDeniedException")
    }

    @Test
    fun `maps Bean Validation errors to 400 VALIDATION_ERROR in Spanish listing invalid fields without English defaults`() {
        val result = mockMvc.post("/probe/validate-body") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"folio": "   ", "weightKg": -2.5}"""
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        val error = assertUniformError(result, expectedCode = "VALIDATION_ERROR")
        assertTrue(error.message.contains("folio"), "Expected field 'folio' in: ${error.message}")
        assertTrue(error.message.contains("weightKg"), "Expected field 'weightKg' in: ${error.message}")
        assertNoInternalLeaks(
            result,
            "must not be blank",
            "must be greater than",
            "MethodArgumentNotValidException",
            "ProbePayload",
        )

        val customResult = mockMvc.post("/probe/validate-custom-message") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"folio": ""}"""
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        val customError = assertUniformError(customResult, expectedCode = "VALIDATION_ERROR")
        assertTrue(customError.message.contains("folio"))
        assertTrue(customError.message.contains("El arete interno es obligatorio"))
        assertFalse(customError.message.contains(".."))
    }

    @Test
    fun `maps ConstraintViolationException and handler method validation to 400 VALIDATION_ERROR in Spanish`() {
        val cvResult = mockMvc.get("/probe/constraint-violation")
            .andExpect { status { isBadRequest() } }
            .andReturn()
        val cvError = assertUniformError(cvResult, expectedCode = "VALIDATION_ERROR")
        assertTrue(cvError.message.contains("folio"), "Expected field 'folio' in: ${cvError.message}")
        assertTrue(cvError.message.contains("weightKg"), "Expected field 'weightKg' in: ${cvError.message}")
        assertNoInternalLeaks(
            cvResult,
            "RAW_CONSTRAINT_VIOLATION_TEXT",
            "must not be blank",
            "must be greater than",
            "ConstraintViolationException",
        )

        val methodValResult = mockMvc.get("/probe/method-validation") {
            param("limit", "0")
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
        val methodValError = assertUniformError(methodValResult, expectedCode = "VALIDATION_ERROR")
        assertTrue(methodValError.message.contains("limit"), "Expected param 'limit' in: ${methodValError.message}")
        assertNoInternalLeaks(
            methodValResult,
            "must be greater than",
            "Validation failure",
            "HandlerMethodValidationException",
        )
    }

    @Test
    fun `preserves nested body field names alongside scalar params in combined method validation`() {
        val result = mockMvc.post("/probe/validate-combined") {
            param("limit", "0")
            contentType = MediaType.APPLICATION_JSON
            content = """{"folio": "   ", "weightKg": -2.5}"""
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()

        val error = assertUniformError(result, expectedCode = "VALIDATION_ERROR")
        assertTrue(error.message.contains("limit"), "Expected scalar param 'limit' in: ${error.message}")
        assertTrue(error.message.contains("folio"), "Expected nested body field 'folio' in: ${error.message}")
        assertTrue(error.message.contains("weightKg"), "Expected nested body field 'weightKg' in: ${error.message}")
        assertFalse(error.message.contains("payload"), "Must not expose wrapper parameter name 'payload' in: ${error.message}")
        assertNoInternalLeaks(
            result,
            "must not be blank",
            "must be greater than",
            "ProbePayload",
            "HandlerMethodValidationException",
        )
    }

    @Test
    fun `preserves 500 INTERNAL_ERROR when handler method return-value validation fails`() {
        val result = mockMvc.get("/probe/invalid-return-value")
            .andExpect { status { isInternalServerError() } }
            .andReturn()

        assertUniformError(result, expectedCode = "INTERNAL_ERROR")
        assertNoInternalLeaks(
            result,
            "folio",
            "weightKg",
            "ProbePayload",
            "must not be blank",
            "must be greater than",
            "HandlerMethodValidationException",
        )
    }

    @Test
    fun `maps malformed JSON, type mismatch, missing query param, and missing header to 400 without framework leaks`() {
        val malformedJson = mockMvc.post("/probe/validate-body") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"folio": "OV-1", "weightKg": "invalid_decimal""""
        }.andExpect {
            status { isBadRequest() }
        }.andReturn()
        assertUniformError(malformedJson, expectedCode = "INVALID_REQUEST")
        assertNoInternalLeaks(
            malformedJson,
            "JsonParseException",
            "InvalidFormatException",
            "BigDecimal",
            "HttpMessageNotReadableException",
            "Unexpected end-of-input",
        )

        val typeMismatch = mockMvc.get("/probe/type-mismatch/not-a-uuid-value")
            .andExpect { status { isBadRequest() } }
            .andReturn()
        assertUniformError(typeMismatch, expectedCode = "INVALID_REQUEST")
        assertNoInternalLeaks(
            typeMismatch,
            "java.util.UUID",
            "java.lang.String",
            "Failed to convert",
            "not-a-uuid-value",
            "MethodArgumentTypeMismatchException",
        )

        val missingParam = mockMvc.get("/probe/missing-param")
            .andExpect { status { isBadRequest() } }
            .andReturn()
        val missingParamError = assertUniformError(missingParam, expectedCode = "MISSING_PARAMETER")
        assertTrue(missingParamError.message.contains("folio"))
        assertNoInternalLeaks(
            missingParam,
            "Required request parameter",
            "MissingServletRequestParameterException",
        )

        val missingHeader = mockMvc.get("/probe/missing-header")
            .andExpect { status { isBadRequest() } }
            .andReturn()
        val missingHeaderError = assertUniformError(missingHeader, expectedCode = "MISSING_HEADER")
        assertTrue(missingHeaderError.message.contains("X-Active-Ranch-Id"))
        assertNoInternalLeaks(
            missingHeader,
            "Required request header",
            "MissingRequestHeaderException",
        )
    }

    @Test
    fun `maps DataIntegrityViolationException to 409 DATA_CONFLICT without exposing SQL or constraint names`() {
        val result = mockMvc.get("/probe/data-integrity")
            .andExpect { status { isConflict() } }
            .andReturn()

        assertUniformError(result, expectedCode = "DATA_CONFLICT")
        assertNoInternalLeaks(
            result,
            "animales_rancho_id_folio_interno_key",
            "duplicate key",
            "INSERT INTO",
            "23505",
            "SQLException",
            "DataIntegrityViolationException",
        )
    }

    @Test
    fun `preserves 405, 415, and 404 Spring MVC statuses while always returning uniform ApiErrorResponse JSON`() {
        val r405 = mockMvc.delete("/probe/validate-body")
            .andExpect { status { isMethodNotAllowed() } }
            .andReturn()
        assertUniformError(r405, expectedCode = "METHOD_NOT_ALLOWED")
        assertNoInternalLeaks(r405, "Request method", "not supported", "HttpRequestMethodNotSupportedException")

        val r415 = mockMvc.post("/probe/validate-body") {
            contentType = MediaType.TEXT_PLAIN
            content = "plain text"
        }.andExpect {
            status { isUnsupportedMediaType() }
        }.andReturn()
        assertUniformError(r415, expectedCode = "UNSUPPORTED_MEDIA_TYPE")
        assertNoInternalLeaks(r415, "Content-Type", "not supported", "HttpMediaTypeNotSupportedException")

        val r404 = mockMvc.get("/probe/non-existent-route-xyz")
            .andExpect { status { isNotFound() } }
            .andReturn()
        assertUniformError(r404, expectedCode = "RESOURCE_NOT_FOUND")
        assertNoInternalLeaks(r404, "No static resource", "NoResourceFoundException", "non-existent-route-xyz")
    }

    @Test
    fun `maps unexpected RuntimeException to 500 INTERNAL_ERROR without exposing message, class names, or stack trace`() {
        val result = mockMvc.get("/probe/unexpected")
            .andExpect { status { isInternalServerError() } }
            .andReturn()

        assertUniformError(result, expectedCode = "INTERNAL_ERROR")
        assertNoInternalLeaks(
            result,
            "secret_pass_999",
            "Sensitive DB credential",
            "InternalRepo",
            "RuntimeException",
        )
    }

    private fun readError(result: MvcResult): ApiErrorResponse =
        objectMapper.readValue(result.response.contentAsString, ApiErrorResponse::class.java)

    private fun assertUniformError(result: MvcResult, expectedCode: String): ApiErrorResponse {
        val body = result.response.contentAsString
        assertTrue(body.isNotBlank(), "Expected non-empty JSON error body")
        val tree = objectMapper.readTree(body)
        assertEquals(setOf("code", "message"), tree.fieldNames().asSequence().toSet(), "Unexpected JSON fields in: $body")
        assertFalse(tree.has("title"), "Must not return Spring ProblemDetail")
        assertFalse(tree.has("detail"), "Must not return Spring ProblemDetail")
        assertFalse(tree.has("trace"), "Must not return stack trace")

        val error = readError(result)
        assertEquals(expectedCode, error.code)
        assertTrue(error.message.isNotBlank(), "Error message must not be blank")
        return error
    }

    private fun assertNoInternalLeaks(result: MvcResult, vararg forbiddenSubstrings: String) {
        val body = result.response.contentAsString
        val alwaysForbidden = listOf(
            "java.",
            "kotlin.",
            "org.springframework",
            "com.agrotech",
            "com.fasterxml",
            "\tat ",
            "Exception",
            "SQL",
            "ProblemDetail",
        )
        (alwaysForbidden + forbiddenSubstrings).forEach { token ->
            assertFalse(
                body.contains(token),
                "Response body leaked forbidden token '$token': $body",
            )
        }
    }
}
