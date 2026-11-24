package com.agrotech.oviapp.reports.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.Sex
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Representa una fila de la Sábana oficial de 24 columnas (A–X),
 * alineada 1:1 con `v_sabana` (V4__vistas.sql) y `SabanaColumns.ts`.
 */
data class SabanaRow(
    val folio: String,                           // A: folio
    val adn: String?,                            // B: adn
    val chipSiniiga: String?,                    // C: CHIP (484...)
    val fechaNacimiento: LocalDate,              // D: FECHA NACIMIENTO
    val fechaDestete: LocalDate?,                // E: FECHA DESTETE
    val edadMeses: BigDecimal,                   // F: EDAD MESES
    val edadDias: Int,                           // G: EDAD DIAS
    val pesoNacimientoKg: BigDecimal?,           // H: PESO NACIMIENTO
    val peso120DiasKg: BigDecimal?,              // I: PESO 120 DIAS
    val peso150DiasKg: BigDecimal?,              // J: PESO 150 DIAS
    val pesoEvaluacionKg: BigDecimal?,           // K: PESO A LA EVALUACION
    val circunferenciaEscrotalCm: BigDecimal?,   // L: CIRCUNFERENCIA (solo machos)
    val pesoDesteteKg: BigDecimal?,              // M: PESO DESTETE
    val tipoDestete: String?,                    // N: TIPO DESTETE
    val padreFolioInterno: String?,              // O: PADRE
    val folioPadreUno: String?,                  // P: FOLIO PADRE
    val madreFolioInterno: String?,              // Q: MADRE
    val folioMadreUno: String?,                  // R: FOLIO MADRE
    val raza: String,                            // S: RAZA
    val tipoParto: String,                       // T: TIPO DE PARTO
    val concepcion: String,                      // U: CONCEPCION (MN | IA | TE)
    val color: String,                           // V: COLOR
    val sexo: String,                            // W: SEXO (HEMBRA | MACHO)
    val fechaCorteIso: LocalDate,                // X: FECHA CORTE
)

data class SabanaQuery(
    val ranchId: UUID,
    val search: String? = null,
    val sex: Sex? = null,
    val breed: String? = null,
    val status: AnimalStatus? = null,
    val page: Int = 0,
    val size: Int = DEFAULT_PAGE_SIZE,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}

data class SabanaPage(
    val items: List<SabanaRow>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

enum class SabanaExportFormat {
    JSON,
    XLSX,
}

object SabanaColumnsSpec {
    const val COLUMN_COUNT = 24
    const val WORKSHEET_NAME = "SABANA"
    const val XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Encabezados exactos A–X compatibles con SABANA OCT 2026 DORPER.xlsx. */
    val HEADERS: List<String> = listOf(
        "folio",                 // A
        "adn",                   // B
        "CHIP",                  // C
        "FECHA NACIMIENTO",      // D
        "FECHA DESTETE",         // E
        "EDAD MESES",            // F
        "EDAD DIAS",             // G
        "PESO NACIMIENTO",       // H
        "PESO 120 DIAS",         // I
        "PESO 150 DIAS",         // J
        "PESO A LA EVALUACION",  // K
        "CIRCUNFERENCIA",        // L
        "PESO DESTETE",          // M
        "TIPO DESTETE",          // N
        "PADRE",                 // O
        "FOLIO PADRE",           // P
        "MADRE",                 // Q
        "FOLIO MADRE",           // R
        "RAZA",                  // S
        "TIPO DE PARTO",         // T
        "CONCEPCION",            // U
        "COLOR",                 // V
        "SEXO",                  // W
        "FECHA CORTE",           // X
    )
}

object SabanaPolicy {
    const val MIN_PAGE_SIZE = 1
    const val MAX_PAGE_SIZE = 500

    fun validatePagination(page: Int, size: Int) {
        if (page < 0 || size !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            throw DomainValidationException(
                code = "INVALID_PAGINATION",
                message = "Parámetros de paginación inválidos: page debe ser >= 0 y size entre $MIN_PAGE_SIZE y $MAX_PAGE_SIZE.",
            )
        }
    }

    fun resolveFormat(formatParam: String?, acceptHeader: String?): SabanaExportFormat {
        val normalizedParam = formatParam?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (normalizedParam != null) {
            return when (normalizedParam) {
                "json" -> SabanaExportFormat.JSON
                "xlsx", "excel" -> SabanaExportFormat.XLSX
                else -> throw DomainValidationException(
                    code = "INVALID_EXPORT_FORMAT",
                    message = "Formato de exportación no soportado: '$formatParam'. Use 'json' o 'xlsx'.",
                )
            }
        }
        val acceptsXlsx = acceptHeader?.contains(SabanaColumnsSpec.XLSX_MEDIA_TYPE, ignoreCase = true) == true
        return if (acceptsXlsx) SabanaExportFormat.XLSX else SabanaExportFormat.JSON
    }

    fun toSabanaSexFilter(sex: Sex?): String? =
        when (sex) {
            Sex.FEMALE -> "HEMBRA"
            Sex.MALE -> "MACHO"
            null -> null
        }

    fun toContainsLikePattern(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val escaped = trimmed
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
        return "%$escaped%"
    }
}

interface SabanaReadPort {
    fun findPage(query: SabanaQuery): SabanaPage
    fun streamAll(query: SabanaQuery, consumer: (SabanaRow) -> Unit)
}

interface SabanaWorkbookWriterPort {
    fun writeXlsx(outputStream: OutputStream, rowStreamer: ((SabanaRow) -> Unit) -> Unit)
}
