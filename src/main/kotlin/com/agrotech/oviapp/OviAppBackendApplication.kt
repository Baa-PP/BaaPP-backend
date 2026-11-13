package com.agrotech.oviapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class OviAppBackendApplication

fun main(args: Array<String>) {
    runApplication<OviAppBackendApplication>(*args)
}
