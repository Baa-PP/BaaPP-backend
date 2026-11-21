package com.agrotech.oviapp.ranches.application

import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ForbiddenAccessException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.ranches.domain.RanchActor
import com.agrotech.oviapp.ranches.domain.RanchActorPort
import com.agrotech.oviapp.ranches.domain.RanchProfile
import com.agrotech.oviapp.ranches.domain.RanchRepositoryPort
import com.agrotech.oviapp.ranches.domain.RanchWorker
import com.agrotech.oviapp.ranches.domain.RanchWorkerRepositoryPort
import com.agrotech.oviapp.ranches.domain.WorkerPasswordEncoderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RanchAndWorkerUseCasesTest {

    private val ranchA = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val ranchB = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val associationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val fixedNow = Instant.parse("2026-11-21T01:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `ListAccessibleRanchesUseCase returns only permitted ranches with lastSyncedAt`() {
        val syncTime = Instant.parse("2026-11-20T18:30:00Z")
        val owner = RanchActor(UUID.randomUUID(), "owner_a", Role.OWNER, ranchA, null, active = true)
        val superuser = RanchActor(UUID.randomUUID(), "assoc_admin", Role.ASSOCIATION_SUPERUSER, null, associationId, active = true)
        val ranchRepo = FakeRanchRepositoryPort(
            ranches = listOf(
                RanchProfile(ranchA, "Rancho A", "Dir A", "Juan", 75, syncTime),
                RanchProfile(ranchB, "Rancho B", "Dir B", "María", 60, null),
            ),
            affiliations = mapOf(associationId to setOf(ranchA, ranchB)),
        )
        val useCase = ListAccessibleRanchesUseCase(FakeRanchActorPort(owner, superuser), ranchRepo)

        val ownerRanches = useCase.execute("owner_a")
        assertEquals(1, ownerRanches.size)
        assertEquals(ranchA, ownerRanches.first().id)
        assertEquals(syncTime, ownerRanches.first().lastSyncedAt)

        val assocRanches = useCase.execute("assoc_admin")
        assertEquals(listOf(ranchA, ranchB), assocRanches.map { it.id })
    }

    @Test
    fun `ListAccessibleRanchesUseCase blocks inactive or unknown principal`() {
        val inactive = RanchActor(UUID.randomUUID(), "inactive_owner", Role.OWNER, ranchA, null, active = false)
        val useCase = ListAccessibleRanchesUseCase(FakeRanchActorPort(inactive), FakeRanchRepositoryPort())

        assertThrows(ForbiddenAccessException::class.java) { useCase.execute("inactive_owner") }
        assertThrows(ForbiddenAccessException::class.java) { useCase.execute("ghost") }
    }

    @Test
    fun `UpdateRanchProfileUseCase updates ranch for OWNER or association and blocks WORKER_BRANCH`() {
        val owner = RanchActor(UUID.randomUUID(), "owner_a", Role.OWNER, ranchA, null, active = true)
        val worker = RanchActor(UUID.randomUUID(), "worker_a", Role.WORKER_BRANCH, ranchA, null, active = true)
        val ranchRepo = FakeRanchRepositoryPort(
            ranches = listOf(RanchProfile(ranchA, "Viejo", "Dir vieja", "Dueño viejo", 75, null)),
        )
        val useCase = UpdateRanchProfileUseCase(FakeRanchActorPort(owner, worker), ranchRepo)

        val updated = useCase.execute(
            UpdateRanchCommand(
                principalIdentifier = "owner_a",
                ranchId = ranchA,
                name = "Rancho Nuevo",
                address = "Km 15 Carretera Sur",
                owner = "Carlos Mendoza",
                weaningTargetDays = 90,
            ),
        )

        assertEquals("Rancho Nuevo", updated.name)
        assertEquals(90, updated.weaningTargetDays)

        assertThrows(ForbiddenAccessException::class.java) {
            useCase.execute(UpdateRanchCommand("worker_a", ranchA, "X", "Y", "Z", 75))
        }
        assertThrows(DomainValidationException::class.java) {
            useCase.execute(UpdateRanchCommand("owner_a", ranchA, "X", "Y", "Z", 55))
        }
        assertThrows(ResourceNotFoundException::class.java) {
            val superuser = RanchActor(UUID.randomUUID(), "assoc", Role.ASSOCIATION_SUPERUSER, null, associationId, true)
            val missingRanchId = UUID.randomUUID()
            UpdateRanchProfileUseCase(
                FakeRanchActorPort(superuser),
                FakeRanchRepositoryPort(affiliations = mapOf(associationId to setOf(missingRanchId))),
            ).execute(UpdateRanchCommand("assoc", missingRanchId, "X", "Y", "Z", 75))
        }
    }

    @Test
    fun `CreateRanchWorkerUseCase hashes password, creates WORKER_BRANCH and rejects duplicates or non-OWNER`() {
        val owner = RanchActor(UUID.randomUUID(), "owner_a", Role.OWNER, ranchA, null, active = true)
        val assoc = RanchActor(UUID.randomUUID(), "assoc", Role.ASSOCIATION_SUPERUSER, null, associationId, active = true)
        val ranchRepo = FakeRanchRepositoryPort(
            ranches = listOf(RanchProfile(ranchA, "Rancho A", "Dir A", "Juan", 75, null)),
        )
        val workerRepo = FakeRanchWorkerRepositoryPort()
        val useCase = CreateRanchWorkerUseCase(
            actorPort = FakeRanchActorPort(owner, assoc),
            ranchPort = ranchRepo,
            workerPort = workerRepo,
            passwordEncoder = FakeWorkerPasswordEncoder(),
            clock = clock,
        )

        val created = useCase.execute(
            CreateRanchWorkerCommand(
                principalIdentifier = "owner_a",
                ranchId = ranchA,
                username = "  capataz_1 ",
                rawPassword = "Password123!",
                permissions = mapOf("read_inventory" to true, "capture_weights" to true),
            ),
        )

        assertEquals("capataz_1", created.username)
        assertTrue(created.active)
        assertEquals("bcrypt:Password123!", workerRepo.hashesById[created.id])
        assertEquals(mapOf("read_inventory" to true, "capture_weights" to true), created.permissions)

        assertThrows(BusinessConflictException::class.java) {
            useCase.execute(CreateRanchWorkerCommand("owner_a", ranchA, "capataz_1", "Password123!", emptyMap()))
        }
        assertThrows(DomainValidationException::class.java) {
            useCase.execute(CreateRanchWorkerCommand("owner_a", ranchA, "capataz_3", "á".repeat(37), emptyMap()))
        }
        assertFalse(workerRepo.existsByUsername("capataz_3"))
        assertThrows(ForbiddenAccessException::class.java) {
            useCase.execute(CreateRanchWorkerCommand("assoc", ranchA, "capataz_2", "Password123!", emptyMap()))
        }
    }

    @Test
    fun `UpdateRanchWorkerUseCase updates permissions and revokes refresh tokens when deactivated`() {
        val owner = RanchActor(UUID.randomUUID(), "owner_a", Role.OWNER, ranchA, null, active = true)
        val workerId = UUID.randomUUID()
        val worker = RanchWorker(
            id = workerId,
            ranchId = ranchA,
            username = "capataz_1",
            active = true,
            permissions = mapOf("read_inventory" to true),
            createdAt = fixedNow,
        )
        val workerRepo = FakeRanchWorkerRepositoryPort(worker)
        val useCase = UpdateRanchWorkerUseCase(FakeRanchActorPort(owner), workerRepo, clock)

        val updated = useCase.execute(
            UpdateRanchWorkerCommand(
                principalIdentifier = "owner_a",
                ranchId = ranchA,
                workerId = workerId,
                active = false,
                permissions = mapOf("read_inventory" to true, "export" to false),
            ),
        )

        assertTrue(workerRepo.lockedIds.contains(workerId))
        assertFalse(updated.active)
        assertEquals(mapOf("read_inventory" to true, "export" to false), updated.permissions)
        assertTrue(workerRepo.revokedTokensForUsers.contains(workerId))

        assertThrows(DomainValidationException::class.java) {
            useCase.execute(UpdateRanchWorkerCommand("owner_a", ranchA, workerId, active = null, permissions = null))
        }
        assertThrows(ResourceNotFoundException::class.java) {
            useCase.execute(UpdateRanchWorkerCommand("owner_a", ranchA, UUID.randomUUID(), active = true, permissions = null))
        }
    }

    @Test
    fun `UpdateRanchWorkerUseCase permissions-only update does not restore concurrent deactivation`() {
        val owner = RanchActor(UUID.randomUUID(), "owner_a", Role.OWNER, ranchA, null, active = true)
        val workerId = UUID.randomUUID()
        val worker = RanchWorker(
            id = workerId,
            ranchId = ranchA,
            username = "capataz_1",
            active = true,
            permissions = mapOf("read_inventory" to true),
            createdAt = fixedNow,
        )
        val workerRepo = FakeRanchWorkerRepositoryPort(worker).apply {
            onBeforeUpdate = { id ->
                // Simulate concurrent deactivation occurring between read and write
                deactivateInStore(id)
            }
        }
        val useCase = UpdateRanchWorkerUseCase(FakeRanchActorPort(owner), workerRepo, clock)

        val updated = useCase.execute(
            UpdateRanchWorkerCommand(
                principalIdentifier = "owner_a",
                ranchId = ranchA,
                workerId = workerId,
                active = null,
                permissions = mapOf("read_inventory" to false),
            ),
        )

        assertFalse(updated.active)
        assertFalse(workerRepo.findByIdAndRanchId(workerId, ranchA)!!.active)
        assertEquals(mapOf("read_inventory" to false), updated.permissions)
    }

    private class FakeRanchActorPort(vararg actors: RanchActor) : RanchActorPort {
        private val byName = actors.associateBy { it.username }
        override fun findByPrincipalIdentifier(principalIdentifier: String): RanchActor? = byName[principalIdentifier]
    }

    private class FakeRanchRepositoryPort(
        ranches: List<RanchProfile> = emptyList(),
        private val affiliations: Map<UUID, Set<UUID>> = emptyMap(),
    ) : RanchRepositoryPort {
        private val store = ranches.associateBy { it.id }.toMutableMap()

        override fun findById(ranchId: UUID): RanchProfile? = store[ranchId]

        override fun findByAssociationId(associationId: UUID): List<RanchProfile> =
            affiliations[associationId].orEmpty().mapNotNull { store[it] }.sortedBy { it.name }

        override fun isAffiliatedWithAssociation(associationId: UUID, ranchId: UUID): Boolean =
            affiliations[associationId]?.contains(ranchId) == true

        override fun save(ranch: RanchProfile): RanchProfile {
            store[ranch.id] = ranch
            return ranch
        }
    }

    private class FakeRanchWorkerRepositoryPort(vararg initial: RanchWorker) : RanchWorkerRepositoryPort {
        private val store = initial.associateBy { it.id }.toMutableMap()
        val hashesById = mutableMapOf<UUID, String>()
        val revokedTokensForUsers = mutableListOf<UUID>()
        val lockedIds = mutableSetOf<UUID>()
        var onBeforeUpdate: ((UUID) -> Unit)? = null

        fun deactivateInStore(workerId: UUID) {
            store[workerId]?.let { store[workerId] = it.copy(active = false) }
        }

        override fun findAllByRanchId(ranchId: UUID): List<RanchWorker> =
            store.values.filter { it.ranchId == ranchId }.sortedBy { it.username }

        override fun findByIdAndRanchId(workerId: UUID, ranchId: UUID): RanchWorker? =
            store[workerId]?.takeIf { it.ranchId == ranchId }

        override fun findByIdAndRanchIdForUpdate(workerId: UUID, ranchId: UUID): RanchWorker? {
            lockedIds += workerId
            return findByIdAndRanchId(workerId, ranchId)
        }

        override fun existsByUsername(username: String): Boolean =
            store.values.any { it.username == username }

        override fun create(worker: RanchWorker, passwordHash: String): RanchWorker {
            store[worker.id] = worker
            hashesById[worker.id] = passwordHash
            return worker
        }

        override fun updateWorker(
            workerId: UUID,
            ranchId: UUID,
            active: Boolean?,
            permissions: Map<String, Any?>?,
        ): RanchWorker? {
            onBeforeUpdate?.invoke(workerId)
            val current = store[workerId]?.takeIf { it.ranchId == ranchId } ?: return null
            val next = current.copy(
                active = active ?: current.active,
                permissions = permissions ?: current.permissions,
            )
            store[workerId] = next
            return next
        }

        override fun revokeActiveRefreshTokens(workerId: UUID, revokedAt: Instant) {
            revokedTokensForUsers += workerId
        }
    }

    private class FakeWorkerPasswordEncoder : WorkerPasswordEncoderPort {
        override fun encode(rawPassword: String): String = "bcrypt:$rawPassword"
    }
}
