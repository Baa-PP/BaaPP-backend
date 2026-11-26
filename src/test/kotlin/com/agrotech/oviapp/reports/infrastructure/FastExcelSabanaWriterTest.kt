package com.agrotech.oviapp.reports.infrastructure

import com.agrotech.oviapp.reports.domain.SabanaColumnsSpec
import com.agrotech.oviapp.reports.domain.SabanaRow
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate

class FastExcelSabanaWriterTest {

    private val writer = FastExcelSabanaWriter()

    @Test
    fun `writes valid xlsx with exact 24 columns A-X and preserves SINIIGA chip text`() {
        val sample = sampleRow(folio = "79", sexo = "MACHO", scrotal = BigDecimal("34.50"))
        val output = ByteArrayOutputStream()

        writer.writeXlsx(output) { emit ->
            emit(sample)
        }

        val bytes = output.toByteArray()
        assertTrue(bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())

        ReadableWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.firstSheet
            assertEquals(SabanaColumnsSpec.WORKSHEET_NAME, sheet.name)

            sheet.openStream().use { stream ->
                val rows = stream.toList()
                assertEquals(2, rows.size)

                val headerRow = rows[0]
                val headers = (0 until SabanaColumnsSpec.COLUMN_COUNT).map { col -> headerRow.getCellText(col) }
                assertEquals(SabanaColumnsSpec.HEADERS, headers)

                val dataRow = rows[1]
                assertEquals("79", dataRow.getCellText(0))
                assertEquals("ADN-79", dataRow.getCellText(1))
                assertEquals("484000123456789", dataRow.getCellText(2))
                assertEquals(LocalDate.of(2025, 3, 15), dataRow.getCellAsDate(3).orElseThrow().toLocalDate())
                assertEquals(LocalDate.of(2025, 5, 29), dataRow.getCellAsDate(4).orElseThrow().toLocalDate())
                assertEquals(0, BigDecimal("19.5").compareTo(dataRow.getCellAsNumber(5).orElseThrow()))
                assertEquals(585, dataRow.getCellAsNumber(6).orElseThrow().toInt())
                assertEquals(0, BigDecimal("4.20").compareTo(dataRow.getCellAsNumber(7).orElseThrow()))
                assertEquals(0, BigDecimal("34.50").compareTo(dataRow.getCellAsNumber(11).orElseThrow()))
                assertEquals("DORPER", dataRow.getCellText(18))
                assertEquals("MACHO", dataRow.getCellText(22))
                assertEquals(LocalDate.of(2026, 10, 21), dataRow.getCellAsDate(23).orElseThrow().toLocalDate())
            }
        }
    }

    @Test
    fun `streams multiple flush batches cleanly for large ranch exports`() {
        val totalRows = 450
        val output = ByteArrayOutputStream()

        writer.writeXlsx(output) { emit ->
            repeat(totalRows) { idx ->
                emit(
                    sampleRow(
                        folio = "F-${idx + 1}",
                        sexo = if (idx % 2 == 0) "HEMBRA" else "MACHO",
                        scrotal = if (idx % 2 == 0) null else BigDecimal("32.00"),
                    ),
                )
            }
        }

        ReadableWorkbook(ByteArrayInputStream(output.toByteArray())).use { workbook ->
            workbook.firstSheet.openStream().use { stream ->
                val rows = stream.toList()
                assertEquals(totalRows + 1, rows.size)
                assertEquals("F-1", rows[1].getCellText(0))
                assertEquals("F-450", rows[totalRows].getCellText(0))
            }
        }
    }

    private fun sampleRow(folio: String, sexo: String, scrotal: BigDecimal?) = SabanaRow(
        folio = folio,
        adn = "ADN-$folio",
        chipSiniiga = "484000123456789",
        fechaNacimiento = LocalDate.of(2025, 3, 15),
        fechaDestete = LocalDate.of(2025, 5, 29),
        edadMeses = BigDecimal("19.5"),
        edadDias = 585,
        pesoNacimientoKg = BigDecimal("4.20"),
        peso120DiasKg = BigDecimal("32.40"),
        peso150DiasKg = BigDecimal("38.90"),
        pesoEvaluacionKg = BigDecimal("46.10"),
        circunferenciaEscrotalCm = scrotal,
        pesoDesteteKg = BigDecimal("21.50"),
        tipoDestete = "SIMPLE",
        padreFolioInterno = "S-01",
        folioPadreUno = "268309MM-RP",
        madreFolioInterno = "M-12",
        folioMadreUno = "268310HH-RP",
        raza = "DORPER",
        tipoParto = "SIMPLE",
        concepcion = "MN",
        color = "BLANCO",
        sexo = sexo,
        fechaCorteIso = LocalDate.of(2026, 10, 21),
    )
}
