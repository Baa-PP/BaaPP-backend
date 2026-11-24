package com.agrotech.oviapp.reports.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.agrotech.oviapp.reports.application.GetSabanaUseCase
import com.agrotech.oviapp.reports.domain.SabanaColumnsSpec
import com.agrotech.oviapp.reports.domain.SabanaPage
import com.agrotech.oviapp.reports.domain.SabanaQuery
import com.agrotech.oviapp.reports.domain.SabanaRow
import com.agrotech.oviapp.reports.infrastructure.FastExcelSabanaWriter
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willAnswer
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(SabanaReportsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    RanchAccessAuthorizer::class,
)
class SabanaReportsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getSabanaUseCase: GetSabanaUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val ownerPrincipal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")
    private val xlsxWriter = FastExcelSabanaWriter()

    @BeforeEach
    fun setUpMembership() {
        given(ranchMembershipPort.resolveAccess("producer_1", ranchId)).willReturn(RanchAccess(Role.OWNER))
        given(ranchMembershipPort.resolveAccess("producer_1", otherRanchId)).willReturn(null)
    }

    @Test
    fun `GET sabana JSON returns paginated 24-column rows matching SabanaColumns contract`() {
        val expectedQuery = SabanaQuery(
            ranchId = ranchId,
            search = "79",
            sex = Sex.FEMALE,
            breed = "Dorper",
            status = AnimalStatus.ACTIVE,
            page = 0,
            size = 50,
        )
        given(getSabanaUseCase.executePage(expectedQuery)).willReturn(
            SabanaPage(
                items = listOf(sampleRow()),
                page = 0,
                size = 50,
                totalElements = 1L,
                totalPages = 1,
            ),
        )

        mockMvc.get("/api/v1/reports/sabana") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            param("q", "79")
            param("sexo", "FEMALE")
            param("raza", "Dorper")
            param("estado", "ACTIVE")
            param("page", "0")
            param("size", "50")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.items[0].folio") { value("79") }
            jsonPath("$.items[0].adn") { value("ADN-79") }
            jsonPath("$.items[0].chipSiniiga") { value("484000123456789") }
            jsonPath("$.items[0].fechaNacimiento") { value("2025-03-15") }
            jsonPath("$.items[0].fechaDestete") { value("2025-05-29") }
            jsonPath("$.items[0].edadMeses") { value(19.5) }
            jsonPath("$.items[0].edadDias") { value(585) }
            jsonPath("$.items[0].pesoNacimientoKg") { value(4.2) }
            jsonPath("$.items[0].peso120DiasKg") { value(32.4) }
            jsonPath("$.items[0].peso150DiasKg") { value(38.9) }
            jsonPath("$.items[0].pesoEvaluacionKg") { value(46.1) }
            jsonPath("$.items[0].circunferenciaEscrotalCm") { value(nullValue()) }
            jsonPath("$.items[0].pesoDesteteKg") { value(21.5) }
            jsonPath("$.items[0].tipoDestete") { value("SIMPLE") }
            jsonPath("$.items[0].padreFolioInterno") { value("S-01") }
            jsonPath("$.items[0].folioPadreUno") { value("268309MM-RP") }
            jsonPath("$.items[0].madreFolioInterno") { value("M-12") }
            jsonPath("$.items[0].folioMadreUno") { value("268310HH-RP") }
            jsonPath("$.items[0].raza") { value("DORPER") }
            jsonPath("$.items[0].tipoParto") { value("SIMPLE") }
            jsonPath("$.items[0].concepcion") { value("MN") }
            jsonPath("$.items[0].color") { value("BLANCO") }
            jsonPath("$.items[0].sexo") { value("HEMBRA") }
            jsonPath("$.items[0].fechaCorteIso") { value("2026-10-21") }
        }
    }

    @Test
    fun `GET sabana with format=xlsx streams valid FastExcel workbook`() {
        val expectedQuery = SabanaQuery(ranchId = ranchId)
        willAnswer { invocation ->
            val out = invocation.getArgument<OutputStream>(1)
            xlsxWriter.writeXlsx(out) { emit -> emit(sampleRow()) }
            null
        }.given(getSabanaUseCase).exportXlsx(eqNonNull(expectedQuery), anyOutputStream())

        val mvcResult = mockMvc.get("/api/v1/reports/sabana") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            param("format", "xlsx")
        }.andExpect {
            request { asyncStarted() }
        }.andReturn()

        val completed = mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, SabanaColumnsSpec.XLSX_MEDIA_TYPE))
            .andReturn()

        ReadableWorkbook(ByteArrayInputStream(completed.response.contentAsByteArray)).use { wb ->
            wb.firstSheet.openStream().use { stream ->
                val rows = stream.toList()
                assertEquals(2, rows.size)
                assertEquals("79", rows[1].getCellText(0))
                assertEquals("HEMBRA", rows[1].getCellText(22))
            }
        }
    }

    @Test
    fun `GET sabana with XLSX Accept header streams valid FastExcel workbook`() {
        val expectedQuery = SabanaQuery(ranchId = ranchId)
        willAnswer { invocation ->
            val out = invocation.getArgument<OutputStream>(1)
            xlsxWriter.writeXlsx(out) { emit -> emit(sampleRow()) }
            null
        }.given(getSabanaUseCase).exportXlsx(eqNonNull(expectedQuery), anyOutputStream())

        val mvcResult = mockMvc.get("/api/v1/reports/sabana") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            header(HttpHeaders.ACCEPT, SabanaColumnsSpec.XLSX_MEDIA_TYPE)
        }.andExpect {
            request { asyncStarted() }
        }.andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, SabanaColumnsSpec.XLSX_MEDIA_TYPE))
    }

    @Test
    fun `GET sabana with unsupported format returns 400 INVALID_EXPORT_FORMAT`() {
        mockMvc.get("/api/v1/reports/sabana") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            param("format", "csv")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_EXPORT_FORMAT") }
        }

        verifyNoInteractions(getSabanaUseCase)
    }

    @Test
    fun `WORKER_BRANCH without exportacion permission receives 403 before invoking use case`() {
        val worker = TestingAuthenticationToken("worker_read_only", null, "ROLE_WORKER_BRANCH")
        given(ranchMembershipPort.resolveAccess("worker_read_only", ranchId))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf("lectura" to true, "exportacion" to false)))

        mockMvc.get("/api/v1/reports/sabana") {
            principal = worker
            header("X-Active-Ranch-Id", ranchId.toString())
            param("format", "xlsx")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        verifyNoInteractions(getSabanaUseCase)
    }

    @Test
    fun `WORKER_BRANCH with exportacion permission succeeds for its own ranch`() {
        val worker = TestingAuthenticationToken("worker_export", null, "ROLE_WORKER_BRANCH")
        given(ranchMembershipPort.resolveAccess("worker_export", ranchId))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf("exportacion" to true)))
        given(getSabanaUseCase.executePage(SabanaQuery(ranchId = ranchId))).willReturn(
            SabanaPage(items = emptyList(), page = 0, size = 100, totalElements = 0L, totalPages = 0),
        )

        mockMvc.get("/api/v1/reports/sabana") {
            principal = worker
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(0) }
        }
    }

    @Test
    fun `blocks cross-ranch access with 403 and missing ranch header with 400`() {
        mockMvc.get("/api/v1/reports/sabana") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.get("/api/v1/reports/sabana") {
            principal = ownerPrincipal
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_RANCH_CONTEXT") }
        }

        verifyNoInteractions(getSabanaUseCase)
    }

    private fun <T> eqNonNull(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun anyOutputStream(): OutputStream {
        ArgumentMatchers.any<OutputStream>()
        return OutputStream.nullOutputStream()
    }

    private fun sampleRow() = SabanaRow(
        folio = "79",
        adn = "ADN-79",
        chipSiniiga = "484000123456789",
        fechaNacimiento = LocalDate.of(2025, 3, 15),
        fechaDestete = LocalDate.of(2025, 5, 29),
        edadMeses = BigDecimal("19.5"),
        edadDias = 585,
        pesoNacimientoKg = BigDecimal("4.2"),
        peso120DiasKg = BigDecimal("32.4"),
        peso150DiasKg = BigDecimal("38.9"),
        pesoEvaluacionKg = BigDecimal("46.1"),
        circunferenciaEscrotalCm = null,
        pesoDesteteKg = BigDecimal("21.5"),
        tipoDestete = "SIMPLE",
        padreFolioInterno = "S-01",
        folioPadreUno = "268309MM-RP",
        madreFolioInterno = "M-12",
        folioMadreUno = "268310HH-RP",
        raza = "DORPER",
        tipoParto = "SIMPLE",
        concepcion = "MN",
        color = "BLANCO",
        sexo = "HEMBRA",
        fechaCorteIso = LocalDate.of(2026, 10, 21),
    )
}
