package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.domain.model.RanchPermission

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireRanchPermission(
    val value: RanchPermission,
)
