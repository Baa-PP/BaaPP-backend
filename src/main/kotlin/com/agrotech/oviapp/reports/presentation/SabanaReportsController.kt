package com.agrotech.oviapp.reports.presentation

import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.RanchPermission
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RequireRanchPermission
import com.agrotech.oviapp.reports.application.GetSabanaUseCase
import com.agrotech.oviapp.reports.domain.SabanaColumnsSpec
import com.agrotech.oviapp.reports.domain.SabanaExportFormat
import com.agrotech.oviapp.reports.domain.SabanaPage
import com.agrotech.oviapp.reports.domain.SabanaPolicy
import com.agrotech.oviapp.reports.domain.SabanaQuery
import com.agrotech.oviapp.reports.domain.SabanaRow
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Contrato JSON de las 24 columnas A–X, idéntico a `SabanaRow` en `SabanaColumns.ts`.
 */
data class SabanaRowResponse(
    val folio: String,
    val adn: String?,
    val chipSiniiga: String?,
    val fechaNacimiento: LocalDate,
    val fechaDestete: LocalDate?,
    val edadMeses: BigDecimal,
    val edadDias: Int,
    val pesoNacimientoKg: BigDecimal?,
    val peso120DiasKg: BigDecimal?,
    val peso150DiasKg: BigDecimal?,
    val pesoEvaluacionKg: BigDecimal?,
    val circunferenciaEscrotalCm: BigDecimal?,
    val pesoDesteteKg: BigDecimal?,
    val tipoDestete: String?,
    val padreFolioInterno: String?,
    val folioPadreUno: String?,
    val madreFolioInterno: String?,
    val folioMadreUno: String?,
    val raza: String,
    val tipoParto: String,
    val concepcion: String,
    val color: String,
    val sexo: String,
    val fechaCorteIso: LocalDate,
)

data class SabanaPageResponse(
    val items: List<SabanaRowResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@RestController
@RequestMapping("/api/v1/reports")
class SabanaReportsController(
    private val ranchAccessAuthorizer: RanchAccessAuthorizer,
    private val getSabanaUseCase: GetSabanaUseCase,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping("/sabana", params = ["format=json"])
    @RequireRanchPermission(RanchPermission.EXPORTACION)
    fun getSabanaJsonByParam(
        authentication: Authentication?,
        @RequestParam(name = "q", required = false) search: String?,
        @RequestParam(name = "sexo", required = false) sex: Sex?,
        @RequestParam(name = "raza", required = false) breed: String?,
        @RequestParam(name = "estado", required = false) status: AnimalStatus?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "100") size: Int,
    ): ResponseEntity<SabanaPageResponse> =
        buildJsonPageResponse(authentication, search, sex, breed, status, page, size)

    @GetMapping("/sabana", params = ["format", "format!=json"])
    @RequireRanchPermission(RanchPermission.EXPORTACION)
    fun getSabanaByFormatParam(
        authentication: Authentication?,
        @RequestParam(name = "format") format: String,
        @RequestHeader(name = HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @RequestParam(name = "q", required = false) search: String?,
        @RequestParam(name = "sexo", required = false) sex: Sex?,
        @RequestParam(name = "raza", required = false) breed: String?,
        @RequestParam(name = "estado", required = false) status: AnimalStatus?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "100") size: Int,
    ): ResponseEntity<StreamingResponseBody> =
        buildResolvedStreamingResponse(
            authentication = authentication,
            formatParam = format,
            acceptHeader = acceptHeader,
            search = search,
            sex = sex,
            breed = breed,
            status = status,
            page = page,
            size = size,
        )

    @GetMapping(
        "/sabana",
        params = ["!format"],
        produces = [SabanaColumnsSpec.XLSX_MEDIA_TYPE],
    )
    @RequireRanchPermission(RanchPermission.EXPORTACION)
    fun exportSabanaXlsxByAccept(
        authentication: Authentication?,
        @RequestHeader(name = HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @RequestParam(name = "q", required = false) search: String?,
        @RequestParam(name = "sexo", required = false) sex: Sex?,
        @RequestParam(name = "raza", required = false) breed: String?,
        @RequestParam(name = "estado", required = false) status: AnimalStatus?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "100") size: Int,
    ): ResponseEntity<StreamingResponseBody> =
        buildResolvedStreamingResponse(
            authentication = authentication,
            formatParam = null,
            acceptHeader = acceptHeader,
            search = search,
            sex = sex,
            breed = breed,
            status = status,
            page = page,
            size = size,
        )

    @GetMapping(
        "/sabana",
        params = ["!format"],
        produces = [MediaType.APPLICATION_JSON_VALUE, "application/*", MediaType.ALL_VALUE],
    )
    @RequireRanchPermission(RanchPermission.EXPORTACION)
    fun getSabanaJson(
        authentication: Authentication?,
        @RequestParam(name = "q", required = false) search: String?,
        @RequestParam(name = "sexo", required = false) sex: Sex?,
        @RequestParam(name = "raza", required = false) breed: String?,
        @RequestParam(name = "estado", required = false) status: AnimalStatus?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "100") size: Int,
    ): ResponseEntity<SabanaPageResponse> =
        buildJsonPageResponse(authentication, search, sex, breed, status, page, size)

    private fun buildJsonPageResponse(
        authentication: Authentication?,
        search: String?,
        sex: Sex?,
        breed: String?,
        status: AnimalStatus?,
        page: Int,
        size: Int,
    ): ResponseEntity<SabanaPageResponse> {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication, RanchPermission.EXPORTACION)
        val query = SabanaQuery(
            ranchId = ranchId,
            search = search,
            sex = sex,
            breed = breed,
            status = status,
            page = page,
            size = size,
        )
        val pageResult = getSabanaUseCase.executePage(query).toResponse()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(pageResult)
    }

    private fun buildResolvedStreamingResponse(
        authentication: Authentication?,
        formatParam: String?,
        acceptHeader: String?,
        search: String?,
        sex: Sex?,
        breed: String?,
        status: AnimalStatus?,
        page: Int,
        size: Int,
    ): ResponseEntity<StreamingResponseBody> {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication, RanchPermission.EXPORTACION)
        val resolvedFormat = SabanaPolicy.resolveFormat(formatParam = formatParam, acceptHeader = acceptHeader)
        val query = SabanaQuery(
            ranchId = ranchId,
            search = search,
            sex = sex,
            breed = breed,
            status = status,
            page = page,
            size = size,
        )

        if (resolvedFormat == SabanaExportFormat.JSON) {
            val pageResult = getSabanaUseCase.executePage(query).toResponse()
            val body = StreamingResponseBody { outputStream ->
                objectMapper.writeValue(outputStream, pageResult)
            }
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        }

        return buildXlsxResponse(ranchId, query)
    }

    private fun buildXlsxResponse(
        ranchId: UUID,
        query: SabanaQuery,
    ): ResponseEntity<StreamingResponseBody> {
        val filename = "sabana-$ranchId-${LocalDate.now(ZoneOffset.UTC)}.xlsx"
        val body = StreamingResponseBody { outputStream ->
            getSabanaUseCase.exportXlsx(query, outputStream)
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(SabanaColumnsSpec.XLSX_MEDIA_TYPE))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString(),
            )
            .cacheControl(CacheControl.noStore())
            .body(body)
    }

    private fun SabanaPage.toResponse() = SabanaPageResponse(
        items = items.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )

    private fun SabanaRow.toResponse() = SabanaRowResponse(
        folio = folio,
        adn = adn,
        chipSiniiga = chipSiniiga,
        fechaNacimiento = fechaNacimiento,
        fechaDestete = fechaDestete,
        edadMeses = edadMeses,
        edadDias = edadDias,
        pesoNacimientoKg = pesoNacimientoKg,
        peso120DiasKg = peso120DiasKg,
        peso150DiasKg = peso150DiasKg,
        pesoEvaluacionKg = pesoEvaluacionKg,
        circunferenciaEscrotalCm = circunferenciaEscrotalCm,
        pesoDesteteKg = pesoDesteteKg,
        tipoDestete = tipoDestete,
        padreFolioInterno = padreFolioInterno,
        folioPadreUno = folioPadreUno,
        madreFolioInterno = madreFolioInterno,
        folioMadreUno = folioMadreUno,
        raza = raza,
        tipoParto = tipoParto,
        concepcion = concepcion,
        color = color,
        sexo = sexo,
        fechaCorteIso = fechaCorteIso,
    )
}
