package com.agrotech.oviapp.domain.exception

sealed class DomainException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

class DomainValidationException(
    code: String,
    message: String,
) : DomainException(code, message)

class ResourceNotFoundException(
    code: String,
    message: String,
) : DomainException(code, message)

class BusinessConflictException(
    code: String,
    message: String,
) : DomainException(code, message) {
    companion object {
        const val CODE_FOLIO_DUPLICADO = "FOLIO_DUPLICADO"

        fun duplicateFolio(folio: String): BusinessConflictException =
            BusinessConflictException(
                code = CODE_FOLIO_DUPLICADO,
                message = "El folio '$folio' ya está registrado en este rancho.",
            )
    }
}

class BusinessRuleViolationException(
    code: String,
    message: String,
) : DomainException(code, message) {
    companion object {
        const val CODE_VENTANA_PARTO_CERRADA = "VENTANA_PARTO_CERRADA"

        fun birthRegistrationWindowClosed(daysSinceBirth: Long? = null): BusinessRuleViolationException {
            val elapsed = daysSinceBirth?.let { " ($it días)" }.orEmpty()
            return BusinessRuleViolationException(
                code = CODE_VENTANA_PARTO_CERRADA,
                message = "Ventana de 30 días cerrada$elapsed. Solicite el alta auditada a la Asociación.",
            )
        }
    }
}

class ForbiddenAccessException(
    code: String,
    message: String,
) : DomainException(code, message)

class InvalidCredentialsException(
    code: String,
    message: String,
) : DomainException(code, message)
