package com.agrotech.oviapp.auth.presentation

import com.agrotech.oviapp.auth.application.AuthResult
import com.agrotech.oviapp.auth.application.LoginCommand
import com.agrotech.oviapp.auth.application.LoginUseCase
import com.agrotech.oviapp.auth.application.RefreshResult
import com.agrotech.oviapp.auth.application.RefreshTokenUseCase
import com.agrotech.oviapp.auth.domain.RanchSummary
import com.agrotech.oviapp.domain.model.Role
import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class LoginRequest(
    @JsonAlias("usuario")
    @field:NotBlank
    val username: String = "",

    @JsonAlias("contrasena")
    @field:NotBlank
    val password: String = "",
)

data class RanchSummaryResponse(
    val id: UUID,
    val name: String,
    val owner: String,
    val weaningTargetDays: Int,
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val username: String,
    val role: Role,
    val ranchId: UUID?,
    val associationId: UUID?,
    val permissions: Map<String, Any?>,
    val ranches: List<RanchSummaryResponse>,
)

data class RefreshTokenRequest(
    @JsonAlias("refresh_token")
    @field:NotBlank
    val refreshToken: String = "",
)

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val loginUseCase: LoginUseCase,
    private val refreshTokenUseCase: RefreshTokenUseCase,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse =
        loginUseCase.execute(LoginCommand(username = request.username, password = request.password)).toResponse()

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): RefreshTokenResponse =
        refreshTokenUseCase.execute(request.refreshToken).toResponse()

    private fun AuthResult.toResponse() = LoginResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresInSeconds,
        username = username,
        role = role,
        ranchId = ranchId,
        associationId = associationId,
        permissions = permissions,
        ranches = ranches.map { it.toResponse() },
    )

    private fun RanchSummary.toResponse() = RanchSummaryResponse(
        id = id,
        name = name,
        owner = owner,
        weaningTargetDays = weaningTargetDays,
    )

    private fun RefreshResult.toResponse() = RefreshTokenResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresInSeconds,
    )
}
