package com.agrotech.oviapp.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RanchAuthorizationTest {

    @Test
    fun `RanchPermission exposes the four canonical JSONB permission keys`() {
        assertEquals(
            mapOf(
                RanchPermission.LECTURA to "lectura",
                RanchPermission.CAPTURA to "captura",
                RanchPermission.EDICION to "edicion",
                RanchPermission.EXPORTACION to "exportacion",
            ),
            RanchPermission.entries.associateWith { it.jsonKey },
        )
    }

    @Test
    fun `OWNER, ASSOCIATION_SUPERUSER, and VET_TECHNICIAN always have all ranch permissions`() {
        val fullAccessRoles = listOf(
            Role.OWNER,
            Role.ASSOCIATION_SUPERUSER,
            Role.VET_TECHNICIAN,
        )
        val restrictiveMap = mapOf(
            "lectura" to false,
            "captura" to false,
            "edicion" to false,
            "exportacion" to false,
        )

        fullAccessRoles.forEach { role ->
            val emptyAccess = RanchAccess(role = role, permissions = emptyMap())
            val restrictiveAccess = RanchAccess(role = role, permissions = restrictiveMap)

            RanchPermission.entries.forEach { permission ->
                assertTrue(emptyAccess.hasPermission(permission), "$role with empty map must have $permission")
                assertTrue(restrictiveAccess.hasPermission(permission), "$role must ignore restrictive JSONB for $permission")
            }
        }
    }

    @Test
    fun `WORKER_BRANCH only has permissions explicitly set to boolean true`() {
        RanchPermission.entries.forEach { granted ->
            val access = RanchAccess(
                role = Role.WORKER_BRANCH,
                permissions = mapOf(granted.jsonKey to true),
            )

            assertTrue(access.hasPermission(granted), "WORKER_BRANCH must have granted $granted")
            RanchPermission.entries.filter { it != granted }.forEach { other ->
                assertFalse(access.hasPermission(other), "WORKER_BRANCH must not have ungranted $other")
            }
        }
    }

    @Test
    fun `WORKER_BRANCH denies missing, false, null, and non-boolean permission values`() {
        val malformed = RanchAccess(
            role = Role.WORKER_BRANCH,
            permissions = mapOf(
                "lectura" to false,
                "captura" to null,
                "edicion" to "true",
                "exportacion" to 1,
            ),
        )
        val empty = RanchAccess(role = Role.WORKER_BRANCH, permissions = emptyMap())

        RanchPermission.entries.forEach { permission ->
            assertFalse(malformed.hasPermission(permission), "Malformed value for $permission must be denied")
            assertFalse(empty.hasPermission(permission), "Missing value for $permission must be denied")
        }
    }
}
