package com.agrotech.oviapp.animals.presentation

import com.agrotech.oviapp.animals.application.CreateAnimalCommand
import com.agrotech.oviapp.animals.application.CreateAnimalUseCase
import com.agrotech.oviapp.animals.application.GetAnimalDetailUseCase
import com.agrotech.oviapp.animals.application.ListAnimalsUseCase
import com.agrotech.oviapp.animals.application.UpdateAnimalCommand
import com.agrotech.oviapp.animals.application.UpdateAnimalUseCase
import com.agrotech.oviapp.animals.domain.AnimalDetail
import com.agrotech.oviapp.animals.domain.AnimalPage
import com.agrotech.oviapp.animals.domain.AnimalRecord
import com.agrotech.oviapp.animals.domain.AnimalSummary
import com.agrotech.oviapp.animals.domain.AnimalWeightRecord
import com.agrotech.oviapp.animals.domain.EntryReason
import com.agrotech.oviapp.animals.domain.ListAnimalsQuery
import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.exception.BusinessConflictException
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.presentation.GlobalExceptionHandler
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import com.agrotech.oviapp.presentation.security.RanchContextInterceptor
import com.agrotech.oviapp.presentation.security.WebMvcConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(AnimalsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class, RanchAccessAuthorizer::class)
class AnimalsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var listAnimalsUseCase: ListAnimalsUseCase

    @MockitoBean
    private lateinit var getAnimalDetailUseCase: GetAnimalDetailUseCase

    @MockitoBean
    private lateinit var createAnimalUseCase: CreateAnimalUseCase

    @MockitoBean
    private lateinit var updateAnimalUseCase: UpdateAnimalUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val ownerPrincipal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")

    @BeforeEach
    fun setUpMembership() {
        given(ranchMembershipPort.resolveAccess("producer_1", ranchId)).willReturn(RanchAccess(Role.OWNER))
        given(ranchMembershipPort.resolveAccess("producer_1", otherRanchId)).willReturn(null)
    }

    @Test
    fun `WORKER_BRANCH without lectura, captura, or edicion permission receives 403 before invoking use cases`() {
        val noPermWorker = TestingAuthenticationToken("worker_none", null, "ROLE_WORKER_BRANCH")
        given(ranchMembershipPort.resolveAccess("worker_none", ranchId))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, emptyMap()))

        mockMvc.get("/api/v1/animals") {
            principal = noPermWorker
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.get("/api/v1/animals/$animalId") {
            principal = noPermWorker
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.post("/api/v1/animals") {
            principal = noPermWorker
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validCreateJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        mockMvc.put("/api/v1/animals/$animalId") {
            principal = noPermWorker
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INSUFFICIENT_PERMISSION") }
        }

        verifyNoInteractions(listAnimalsUseCase, getAnimalDetailUseCase, createAnimalUseCase, updateAnimalUseCase)
    }

    @Test
    fun `rejects cross-ranch access with 403 across all endpoints before invoking use cases`() {
        mockMvc.get("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.get("/api/v1/animals/$animalId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.post("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validCreateJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        mockMvc.put("/api/v1/animals/$animalId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", otherRanchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RANCH_ACCESS_DENIED") }
        }

        verifyNoInteractions(listAnimalsUseCase, getAnimalDetailUseCase, createAnimalUseCase, updateAnimalUseCase)
    }

    @Test
    fun `DELETE animal endpoint does not exist and returns 405 Method Not Allowed`() {
        mockMvc.delete("/api/v1/animals/$animalId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isMethodNotAllowed() }
        }

        verifyNoInteractions(listAnimalsUseCase, getAnimalDetailUseCase, createAnimalUseCase, updateAnimalUseCase)
    }

    @Test
    fun `GET animals supports combinable filters, pagination, and updated_after cursor`() {
        val updatedAfter = Instant.parse("2026-10-01T12:00:00Z")
        val updatedAt = Instant.parse("2026-10-05T09:30:00Z")
        val expectedQuery = ListAnimalsQuery(
            ranchId = ranchId,
            search = "OV-10",
            sex = Sex.FEMALE,
            lifeStage = LifeStage.ADULT,
            reproductiveStatus = ReproductiveStatus.PREGNANT,
            breed = "Dorper",
            status = AnimalStatus.ACTIVE,
            updatedAfter = updatedAfter,
            page = 1,
            size = 10,
        )
        given(listAnimalsUseCase.execute(expectedQuery)).willReturn(
            AnimalPage(
                items = listOf(
                    AnimalSummary(
                        id = animalId,
                        ranchId = ranchId,
                        folioInterno = "OV-101",
                        siniigaChip = "4841234567",
                        registroUno = "UNO-101",
                        sex = Sex.FEMALE,
                        lifeStage = LifeStage.ADULT,
                        reproductiveStatus = ReproductiveStatus.PREGNANT,
                        breed = "Dorper",
                        color = "Blanco",
                        birthDate = LocalDate.of(2024, 5, 1),
                        ageDays = 500,
                        weaningDate = LocalDate.of(2024, 7, 15),
                        entryReason = EntryReason.BIRTH,
                        criador = "Criadero Norte",
                        propietario = "Lucas Ganadero",
                        photoS3Key = "$ranchId/animals/pic.webp",
                        latestWeightKg = BigDecimal("55.20"),
                        latestWeightDate = LocalDate.of(2026, 9, 10),
                        birthCount = 2,
                        pregnant = true,
                        status = AnimalStatus.ACTIVE,
                        mortalityDate = null,
                        mortalityCause = null,
                        sireId = null,
                        damId = null,
                        updatedAt = updatedAt,
                    ),
                ),
                page = 1,
                size = 10,
                totalElements = 11L,
                totalPages = 2,
            ),
        )

        mockMvc.get("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            param("q", "OV-10")
            param("sexo", "FEMALE")
            param("etapa_vida", "ADULT")
            param("estado_reproductivo", "PREGNANT")
            param("raza", "Dorper")
            param("estado", "ACTIVE")
            param("updated_after", "2026-10-01T12:00:00Z")
            param("page", "1")
            param("size", "10")
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(10) }
            jsonPath("$.totalElements") { value(11) }
            jsonPath("$.totalPages") { value(2) }
            jsonPath("$.items[0].id") { value(animalId.toString()) }
            jsonPath("$.items[0].folioInterno") { value("OV-101") }
            jsonPath("$.items[0].criador") { value("Criadero Norte") }
            jsonPath("$.items[0].propietario") { value("Lucas Ganadero") }
            jsonPath("$.items[0].updatedAt") { value("2026-10-05T09:30:00Z") }
        }
    }

    @Test
    fun `GET animal detail returns full ficha including criador, propietario, weights, and photoUrl and maps 404`() {
        val weightId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val updatedAt = Instant.parse("2026-10-10T10:00:00Z")
        given(getAnimalDetailUseCase.execute(ranchId, animalId)).willReturn(
            AnimalDetail(
                id = animalId,
                ranchId = ranchId,
                folioInterno = "OV-101",
                siniigaChip = "4841234567",
                registroUno = "UNO-101",
                adn = "ADN-101",
                breed = "Dorper",
                color = "Blanco",
                sex = Sex.FEMALE,
                birthDate = LocalDate.of(2024, 5, 1),
                ageDays = 527,
                weaningDate = LocalDate.of(2024, 7, 15),
                entryReason = EntryReason.PURCHASE,
                criador = "Rancho El Origen",
                propietario = "Lucas Ganadero",
                lifeStage = LifeStage.ADULT,
                reproductiveStatus = ReproductiveStatus.LACTATING,
                status = AnimalStatus.ACTIVE,
                parturitionType = ParturitionType.TWIN,
                conceptionMethod = ConceptionMethod.MN,
                pregnant = false,
                birthCount = 1,
                mortalityDate = null,
                mortalityCause = null,
                lifeStageAtDeath = null,
                sireId = null,
                damId = null,
                externalSireFolio = "EXT-S1",
                externalDamFolio = null,
                birthEventId = null,
                latestWeightKg = BigDecimal("54.50"),
                latestWeightDate = LocalDate.of(2026, 9, 20),
                weights = listOf(
                    AnimalWeightRecord(
                        id = weightId,
                        milestone = "EVALUATION",
                        weightKg = BigDecimal("54.50"),
                        scrotalCircumferenceCm = null,
                        date = LocalDate.of(2026, 9, 20),
                    ),
                ),
                photoS3Key = "$ranchId/animals/$animalId.webp",
                photoUrl = "https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/$ranchId/animals/$animalId.webp?sig=1",
                updatedAt = updatedAt,
            ),
        )

        mockMvc.get("/api/v1/animals/$animalId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(animalId.toString()) }
            jsonPath("$.criador") { value("Rancho El Origen") }
            jsonPath("$.propietario") { value("Lucas Ganadero") }
            jsonPath("$.weights[0].id") { value(weightId.toString()) }
            jsonPath("$.weights[0].weightKg") { value(54.50) }
            jsonPath("$.photoUrl") { value("https://agrotech-oviapp-media.s3.us-east-2.amazonaws.com/$ranchId/animals/$animalId.webp?sig=1") }
            jsonPath("$.updatedAt") { value("2026-10-10T10:00:00Z") }
        }

        val missingId = UUID.fromString("88888888-8888-8888-8888-888888888888")
        given(getAnimalDetailUseCase.execute(ranchId, missingId))
            .willThrow(ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo."))

        mockMvc.get("/api/v1/animals/$missingId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ANIMAL_NOT_FOUND") }
        }
    }

    @Test
    fun `POST animals creates animal with 201 and maps 400 validation and 409 conflicts`() {
        val createdAt = Instant.parse("2026-10-15T12:00:00Z")
        val command = CreateAnimalCommand(
            id = animalId,
            ranchId = ranchId,
            folioInterno = "OV-500",
            siniigaChip = "4841234567",
            breed = "Dorper",
            color = "Blanco",
            sex = Sex.FEMALE,
            birthDate = LocalDate.of(2026, 3, 1),
            entryReason = EntryReason.BIRTH,
            parturitionType = ParturitionType.SIMPLE,
            conceptionMethod = ConceptionMethod.MN,
            lifeStage = LifeStage.LAMB_LACTATING,
            reproductiveStatus = ReproductiveStatus.EMPTY,
            status = AnimalStatus.ACTIVE,
        )
        given(createAnimalUseCase.execute(command)).willReturn(
            sampleRecord(id = animalId, folioInterno = "OV-500", updatedAt = createdAt),
        )

        mockMvc.post("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validCreateJson(id = animalId)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(animalId.toString()) }
            jsonPath("$.folioInterno") { value("OV-500") }
            jsonPath("$.siniigaChip") { value("4841234567") }
        }

        // Bean validation failure -> 400
        mockMvc.post("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "folioInterno": "",
                  "breed": "Dorper",
                  "color": "Blanco",
                  "sex": "FEMALE",
                  "birthDate": "2026-03-01",
                  "lifeStage": "LAMB_LACTATING",
                  "reproductiveStatus": "EMPTY"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }

        // SINIIGA domain validation -> 400
        given(createAnimalUseCase.execute(command.copy(id = null, siniigaChip = "12345")))
            .willThrow(DomainValidationException("INVALID_SINIIGA_CHIP", "Chip SINIIGA inválido."))

        mockMvc.post("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validCreateJson(siniigaChip = "12345")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_SINIIGA_CHIP") }
        }

        // Duplicate folio -> 409
        given(createAnimalUseCase.execute(command.copy(id = null, folioInterno = "OV-DUP")))
            .willThrow(BusinessConflictException("FOLIO_ALREADY_EXISTS", "Folio duplicado."))

        mockMvc.post("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validCreateJson(folioInterno = "OV-DUP")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("FOLIO_ALREADY_EXISTS") }
        }
    }

    @Test
    fun `PUT animal updates record with 200 and maps 409 and 404`() {
        val updatedAt = Instant.parse("2026-10-16T14:00:00Z")
        val command = UpdateAnimalCommand(
            animalId = animalId,
            ranchId = ranchId,
            folioInterno = "OV-500",
            siniigaChip = "4841234567",
            breed = "Dorper",
            color = "Blanco",
            sex = Sex.FEMALE,
            birthDate = LocalDate.of(2026, 3, 1),
            lifeStage = LifeStage.ADULT,
            reproductiveStatus = ReproductiveStatus.EMPTY,
            status = AnimalStatus.DECEASED,
            mortalityDate = LocalDate.of(2026, 10, 15),
            mortalityCause = "Neumonía",
        )
        given(updateAnimalUseCase.execute(command)).willReturn(
            sampleRecord(
                id = animalId,
                folioInterno = "OV-500",
                status = AnimalStatus.DECEASED,
                mortalityDate = LocalDate.of(2026, 10, 15),
                mortalityCause = "Neumonía",
                lifeStageAtDeath = LifeStage.ADULT,
                updatedAt = updatedAt,
            ),
        )

        mockMvc.put("/api/v1/animals/$animalId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateJson(
                status = "DECEASED",
                mortalityDate = "2026-10-15",
                mortalityCause = "Neumonía",
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DECEASED") }
            jsonPath("$.mortalityDate") { value("2026-10-15") }
            jsonPath("$.mortalityCause") { value("Neumonía") }
            jsonPath("$.lifeStageAtDeath") { value("ADULT") }
        }

        // Duplicate chip on update -> 409
        given(updateAnimalUseCase.execute(command.copy(status = AnimalStatus.ACTIVE, mortalityDate = null, mortalityCause = null, siniigaChip = "4849999999")))
            .willThrow(BusinessConflictException("SINIIGA_CHIP_ALREADY_EXISTS", "Chip duplicado."))

        mockMvc.put("/api/v1/animals/$animalId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateJson(siniigaChip = "4849999999")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SINIIGA_CHIP_ALREADY_EXISTS") }
        }

        // Missing animal on update -> 404
        val missingId = UUID.fromString("77777777-7777-7777-7777-777777777777")
        given(updateAnimalUseCase.execute(command.copy(animalId = missingId, status = AnimalStatus.ACTIVE, mortalityDate = null, mortalityCause = null)))
            .willThrow(ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo."))

        mockMvc.put("/api/v1/animals/$missingId") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = validUpdateJson()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ANIMAL_NOT_FOUND") }
        }
    }

    @Test
    fun `GET animals rejects missing ranch header and invalid updated_after timestamp with 400`() {
        mockMvc.get("/api/v1/animals") {
            principal = ownerPrincipal
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_RANCH_CONTEXT") }
        }

        mockMvc.get("/api/v1/animals") {
            principal = ownerPrincipal
            header("X-Active-Ranch-Id", ranchId.toString())
            param("updated_after", "not-an-iso-timestamp")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }

        verifyNoInteractions(listAnimalsUseCase)
    }

    private fun validCreateJson(
        id: UUID? = null,
        folioInterno: String = "OV-500",
        siniigaChip: String = "4841234567",
    ): String {
        val idField = if (id != null) """"id": "$id",""" else ""
        return """
            {
              $idField
              "folioInterno": "$folioInterno",
              "siniigaChip": "$siniigaChip",
              "breed": "Dorper",
              "color": "Blanco",
              "sex": "FEMALE",
              "birthDate": "2026-03-01",
              "lifeStage": "LAMB_LACTATING",
              "reproductiveStatus": "EMPTY"
            }
        """.trimIndent()
    }

    private fun validUpdateJson(
        siniigaChip: String = "4841234567",
        status: String = "ACTIVE",
        mortalityDate: String? = null,
        mortalityCause: String? = null,
    ): String {
        val mortalityFields = buildString {
            if (mortalityDate != null) append(""""mortalityDate": "$mortalityDate",""")
            if (mortalityCause != null) append(""""mortalityCause": "$mortalityCause",""")
        }
        return """
            {
              "folioInterno": "OV-500",
              "siniigaChip": "$siniigaChip",
              "breed": "Dorper",
              "color": "Blanco",
              "sex": "FEMALE",
              "birthDate": "2026-03-01",
              "lifeStage": "ADULT",
              "reproductiveStatus": "EMPTY",
              $mortalityFields
              "status": "$status"
            }
        """.trimIndent()
    }

    private fun sampleRecord(
        id: UUID,
        folioInterno: String,
        status: AnimalStatus = AnimalStatus.ACTIVE,
        mortalityDate: LocalDate? = null,
        mortalityCause: String? = null,
        lifeStageAtDeath: LifeStage? = null,
        updatedAt: Instant,
    ) = AnimalRecord(
        id = id,
        ranchId = ranchId,
        folioInterno = folioInterno,
        siniigaChip = "4841234567",
        registroUno = null,
        adn = null,
        breed = "Dorper",
        color = "Blanco",
        sex = Sex.FEMALE,
        birthDate = LocalDate.of(2026, 3, 1),
        weaningDate = null,
        entryReason = EntryReason.BIRTH,
        criador = null,
        parturitionType = ParturitionType.SIMPLE,
        conceptionMethod = ConceptionMethod.MN,
        sireId = null,
        damId = null,
        externalSireFolio = null,
        externalDamFolio = null,
        lifeStage = LifeStage.ADULT,
        reproductiveStatus = ReproductiveStatus.EMPTY,
        status = status,
        mortalityDate = mortalityDate,
        mortalityCause = mortalityCause,
        lifeStageAtDeath = lifeStageAtDeath,
        photoS3Key = null,
        birthEventId = null,
        updatedAt = updatedAt,
    )
}
