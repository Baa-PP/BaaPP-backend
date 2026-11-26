package com.agrotech.oviapp.reports.application

import com.agrotech.oviapp.reports.domain.SabanaPage
import com.agrotech.oviapp.reports.domain.SabanaPolicy
import com.agrotech.oviapp.reports.domain.SabanaQuery
import com.agrotech.oviapp.reports.domain.SabanaReadPort
import com.agrotech.oviapp.reports.domain.SabanaWorkbookWriterPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.OutputStream

@Service
class GetSabanaUseCase(
    private val sabanaReadPort: SabanaReadPort,
    private val sabanaWorkbookWriterPort: SabanaWorkbookWriterPort,
) {
    @Transactional(readOnly = true)
    fun executePage(query: SabanaQuery): SabanaPage {
        SabanaPolicy.validatePagination(page = query.page, size = query.size)
        return sabanaReadPort.findPage(query)
    }

    /**
     * Se ejecuta dentro de una transacción de solo lectura (autoCommit=false) para que
     * el cursor JDBC en PostgreSQL haga streaming por lotes (fetchSize) directamente
     * hacia FastExcel sin materializar todas las filas ni el workbook completo en heap.
     */
    @Transactional(readOnly = true)
    fun exportXlsx(query: SabanaQuery, outputStream: OutputStream) {
        sabanaWorkbookWriterPort.writeXlsx(outputStream) { onRow ->
            sabanaReadPort.streamAll(query, onRow)
        }
    }
}
