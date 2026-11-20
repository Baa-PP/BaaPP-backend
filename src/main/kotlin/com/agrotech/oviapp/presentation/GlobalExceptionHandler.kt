package com.agrotech.oviapp.presentation

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

data class ApiErrorResponse(
    val code: String,
    val message: String,
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(ex: DomainException): ResponseEntity<ApiErrorResponse> {
        val status = when (ex) {
            is DomainValidationException -> HttpStatus.BAD_REQUEST
            is ResourceNotFoundException -> HttpStatus.NOT_FOUND
            is BusinessConflictException -> HttpStatus.CONFLICT
        }
        return ResponseEntity.status(status).body(ApiErrorResponse(code = ex.code, message = ex.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val fieldSummary = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                code = "VALIDATION_ERROR",
                message = fieldSummary.ifBlank { "Solicitud inválida." },
            ),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun handleMalformedRequest(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(
            ApiErrorResponse(
                code = "INVALID_REQUEST",
                message = "Formato de solicitud o parámetro inválido.",
            ),
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiErrorResponse> {
        logger.error("Unexpected error processing request", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(
                code = "INTERNAL_ERROR",
                message = "Error interno del servidor.",
            ),
        )
    }
}
