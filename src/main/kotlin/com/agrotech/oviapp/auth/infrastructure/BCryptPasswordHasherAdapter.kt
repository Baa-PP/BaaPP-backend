package com.agrotech.oviapp.auth.infrastructure

import com.agrotech.oviapp.auth.domain.PasswordHasherPort
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasherAdapter : PasswordHasherPort {
    private val encoder = BCryptPasswordEncoder()

    override fun matches(rawPassword: String, encodedHash: String): Boolean =
        runCatching { encoder.matches(rawPassword, encodedHash) }.getOrDefault(false)
}
