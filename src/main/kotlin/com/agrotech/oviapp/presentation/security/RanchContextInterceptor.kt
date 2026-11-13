package com.agrotech.oviapp.presentation.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class RanchContextInterceptor : HandlerInterceptor {
    companion object {
        const val HEADER_ACTIVE_RANCH = "X-Active-Ranch-Id"
        private val activeRanchHolder = ThreadLocal<String?>()
        fun currentRanchId(): String? = activeRanchHolder.get()
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        activeRanchHolder.set(request.getHeader(HEADER_ACTIVE_RANCH))
        return true
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        activeRanchHolder.remove()
    }
}
