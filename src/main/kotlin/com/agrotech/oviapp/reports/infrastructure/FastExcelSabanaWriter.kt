package com.agrotech.oviapp.reports.infrastructure

import com.agrotech.oviapp.reports.domain.SabanaColumnsSpec
import com.agrotech.oviapp.reports.domain.SabanaRow
import com.agrotech.oviapp.reports.domain.SabanaWorkbookWriterPort
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import org.springframework.stereotype.Component
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate

@Component
class FastExcelSabanaWriter : SabanaWorkbookWriterPort {

    override fun writeXlsx(outputStream: OutputStream, rowStreamer: ((SabanaRow) -> Unit) -> Unit) {
        Workbook(outputStream, APP_NAME, APP_VERSION).use { workbook ->
            val ws = workbook.newWorksheet(SabanaColumnsSpec.WORKSHEET_NAME)
            writeHeaderRow(ws)

            var rowIndex = 1
            rowStreamer { row ->
                writeDataRow(ws, rowIndex, row)
                if (rowIndex % FLUSH_BATCH_SIZE == 0) {
                    ws.flush()
                }
                rowIndex += 1
            }

            ws.finish()
        }
    }

    private fun writeHeaderRow(ws: Worksheet) {
        SabanaColumnsSpec.HEADERS.forEachIndexed { colIndex, header ->
            ws.value(0, colIndex, header)
        }
        ws.range(0, 0, 0, SabanaColumnsSpec.COLUMN_COUNT - 1)
            .style()
            .bold()
            .fillColor(HEADER_FILL_HEX)
            .fontColor(HEADER_FONT_HEX)
            .set()
        ws.freezePane(0, 1)
    }

    private fun writeDataRow(ws: Worksheet, r: Int, row: SabanaRow) {
        // A–C: identificadores de alta cardinalidad como inlineString para no acumularlos en la shared-string table
        ws.inlineString(r, 0, row.folio)
        writeOptionalInlineString(ws, r, 1, row.adn)
        // C: CHIP (484...) siempre como texto inline para no perder precisión numérica en Excel
        writeOptionalInlineString(ws, r, 2, row.chipSiniiga)
        // D: FECHA NACIMIENTO
        writeDate(ws, r, 3, row.fechaNacimiento)
        // E: FECHA DESTETE
        writeOptionalDate(ws, r, 4, row.fechaDestete)
        // F: EDAD MESES
        writeDecimal(ws, r, 5, row.edadMeses, ONE_DECIMAL_FORMAT)
        // G: EDAD DIAS
        ws.value(r, 6, row.edadDias)
        // H: PESO NACIMIENTO
        writeOptionalDecimal(ws, r, 7, row.pesoNacimientoKg, TWO_DECIMAL_FORMAT)
        // I: PESO 120 DIAS
        writeOptionalDecimal(ws, r, 8, row.peso120DiasKg, TWO_DECIMAL_FORMAT)
        // J: PESO 150 DIAS
        writeOptionalDecimal(ws, r, 9, row.peso150DiasKg, TWO_DECIMAL_FORMAT)
        // K: PESO A LA EVALUACION
        writeOptionalDecimal(ws, r, 10, row.pesoEvaluacionKg, TWO_DECIMAL_FORMAT)
        // L: CIRCUNFERENCIA (solo machos)
        writeOptionalDecimal(ws, r, 11, row.circunferenciaEscrotalCm, TWO_DECIMAL_FORMAT)
        // M: PESO DESTETE
        writeOptionalDecimal(ws, r, 12, row.pesoDesteteKg, TWO_DECIMAL_FORMAT)
        // N: TIPO DESTETE (baja cardinalidad → shared string)
        writeOptionalSharedString(ws, r, 13, row.tipoDestete)
        // O–R: folios genealógicos de alta cardinalidad → inlineString
        writeOptionalInlineString(ws, r, 14, row.padreFolioInterno)
        writeOptionalInlineString(ws, r, 15, row.folioPadreUno)
        writeOptionalInlineString(ws, r, 16, row.madreFolioInterno)
        writeOptionalInlineString(ws, r, 17, row.folioMadreUno)
        // S–W: atributos categóricos de baja cardinalidad → shared string
        ws.value(r, 18, row.raza)
        ws.value(r, 19, row.tipoParto)
        ws.value(r, 20, row.concepcion)
        ws.value(r, 21, row.color)
        ws.value(r, 22, row.sexo)
        // X: FECHA CORTE
        writeDate(ws, r, 23, row.fechaCorteIso)
    }

    private fun writeOptionalInlineString(ws: Worksheet, r: Int, c: Int, value: String?) {
        if (!value.isNullOrBlank()) {
            ws.inlineString(r, c, value)
        }
    }

    private fun writeOptionalSharedString(ws: Worksheet, r: Int, c: Int, value: String?) {
        if (!value.isNullOrBlank()) {
            ws.value(r, c, value)
        }
    }

    private fun writeDate(ws: Worksheet, r: Int, c: Int, value: LocalDate) {
        ws.value(r, c, value)
        ws.style(r, c).format(ISO_DATE_FORMAT).set()
    }

    private fun writeOptionalDate(ws: Worksheet, r: Int, c: Int, value: LocalDate?) {
        if (value != null) {
            writeDate(ws, r, c, value)
        }
    }

    private fun writeDecimal(ws: Worksheet, r: Int, c: Int, value: BigDecimal, format: String) {
        ws.value(r, c, value)
        ws.style(r, c).format(format).set()
    }

    private fun writeOptionalDecimal(ws: Worksheet, r: Int, c: Int, value: BigDecimal?, format: String) {
        if (value != null) {
            writeDecimal(ws, r, c, value, format)
        }
    }

    companion object {
        private const val APP_NAME = "BaaPP-OviApp"
        private const val APP_VERSION = "1.0"
        private const val FLUSH_BATCH_SIZE = 200
        private const val HEADER_FILL_HEX = "1B4332"
        private const val HEADER_FONT_HEX = "FFFFFF"
        private const val ISO_DATE_FORMAT = "yyyy-mm-dd"
        private const val ONE_DECIMAL_FORMAT = "0.0"
        private const val TWO_DECIMAL_FORMAT = "0.00"
    }
}
