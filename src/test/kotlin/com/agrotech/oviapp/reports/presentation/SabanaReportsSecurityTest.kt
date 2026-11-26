package com.agrotech.oviapp.reports.presentation

import com.agrotech.oviapp.auth.domain.JwtClaims
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.JwtAuthenticationFilter
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.SecurityConfig
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import com.agrotech.oviapp.reports.application.GetSabanaUseCase
import com.agrotech.oviapp.reports.domain.SabanaColumnsSpec
import com.agrotech.oviapp.reports.domain.SabanaPage
import com.agrotech.oviapp.reports.domain.SabanaQuery
import com.agrotech.oviapp.reports.domain.SabanaRow
import com.agrotech.oviapp.reports.infrastructure.FastExcelSabanaWriter
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willAnswer
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(SabanaReportsController::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    RanchContextInterceptor::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    RanchAccessAuthorizer::class,
)
class SabanaReportsSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    @MockitoBean
    private lateinit var getSabanaUseCase: GetSabanaUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    private val userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val xlsxWriter = FastExcelSabanaWriter()

    @Test
    fun `unauthenticated sabana report requests are blocked with 401`() {
        mockMvc.get("/api/v1/reports/sabana")
            .andExpect { status { isUnauthorized() } }

        mockMvc.get("/api/v1/reports/sabana") {
            param("format", "xlsx")
        }.andExpect { status { isUnauthorized() } }

        mockMvc.get("/api/v1/reports/sabana") {
            param("format", "json")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `authenticated Bearer token completes no-format JSON streaming response across ASYNC dispatch`() {
        stubAuthenticatedOwner()
        given(getSabanaUseCase.executePage(SabanaQuery(ranchId = ranchId))).willReturn(
            SabanaPage(items = listOf(sampleRow()), page = 0, size = 100, totalElements = 1L, totalPages = 1),
        )

        val mvcResult = mockMvc.get("/api/v1/reports/sabana") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            request { asyncStarted() }
        }.andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].folio").value("79"))

        verify(jwtTokenPort, times(1)).verifyAccessToken("valid-token")
        verify(ranchMembershipPort, times(1)).resolveAccess("producer_1", ranchId)
        verify(getSabanaUseCase, times(1)).executePage(SabanaQuery(ranchId = ranchId))
    }

    @Test
    fun `authenticated Bearer token completes format=xlsx streaming response across ASYNC dispatch`() {
        stubAuthenticatedOwner()
        stubXlsxExport()

        val mvcResult = mockMvc.get("/api/v1/reports/sabana") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
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
            }
        }

        verify(jwtTokenPort, times(1)).verifyAccessToken("valid-token")
        verify(ranchMembershipPort, times(1)).resolveAccess("producer_1", ranchId)
    }

    @Test
    fun `authenticated Bearer token completes Accept XLSX streaming response across ASYNC dispatch`() {
        stubAuthenticatedOwner()
        stubXlsxExport()

        val mvcResult = mockMvc.get("/api/v1/reports/sabana") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
            header(HttpHeaders.ACCEPT, SabanaColumnsSpec.XLSX_MEDIA_TYPE)
        }.andExpect {
            request { asyncStarted() }
        }.andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, SabanaColumnsSpec.XLSX_MEDIA_TYPE))

        verify(jwtTokenPort, times(1)).verifyAccessToken("valid-token")
        verify(ranchMembershipPort, times(1)).resolveAccess("producer_1", ranchId)
    }

    @Test
    fun `authenticated Bearer token completes buffered format=json response synchronously`() {
        stubAuthenticatedOwner()
        given(getSabanaUseCase.executePage(SabanaQuery(ranchId = ranchId))).willReturn(
            SabanaPage(items = listOf(sampleRow()), page = 0, size = 100, totalElements = 1L, totalPages = 1),
        )

        mockMvc.get("/api/v1/reports/sabana") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
            param("format", "json")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.items[0].folio") { value("79") }
        }

        verify(jwtTokenPort, times(1)).verifyAccessToken("valid-token")
        verify(ranchMembershipPort, times(1)).resolveAccess("producer_1", ranchId)
    }

    private fun stubAuthenticatedOwner() {
        given(jwtTokenPort.verifyAccessToken("valid-token")).willReturn(
            JwtClaims(
                userId = userId,
                username = "producer_1",
                role = Role.OWNER,
                ranchId = ranchId,
                associationId = null,
                expiresAt = Instant.now().plusSeconds(300),
            ),
        )
        given(ranchMembershipPort.resolveAccess("producer_1", ranchId)).willReturn(RanchAccess(Role.OWNER))
    }

    private fun stubXlsxExport() {
        willAnswer { invocation ->
            val out = invocation.getArgument<OutputStream>(1)
            xlsxWriter.writeXlsx(out) { emit -> emit(sampleRow()) }
            null
        }.given(getSabanaUseCase).exportXlsx(eqNonNull(SabanaQuery(ranchId = ranchId)), anyOutputStream())
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
