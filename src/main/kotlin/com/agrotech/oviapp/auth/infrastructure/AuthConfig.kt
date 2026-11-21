package com.agrotech.oviapp.auth.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AuthConfig {
    @Bean
    fun systemClock(): Clock = Clock.systemUTC()
}
