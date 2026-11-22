package com.agrotech.oviapp.presentation

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.BusinessRuleViolationException
import com.agrotech.oviapp.domain.exception.DomainException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.exception.InvalidCredentialsException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

data class ApiErrorResponse(
    val code: String,
    val message: String,
)

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(ex: DomainException): ResponseEntity<ApiErrorResponse> {
        val status = when (ex) {
            is DomainValidationException -> HttpStatus.BAD_REQUEST
            is InvalidCredentialsException -> HttpStatus.UNAUTHORIZED
            is ForbiddenAccessException -> HttpStatus.FORBIDDEN
            is ResourceNotFoundException -> HttpStatus.NOT_FOUND
            is BusinessConflictException -> HttpStatus.CONFLICT
            is BusinessRuleViolationException -> HttpStatus.UNPROCESSABLE_ENTITY
        }
        return ResponseEntity.status(status).body(ApiErrorResponse(code = ex.code, message = ex.message))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse(
                code = CODE_UNAUTHORIZED,
                message = MSG_UNAUTHORIZED,
            ),
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiErrorResponse(
                code = CODE_FORBIDDEN,
                message = MSG_FORBIDDEN,
            ),
        )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiErrorResponse> {
        val details = ex.constraintViolations
            .map { violation -> formatConstraintViolation(violation) }
            .distinct()
            .sorted()
        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                code = CODE_VALIDATION_ERROR,
                message = formatValidationSummary(details),
            ),
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ApiErrorResponse> {
        log.warn("Data integrity violation while processing request", ex)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                code = CODE_DATA_CONFLICT,
                message = MSG_DATA_CONFLICT,
            ),
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val details = ex.bindingResult.fieldErrors
            .map { error -> formatFieldError(error) }
            .distinct()
            .sorted()
        return handleExceptionInternal(
            ex,
            ApiErrorResponse(code = CODE_VALIDATION_ERROR, message = formatValidationSummary(details)),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        if (ex.isForReturnValue) {
            return handleExceptionInternal(
                ex,
                ApiErrorResponse(code = CODE_INTERNAL_ERROR, message = MSG_INTERNAL_ERROR),
                headers,
                status,
                request,
            )
        }
        val details = ex.parameterValidationResults
            .flatMap { result -> formatParameterValidationResult(result) }
            .distinct()
            .sorted()
        return handleExceptionInternal(
            ex,
            ApiErrorResponse(code = CODE_VALIDATION_ERROR, message = formatValidationSummary(details)),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        handleExceptionInternal(
            ex,
            ApiErrorResponse(code = CODE_INVALID_REQUEST, message = MSG_INVALID_BODY),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )

    override fun handleTypeMismatch(
        ex: TypeMismatchException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val paramName = (ex as? MethodArgumentTypeMismatchException)?.name?.takeIf { it.isNotBlank() }
        val message = if (paramName != null) {
            "El parámetro '$paramName' tiene un formato inválido."
        } else {
            MSG_INVALID_REQUEST
        }
        return handleExceptionInternal(
            ex,
            ApiErrorResponse(code = CODE_INVALID_REQUEST, message = message),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )
    }

    override fun handleMissingServletRequestParameter(
        ex: MissingServletRequestParameterException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val paramName = ex.parameterName.takeIf { it.isNotBlank() }
        val message = if (paramName != null) {
            "Falta el parámetro obligatorio '$paramName'."
        } else {
            MSG_MISSING_PARAMETER
        }
        return handleExceptionInternal(
            ex,
            ApiErrorResponse(code = CODE_MISSING_PARAMETER, message = message),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )
    }

    override fun handleServletRequestBindingException(
        ex: ServletRequestBindingException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        if (ex is MissingRequestHeaderException) {
            val headerName = ex.headerName.takeIf { it.isNotBlank() }
            val message = if (headerName != null) {
                "Falta el encabezado obligatorio '$headerName'."
            } else {
                MSG_MISSING_HEADER
            }
            return handleExceptionInternal(
                ex,
                ApiErrorResponse(code = CODE_MISSING_HEADER, message = message),
                headers,
                HttpStatus.BAD_REQUEST,
                request,
            )
        }
        return handleExceptionInternal(
            ex,
            ApiErrorResponse(code = CODE_INVALID_REQUEST, message = MSG_INVALID_REQUEST),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val apiError = body as? ApiErrorResponse ?: errorForStatus(statusCode)
        if (statusCode.is5xxServerError) {
            log.error("Unexpected server error processing request (status={})", statusCode.value(), ex)
        }
        return super.handleExceptionInternal(ex, apiError, headers, statusCode, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiErrorResponse> {
        if (ex is ErrorResponse) {
            val status = ex.statusCode
            if (status.is5xxServerError) {
                log.error("Unexpected server error processing request (status={})", status.value(), ex)
            }
            return ResponseEntity.status(status).headers(ex.headers).body(errorForStatus(status))
        }
        log.error("Unexpected error processing request", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(
                code = CODE_INTERNAL_ERROR,
                message = MSG_INTERNAL_ERROR,
            ),
        )
    }

    private fun formatFieldError(error: FieldError): String {
        val reason = extractCustomMessage(error) ?: spanishConstraintReason(error.code)
        return "${error.field}: ${reason.trimEnd('.')}"
    }

    private fun extractCustomMessage(error: FieldError): String? {
        if (!error.contains(ConstraintViolation::class.java)) {
            return null
        }
        val violation = runCatching { error.unwrap(ConstraintViolation::class.java) }.getOrNull()
            ?: return null
        return extractCustomViolationMessage(violation)
    }

    private fun formatConstraintViolation(violation: ConstraintViolation<*>): String {
        val field = violation.propertyPath
            ?.lastOrNull()
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: violation.propertyPath?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
        val reason = extractCustomViolationMessage(violation)
            ?: spanishConstraintReason(violation.constraintDescriptor?.annotation?.annotationClass?.simpleName)
        val normalizedReason = reason.trimEnd('.')
        return if (field != null) "$field: $normalizedReason" else normalizedReason
    }

    private fun extractCustomViolationMessage(violation: ConstraintViolation<*>): String? =
        violation.messageTemplate
            ?.takeIf { it.isNotBlank() && !it.startsWith("{") }
            ?.let { violation.message?.trim()?.takeIf { msg -> msg.isNotBlank() } }

    private fun formatParameterValidationResult(result: ParameterValidationResult): List<String> {
        val paramName = resolveParameterName(result)
        return result.resolvableErrors.map { error ->
            when (error) {
                is FieldError -> formatFieldError(error)
                else -> {
                    val reason = spanishConstraintReason(error.codes?.lastOrNull())
                    if (paramName != null) "$paramName: $reason" else reason
                }
            }
        }
    }

    private fun resolveParameterName(result: ParameterValidationResult): String? {
        val methodParam = result.methodParameter
        val requestParamName = methodParam.getParameterAnnotation(RequestParam::class.java)
            ?.let { it.name.ifBlank { it.value } }
            ?.takeIf { it.isNotBlank() }
        val pathVarName = methodParam.getParameterAnnotation(PathVariable::class.java)
            ?.let { it.name.ifBlank { it.value } }
            ?.takeIf { it.isNotBlank() }
        val headerName = methodParam.getParameterAnnotation(RequestHeader::class.java)
            ?.let { it.name.ifBlank { it.value } }
            ?.takeIf { it.isNotBlank() }
        return requestParamName ?: pathVarName ?: headerName ?: methodParam.parameterName?.takeIf { it.isNotBlank() }
    }

    private fun formatValidationSummary(details: List<String>): String =
        if (details.isEmpty()) {
            MSG_VALIDATION_GENERIC
        } else {
            "Revise los datos enviados: ${details.joinToString("; ")}."
        }

    private fun spanishConstraintReason(constraintCode: String?): String =
        when (constraintCode) {
            "NotNull" -> "es obligatorio"
            "NotBlank", "NotEmpty" -> "no puede estar vacío"
            "Positive" -> "debe ser mayor a cero"
            "PositiveOrZero" -> "no puede ser negativo"
            "Negative" -> "debe ser menor a cero"
            "NegativeOrZero" -> "no puede ser positivo"
            "Min", "DecimalMin" -> "está por debajo del mínimo permitido"
            "Max", "DecimalMax" -> "excede el máximo permitido"
            "Digits" -> "tiene un formato numérico inválido"
            "Size" -> "no cumple con la longitud permitida"
            "Pattern" -> "no cumple con el formato requerido"
            "Email" -> "no tiene un formato de correo válido"
            "Past", "PastOrPresent" -> "no puede ser una fecha futura"
            "Future", "FutureOrPresent" -> "debe ser una fecha futura"
            else -> "no es válido"
        }

    private fun errorForStatus(statusCode: HttpStatusCode): ApiErrorResponse =
        when (statusCode.value()) {
            HttpStatus.BAD_REQUEST.value() -> ApiErrorResponse(CODE_INVALID_REQUEST, MSG_INVALID_REQUEST)
            HttpStatus.UNAUTHORIZED.value() -> ApiErrorResponse(CODE_UNAUTHORIZED, MSG_UNAUTHORIZED)
            HttpStatus.FORBIDDEN.value() -> ApiErrorResponse(CODE_FORBIDDEN, MSG_FORBIDDEN)
            HttpStatus.NOT_FOUND.value() -> ApiErrorResponse(CODE_RESOURCE_NOT_FOUND, MSG_RESOURCE_NOT_FOUND)
            HttpStatus.METHOD_NOT_ALLOWED.value() -> ApiErrorResponse(CODE_METHOD_NOT_ALLOWED, MSG_METHOD_NOT_ALLOWED)
            HttpStatus.NOT_ACCEPTABLE.value() -> ApiErrorResponse(CODE_NOT_ACCEPTABLE, MSG_NOT_ACCEPTABLE)
            HttpStatus.CONFLICT.value() -> ApiErrorResponse(CODE_DATA_CONFLICT, MSG_DATA_CONFLICT)
            HttpStatus.PAYLOAD_TOO_LARGE.value() -> ApiErrorResponse(CODE_PAYLOAD_TOO_LARGE, MSG_PAYLOAD_TOO_LARGE)
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> ApiErrorResponse(CODE_UNSUPPORTED_MEDIA_TYPE, MSG_UNSUPPORTED_MEDIA_TYPE)
            HttpStatus.UNPROCESSABLE_ENTITY.value() -> ApiErrorResponse(CODE_BUSINESS_RULE_VIOLATION, MSG_BUSINESS_RULE_VIOLATION)
            HttpStatus.SERVICE_UNAVAILABLE.value() -> ApiErrorResponse(CODE_SERVICE_UNAVAILABLE, MSG_SERVICE_UNAVAILABLE)
            else -> if (statusCode.is5xxServerError) {
                ApiErrorResponse(CODE_INTERNAL_ERROR, MSG_INTERNAL_ERROR)
            } else {
                ApiErrorResponse(CODE_INVALID_REQUEST, MSG_INVALID_REQUEST)
            }
        }

    companion object {
        private const val CODE_UNAUTHORIZED = "UNAUTHORIZED"
        private const val CODE_FORBIDDEN = "FORBIDDEN"
        private const val CODE_VALIDATION_ERROR = "VALIDATION_ERROR"
        private const val CODE_INVALID_REQUEST = "INVALID_REQUEST"
        private const val CODE_MISSING_PARAMETER = "MISSING_PARAMETER"
        private const val CODE_MISSING_HEADER = "MISSING_HEADER"
        private const val CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND"
        private const val CODE_METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED"
        private const val CODE_NOT_ACCEPTABLE = "NOT_ACCEPTABLE"
        private const val CODE_DATA_CONFLICT = "DATA_CONFLICT"
        private const val CODE_PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
        private const val CODE_UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE"
        private const val CODE_BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION"
        private const val CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
        private const val CODE_INTERNAL_ERROR = "INTERNAL_ERROR"

        private const val MSG_UNAUTHORIZED = "Debe iniciar sesión para acceder a este recurso."
        private const val MSG_FORBIDDEN = "No tiene permisos para realizar esta operación."
        private const val MSG_VALIDATION_GENERIC = "Los datos enviados no cumplen con las validaciones requeridas."
        private const val MSG_INVALID_REQUEST = "Formato de solicitud o parámetro inválido."
        private const val MSG_INVALID_BODY = "El cuerpo de la solicitud tiene un formato JSON inválido."
        private const val MSG_MISSING_PARAMETER = "Falta un parámetro obligatorio en la solicitud."
        private const val MSG_MISSING_HEADER = "Falta un encabezado obligatorio en la solicitud."
        private const val MSG_RESOURCE_NOT_FOUND = "El recurso solicitado no existe."
        private const val MSG_METHOD_NOT_ALLOWED = "El método HTTP utilizado no está permitido para esta ruta."
        private const val MSG_NOT_ACCEPTABLE = "El formato de respuesta solicitado no es compatible."
        private const val MSG_DATA_CONFLICT = "No se pudo completar la operación porque el registro entra en conflicto con datos existentes."
        private const val MSG_PAYLOAD_TOO_LARGE = "El archivo o solicitud excede el tamaño máximo permitido."
        private const val MSG_UNSUPPORTED_MEDIA_TYPE = "El tipo de contenido enviado no es compatible."
        private const val MSG_BUSINESS_RULE_VIOLATION = "No se pudo procesar la operación por una regla de negocio."
        private const val MSG_SERVICE_UNAVAILABLE = "El servicio no está disponible temporalmente. Intente de nuevo más tarde."
        private const val MSG_INTERNAL_ERROR = "Ocurrió un error interno en el servidor. Intente de nuevo más tarde."
    }
}
