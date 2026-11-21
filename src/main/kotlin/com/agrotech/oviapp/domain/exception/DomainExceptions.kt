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
) : DomainException(code, message)

class ForbiddenAccessException(
    code: String,
    message: String,
) : DomainException(code, message)
