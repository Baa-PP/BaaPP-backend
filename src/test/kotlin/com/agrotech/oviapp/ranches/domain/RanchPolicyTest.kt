package com.agrotech.oviapp.ranches.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.model.Role
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class RanchPolicyTest {

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `accepts 60, 75, and 90 weaning target days and rejects other values`() {
        RanchPolicy.validateWeaningTargetDays(60)
        RanchPolicy.validateWeaningTargetDays(75)
        RanchPolicy.validateWeaningTargetDays(90)

        listOf(0, 45, 70, 120).forEach { invalid ->
            assertThrows(DomainValidationException::class.java) {
                RanchPolicy.validateWeaningTargetDays(invalid)
            }
            assertThrows(DomainValidationException::class.java) {
                RanchProfile(
                    id = ranchA,
                    name = "Rancho",
                    address = "Dir",
                    owner = "Dueño",
                    weaningTargetDays = invalid,
                )
            }
        }
    }

    @Test
    fun `validateWorkerPassword accepts up to 72 UTF-8 bytes and rejects over 72 bytes`() {
        RanchPolicy.validateWorkerPassword("a".repeat(72))
        RanchPolicy.validateWorkerPassword("á".repeat(36)) // 36 * 2 = 72 UTF-8 bytes

        assertThrows(DomainValidationException::class.java) {
            RanchPolicy.validateWorkerPassword("a".repeat(73))
        }
        assertThrows(DomainValidationException::class.java) {
            RanchPolicy.validateWorkerPassword("á".repeat(37)) // 74 UTF-8 bytes
        }
    }

    @Test
    fun `requireCanEditRanch allows OWNER or affiliated association roles and blocks WORKER_BRANCH`() {
        RanchPolicy.requireCanEditRanch(actor(Role.OWNER, ranchA), ranchA, isAffiliated = false)
        RanchPolicy.requireCanEditRanch(actor(Role.VET_TECHNICIAN, null), ranchA, isAffiliated = true)
        RanchPolicy.requireCanEditRanch(actor(Role.ASSOCIATION_SUPERUSER, null), ranchA, isAffiliated = true)

        assertThrows(ForbiddenAccessException::class.java) {
            RanchPolicy.requireCanEditRanch(actor(Role.WORKER_BRANCH, ranchA), ranchA, isAffiliated = false)
        }
        assertThrows(ForbiddenAccessException::class.java) {
            RanchPolicy.requireCanEditRanch(actor(Role.OWNER, ranchB), ranchA, isAffiliated = false)
        }
        assertThrows(ForbiddenAccessException::class.java) {
            RanchPolicy.requireCanEditRanch(actor(Role.VET_TECHNICIAN, null), ranchA, isAffiliated = false)
        }
        assertThrows(ForbiddenAccessException::class.java) {
            RanchPolicy.requireCanEditRanch(actor(Role.OWNER, ranchA, active = false), ranchA, isAffiliated = false)
        }
    }

    @Test
    fun `requireCanManageWorkers allows only active OWNER of target ranch`() {
        RanchPolicy.requireCanManageWorkers(actor(Role.OWNER, ranchA), ranchA)

        listOf(
            actor(Role.OWNER, ranchB),
            actor(Role.OWNER, ranchA, active = false),
            actor(Role.WORKER_BRANCH, ranchA),
            actor(Role.VET_TECHNICIAN, ranchA),
            actor(Role.ASSOCIATION_SUPERUSER, ranchA),
        ).forEach { forbiddenActor ->
            assertThrows(ForbiddenAccessException::class.java) {
                RanchPolicy.requireCanManageWorkers(forbiddenActor, ranchA)
            }
        }
    }

    private fun actor(role: Role, ranchId: UUID?, active: Boolean = true) = RanchActor(
        userId = UUID.randomUUID(),
        username = "user",
        role = role,
        ranchId = ranchId,
        associationId = UUID.randomUUID(),
        active = active,
    )
}
