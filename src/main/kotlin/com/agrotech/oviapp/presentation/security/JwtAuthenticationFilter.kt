package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenPort: JwtTokenPort,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            val token = header.substring(BEARER_PREFIX.length).trim()
            val claims = if (token.isNotEmpty()) jwtTokenPort.verifyAccessToken(token) else null
            if (claims != null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}"))
                val auth = UsernamePasswordAuthenticationToken(claims.username, null, authorities)
                auth.details = claims
                SecurityContextHolder.getContext().authentication = auth
            } else {
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
