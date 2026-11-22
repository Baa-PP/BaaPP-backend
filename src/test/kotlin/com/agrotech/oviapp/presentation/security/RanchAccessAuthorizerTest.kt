package com.agrotech.oviapp.presentation.security

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

class RanchAccessAuthorizerTest {

    private val ranchMembershipPort = mock(RanchMembershipPort::class.java)
    private val authorizer = RanchAccessAuthorizer(ranchMembershipPort)
    private val interceptor = RanchContextInterceptor(ranchMembershipPort)

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ownerAuth = TestingAuthenticationToken("owner_1", null, "ROLE_OWNER")
    private val workerAuth = TestingAuthenticationToken("worker_1", null, "ROLE_WORKER_BRANCH")

    @AfterEach
    fun tearDown() {
        val req = mock(HttpServletRequest::class.java)
        val res = mock(HttpServletResponse::class.java)
        interceptor.afterCompletion(req, res, Any(), null)
    }

    @Test
    fun `reuses verified context from RanchContextInterceptor without querying RanchMembershipPort twice`() {
        val req = mock(HttpServletRequest::class.java)
        val res = mock(HttpServletResponse::class.java)
        given(req.getHeader(RanchContextInterceptor.HEADER_ACTIVE_RANCH)).willReturn(ranchId.toString())
        given(req.userPrincipal).willReturn(ownerAuth)
        given(ranchMembershipPort.resolveAccess("owner_1", ranchId)).willReturn(RanchAccess(Role.OWNER))

        interceptor.preHandle(req, res, Any())
        val authorizedRanch = authorizer.requireAuthorizedRanchId(ownerAuth)

        assertEquals(ranchId, authorizedRanch)
        verify(ranchMembershipPort, times(1)).resolveAccess("owner_1", ranchId)
    }

    @Test
    fun `enforces optional requiredPermission for WORKER_BRANCH`() {
        val req = mock(HttpServletRequest::class.java)
        val res = mock(HttpServletResponse::class.java)
        given(req.getHeader(RanchContextInterceptor.HEADER_ACTIVE_RANCH)).willReturn(ranchId.toString())
        given(req.userPrincipal).willReturn(workerAuth)
        given(ranchMembershipPort.resolveAccess("worker_1", ranchId)).willReturn(
            RanchAccess(Role.WORKER_BRANCH, mapOf("lectura" to true, "edicion" to false)),
        )

        interceptor.preHandle(req, res, Any())

        assertEquals(ranchId, authorizer.requireAuthorizedRanchId(workerAuth, RanchPermission.LECTURA))
        val ex = assertThrows(ForbiddenAccessException::class.java) {
            authorizer.requireAuthorizedRanchId(workerAuth, RanchPermission.EDICION)
        }
        assertEquals("INSUFFICIENT_PERMISSION", ex.code)
        verify(ranchMembershipPort, times(1)).resolveAccess("worker_1", ranchId)
    }

    @Test
    fun `rejects missing header, invalid UUID, and anonymous authentication`() {
        val missingEx = assertThrows(DomainValidationException::class.java) {
            authorizer.requireAuthorizedRanchId(ownerAuth)
        }
        assertEquals("MISSING_RANCH_CONTEXT", missingEx.code)

        val req = mock(HttpServletRequest::class.java)
        val res = mock(HttpServletResponse::class.java)
        given(req.getHeader(RanchContextInterceptor.HEADER_ACTIVE_RANCH)).willReturn("not-a-uuid")
        interceptor.preHandle(req, res, Any())

        val invalidEx = assertThrows(DomainValidationException::class.java) {
            authorizer.requireAuthorizedRanchId(ownerAuth)
        }
        assertEquals("INVALID_RANCH_CONTEXT", invalidEx.code)

        given(req.getHeader(RanchContextInterceptor.HEADER_ACTIVE_RANCH)).willReturn(ranchId.toString())
        interceptor.preHandle(req, res, Any())

        val anonymous = AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        val anonEx = assertThrows(ForbiddenAccessException::class.java) {
            authorizer.requireAuthorizedRanchId(anonymous)
        }
        assertEquals("RANCH_ACCESS_DENIED", anonEx.code)
        verifyNoInteractions(ranchMembershipPort)
    }

    @Test
    fun `interceptor fails closed with 403 when RanchMembershipPort is absent and X-Active-Ranch-Id is supplied`() {
        val unbackedInterceptor = RanchContextInterceptor(ranchMembershipPort = null)
        val req = mock(HttpServletRequest::class.java)
        val res = mock(HttpServletResponse::class.java)
        given(req.getHeader(RanchContextInterceptor.HEADER_ACTIVE_RANCH)).willReturn(ranchId.toString())
        given(req.userPrincipal).willReturn(ownerAuth)

        val ex = assertThrows(ForbiddenAccessException::class.java) {
            unbackedInterceptor.preHandle(req, res, Any())
        }
        assertEquals("RANCH_ACCESS_DENIED", ex.code)
    }
}
