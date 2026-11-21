package com.agrotech.oviapp.weights.presentation

import com.agrotech.oviapp.infrastructure.persistence.AsociacionRanchoJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.RanchMembershipJpaAdapter
import com.agrotech.oviapp.infrastructure.persistence.UsuarioJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.entity.UsuarioEntity
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.util.UUID

class RanchMembershipJpaAdapterTest {

    private val usuarioRepo = mock(UsuarioJpaRepository::class.java)
    private val asociacionRanchoRepo = mock(AsociacionRanchoJpaRepository::class.java)
    private val adapter = RanchMembershipJpaAdapter(usuarioRepo, asociacionRanchoRepo)

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val associationId = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `OWNER and WORKER_BRANCH only access their assigned ranch`() {
        val owner = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchA,
            usuario = "owner_a",
            contrasenaHash = "hash",
            rol = "OWNER",
        )
        given(usuarioRepo.findByUsuario("owner_a")).willReturn(owner)

        assertTrue(adapter.hasAccess("owner_a", ranchA))
        assertFalse(adapter.hasAccess("owner_a", ranchB))
    }

    @Test
    fun `VET_TECHNICIAN and ASSOCIATION_SUPERUSER only access ranches affiliated with their association`() {
        val vet = UsuarioEntity(
            id = UUID.randomUUID(),
            asociacionId = associationId,
            usuario = "vet_1",
            contrasenaHash = "hash",
            rol = "VET_TECHNICIAN",
        )
        given(usuarioRepo.findByUsuario("vet_1")).willReturn(vet)
        given(asociacionRanchoRepo.existsByIdAsociacionIdAndIdRanchoId(associationId, ranchA)).willReturn(true)
        given(asociacionRanchoRepo.existsByIdAsociacionIdAndIdRanchoId(associationId, ranchB)).willReturn(false)

        assertTrue(adapter.hasAccess("vet_1", ranchA))
        assertFalse(adapter.hasAccess("vet_1", ranchB))
    }

    @Test
    fun `unknown or inactive user is denied access`() {
        given(usuarioRepo.findByUsuario("ghost")).willReturn(null)
        assertFalse(adapter.hasAccess("ghost", ranchA))

        val inactiveOwner = UsuarioEntity(
            id = UUID.randomUUID(),
            ranchoId = ranchA,
            usuario = "disabled_worker",
            contrasenaHash = "hash",
            rol = "WORKER_BRANCH",
            activo = false,
        )
        given(usuarioRepo.findByUsuario("disabled_worker")).willReturn(inactiveOwner)
        assertFalse(adapter.hasAccess("disabled_worker", ranchA))
    }
}
