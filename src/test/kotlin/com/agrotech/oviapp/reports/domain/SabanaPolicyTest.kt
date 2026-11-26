package com.agrotech.oviapp.reports.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.Sex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SabanaPolicyTest {

    @Test
    fun `SabanaColumnsSpec defines exactly 24 columns from A to X`() {
        assertEquals(24, SabanaColumnsSpec.COLUMN_COUNT)
        assertEquals(24, SabanaColumnsSpec.HEADERS.size)
        assertEquals("folio", SabanaColumnsSpec.HEADERS.first())
        assertEquals("FECHA CORTE", SabanaColumnsSpec.HEADERS.last())
    }

    @Test
    fun `resolveFormat honors explicit format param, Accept header, and rejects unknown formats`() {
        assertEquals(SabanaExportFormat.JSON, SabanaPolicy.resolveFormat(null, "application/json"))
        assertEquals(SabanaExportFormat.JSON, SabanaPolicy.resolveFormat("json", SabanaColumnsSpec.XLSX_MEDIA_TYPE))
        assertEquals(SabanaExportFormat.XLSX, SabanaPolicy.resolveFormat("xlsx", "application/json"))
        assertEquals(SabanaExportFormat.XLSX, SabanaPolicy.resolveFormat("EXCEL", null))
        assertEquals(
            SabanaExportFormat.XLSX,
            SabanaPolicy.resolveFormat(null, SabanaColumnsSpec.XLSX_MEDIA_TYPE),
        )
        assertEquals(
            SabanaExportFormat.JSON,
            SabanaPolicy.resolveFormat(null, "${SabanaColumnsSpec.XLSX_MEDIA_TYPE};q=0, application/json"),
        )
        assertEquals(
            SabanaExportFormat.JSON,
            SabanaPolicy.resolveFormat(null, "application/json;q=1.0, ${SabanaColumnsSpec.XLSX_MEDIA_TYPE};q=0.5"),
        )
        assertEquals(
            SabanaExportFormat.XLSX,
            SabanaPolicy.resolveFormat(null, "${SabanaColumnsSpec.XLSX_MEDIA_TYPE};q=0.9, application/json;q=0.5"),
        )
        assertEquals(
            SabanaExportFormat.JSON,
            SabanaPolicy.resolveFormat(null, "*/*"),
        )
        assertEquals(
            SabanaExportFormat.JSON,
            SabanaPolicy.resolveFormat(null, "application/*"),
        )
        assertEquals(
            SabanaExportFormat.XLSX,
            SabanaPolicy.resolveFormat(null, "application/json;q=0, application/*"),
        )
        assertEquals(
            SabanaExportFormat.XLSX,
            SabanaPolicy.resolveFormat(null, "application/json;q=0, */*"),
        )
        assertEquals(
            SabanaExportFormat.JSON,
            SabanaPolicy.resolveFormat(null, "application/json;q=0"),
        )

        val ex = assertThrows(DomainValidationException::class.java) {
            SabanaPolicy.resolveFormat("csv", null)
        }
        assertEquals("INVALID_EXPORT_FORMAT", ex.code)
    }

    @Test
    fun `validatePagination enforces bounds`() {
        SabanaPolicy.validatePagination(page = 0, size = 1)
        SabanaPolicy.validatePagination(page = 3, size = 500)

        assertThrows(DomainValidationException::class.java) {
            SabanaPolicy.validatePagination(page = -1, size = 100)
        }
        assertThrows(DomainValidationException::class.java) {
            SabanaPolicy.validatePagination(page = 0, size = 0)
        }
        assertThrows(DomainValidationException::class.java) {
            SabanaPolicy.validatePagination(page = 0, size = 501)
        }
    }

    @Test
    fun `toSabanaSexFilter and toContainsLikePattern normalize inputs safely`() {
        assertEquals("HEMBRA", SabanaPolicy.toSabanaSexFilter(Sex.FEMALE))
        assertEquals("MACHO", SabanaPolicy.toSabanaSexFilter(Sex.MALE))
        assertNull(SabanaPolicy.toSabanaSexFilter(null))

        assertNull(SabanaPolicy.toContainsLikePattern("   "))
        assertEquals("%100!%!_!!dorper%", SabanaPolicy.toContainsLikePattern(" 100%_!dorper "))
    }
}
