package com.agrotech.oviapp.weights.presentation

import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.infrastructure.persistence.AsociacionRanchoJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.RanchMembershipJpaAdapter
import com.agrotech.oviapp.infrastructure.persistence.UsuarioJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.entity.UsuarioEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.util.Optional
import java.util.UUID

class RanchMembershipJpaAdapterTest {

    private val usuarioRepo = mock(UsuarioJpaRepository::class.java)
    private val asociacionRanchoRepo = mock(AsociacionRanchoJpaRepository::class.java)
    private val adapter = RanchMembershipJpaAdapter(usuarioRepo, asociacionRanchoRepo)

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val associationId = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `OWNER and WORKER_BRANCH only access their assigned ranch and preserve JSONB permissions`() {
        val owner = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchA,
            usuario = "owner_a",
            contrasenaHash = "hash",
            rol = "OWNER",
        )
        val worker = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchA,
            usuario = "worker_a",
            contrasenaHash = "hash",
            rol = "WORKER_BRANCH",
            permisos = mapOf("lectura" to true, "captura" to false),
        )
        given(usuarioRepo.findByUsuario("owner_a")).willReturn(owner)
        given(usuarioRepo.findByUsuario("worker_a")).willReturn(worker)

        val ownerAccess = adapter.resolveAccess("owner_a", ranchA)
        assertEquals(RanchAccess(Role.OWNER, emptyMap()), ownerAccess)
        assertTrue(adapter.hasAccess("owner_a", ranchA))
        assertNull(adapter.resolveAccess("owner_a", ranchB))
        assertFalse(adapter.hasAccess("owner_a", ranchB))

        val workerAccess = adapter.resolveAccess("worker_a", ranchA)
        assertNotNull(workerAccess)
        assertEquals(Role.WORKER_BRANCH, workerAccess?.role)
        assertTrue(workerAccess?.hasPermission(RanchPermission.LECTURA) == true)
        assertFalse(workerAccess?.hasPermission(RanchPermission.CAPTURA) == true)
        assertTrue(adapter.hasAccess("worker_a", ranchA))
        assertNull(adapter.resolveAccess("worker_a", ranchB))
        assertFalse(adapter.hasAccess("worker_a", ranchB))
    }

    @Test
    fun `VET_TECHNICIAN and ASSOCIATION_SUPERUSER strictly require asociaciones_ranchos affiliation`() {
        val vet = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchB, // Even if legacy ranchoId matches ranchB, unaffiliated ranch MUST be denied
            asociacionId = associationId,
            usuario = "vet_1",
            contrasenaHash = "hash",
            rol = "VET_TECHNICIAN",
        )
        val superuser = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchB, // Unaffiliated ranch MUST be denied even when ranchoId matches
            asociacionId = associationId,
            usuario = "assoc_admin",
            contrasenaHash = "hash",
            rol = "ASSOCIATION_SUPERUSER",
        )
        val orphanVet = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchA,
            asociacionId = null,
            usuario = "vet_no_assoc",
            contrasenaHash = "hash",
            rol = "VET_TECHNICIAN",
        )
        given(usuarioRepo.findByUsuario("vet_1")).willReturn(vet)
        given(usuarioRepo.findByUsuario("assoc_admin")).willReturn(superuser)
        given(usuarioRepo.findByUsuario("vet_no_assoc")).willReturn(orphanVet)
        given(asociacionRanchoRepo.existsByIdAsociacionIdAndIdRanchoId(associationId, ranchA)).willReturn(true)
        given(asociacionRanchoRepo.existsByIdAsociacionIdAndIdRanchoId(associationId, ranchB)).willReturn(false)

        assertEquals(Role.VET_TECHNICIAN, adapter.resolveAccess("vet_1", ranchA)?.role)
        assertTrue(adapter.hasAccess("vet_1", ranchA))
        assertNull(adapter.resolveAccess("vet_1", ranchB))
        assertFalse(adapter.hasAccess("vet_1", ranchB))

        assertEquals(Role.ASSOCIATION_SUPERUSER, adapter.resolveAccess("assoc_admin", ranchA)?.role)
        assertTrue(adapter.hasAccess("assoc_admin", ranchA))
        assertNull(adapter.resolveAccess("assoc_admin", ranchB))
        assertFalse(adapter.hasAccess("assoc_admin", ranchB))

        assertNull(adapter.resolveAccess("vet_no_assoc", ranchA))
        assertFalse(adapter.hasAccess("vet_no_assoc", ranchA))
    }

    @Test
    fun `unknown, blank, invalid role, or inactive user is denied access`() {
        given(usuarioRepo.findByUsuario("ghost")).willReturn(null)
        assertNull(adapter.resolveAccess("ghost", ranchA))
        assertFalse(adapter.hasAccess("ghost", ranchA))

        assertNull(adapter.resolveAccess("   ", ranchA))

        val inactiveWorker = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchA,
            usuario = "disabled_worker",
            contrasenaHash = "hash",
            rol = "WORKER_BRANCH",
            permisos = mapOf("lectura" to true),
            activo = false,
        )
        val inactiveSuperuser = UsuarioEntity(
            id = UUID.randomUUID(),
            asociacionId = associationId,
            usuario = "disabled_assoc",
            contrasenaHash = "hash",
            rol = "ASSOCIATION_SUPERUSER",
            activo = false,
        )
        val unknownRoleUser = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchA,
            usuario = "weird_role",
            contrasenaHash = "hash",
            rol = "UNKNOWN_ROLE",
            activo = true,
        )
        given(usuarioRepo.findByUsuario("disabled_worker")).willReturn(inactiveWorker)
        given(usuarioRepo.findByUsuario("disabled_assoc")).willReturn(inactiveSuperuser)
        given(usuarioRepo.findByUsuario("weird_role")).willReturn(unknownRoleUser)

        assertNull(adapter.resolveAccess("disabled_worker", ranchA))
        assertFalse(adapter.hasAccess("disabled_worker", ranchA))
        assertNull(adapter.resolveAccess("disabled_assoc", ranchA))
        assertNull(adapter.resolveAccess("weird_role", ranchA))
        verifyNoInteractions(asociacionRanchoRepo)
    }

    @Test
    fun `supports looking up user by UUID principal identifier`() {
        val userId = UUID.randomUUID()
        val owner = UsuarioEntity(
            id = userId,
            ranchoId = ranchA,
            usuario = "owner_uuid",
            contrasenaHash = "hash",
            rol = "OWNER",
        )
        given(usuarioRepo.findByUsuario(userId.toString())).willReturn(null)
        given(usuarioRepo.findById(userId)).willReturn(Optional.of(owner))

        val access = adapter.resolveAccess(userId.toString(), ranchA)
        assertEquals(Role.OWNER, access?.role)
    }
}
