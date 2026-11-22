package com.agrotech.oviapp.pedigree.presentation

import com.agrotech.oviapp.auth.domain.JwtTokenPort
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.RanchAccess
import com.agrotech.oviapp.domain.model.Role
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.domain.port.RanchMembershipPort
import com.agrotech.oviapp.pedigree.application.GetPedigreeUseCase
import com.agrotech.oviapp.pedigree.domain.Lineage
import com.agrotech.oviapp.pedigree.domain.Pedigree
import com.agrotech.oviapp.pedigree.domain.PedigreeAncestor
import com.agrotech.oviapp.pedigree.domain.PedigreeAnimal
import com.agrotech.oviapp.pedigree.domain.PedigreeOffspring
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
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(PedigreeController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RanchContextInterceptor::class, WebMvcConfig::class, GlobalExceptionHandler::class, RanchAccessAuthorizer::class)
class PedigreeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getPedigreeUseCase: GetPedigreeUseCase

    @MockitoBean
    private lateinit var ranchMembershipPort: RanchMembershipPort

    @MockitoBean
    private lateinit var jwtTokenPort: JwtTokenPort

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val ownerPrincipal = TestingAuthenticationToken("producer_1", null, "ROLE_OWNER")
    private val workerPrincipal = TestingAuthenticationToken("worker_1", null, "ROLE_WORKER_BRANCH")

    @BeforeEach
    fun setUpMembership() {
        given(ranchMembershipPort.resolveAccess("producer_1", ranchId)).willReturn(RanchAccess(Role.OWNER))
        given(ranchMembershipPort.resolveAccess("producer_1", otherRanchId)).willReturn(null)
        given(ranchMembershipPort.resolveAccess("worker_1", ranchId))
            .willReturn(RanchAccess(Role.WORKER_BRANCH, mapOf("lectura" to false)))
    }

    @Test
    fun `GET pedigree returns subject, ancestors with M-P path and offspring`() {
        given(getPedigreeUseCase.execute(ranchId, animalId, 3)).willReturn(samplePedigree())

        mockMvc.get("/api/v1/animals/$animalId/pedigree") {
            principal = ownerPrincipal
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.subject.folioInterno") { value("79") }
            jsonPath("$.generations") { value(3) }
            jsonPath("$.ancestors[0].path") { value("M") }
            jsonPath("$.ancestors[0].relationship") { value("Madre") }
            jsonPath("$.ancestors[0].lineage") { value("MATERNAL") }
            jsonPath("$.ancestors[0].registroUno") { value("R-10") }
            jsonPath("$.ancestors[1].registered") { value(false) }
            jsonPath("$.ancestors[1].folioInterno") { value("SEM-EXT-7") }
            jsonPath("$.offspring[0].status") { value("DECEASED") }
        }
    }

    @Test
    fun `GET pedigree accepts up to 4 generations`() {
        given(getPedigreeUseCase.execute(ranchId, animalId, 4)).willReturn(samplePedigree())

        mockMvc.get("/api/v1/animals/$animalId/pedigree") {
            principal = ownerPrincipal
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
            param("generations", "4")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `GET pedigree of an animal in another ranch returns 404`() {
        given(getPedigreeUseCase.execute(ranchId, animalId, 3))
            .willThrow(ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo."))

        mockMvc.get("/api/v1/animals/$animalId/pedigree") {
            principal = ownerPrincipal
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ANIMAL_NOT_FOUND") }
        }
    }

    @Test
    fun `GET pedigree rejects a ranch the user cannot access`() {
        mockMvc.get("/api/v1/animals/$animalId/pedigree") {
            principal = ownerPrincipal
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, otherRanchId.toString())
        }.andExpect {
            status { isForbidden() }
        }
        verifyNoInteractions(getPedigreeUseCase)
    }

    @Test
    fun `GET pedigree requires the lectura permission for WORKER_BRANCH`() {
        mockMvc.get("/api/v1/animals/$animalId/pedigree") {
            principal = workerPrincipal
            header(RanchContextInterceptor.HEADER_ACTIVE_RANCH, ranchId.toString())
        }.andExpect {
            status { isForbidden() }
        }
        verifyNoInteractions(getPedigreeUseCase)
    }

    private fun samplePedigree() = Pedigree(
        subject = PedigreeAnimal(
            id = animalId,
            folioInterno = "79",
            registroUno = "R-79",
            sex = Sex.FEMALE,
            breed = "Dorper",
            color = "Blanco",
            breeder = "Cabaña Los Altos",
            birthDate = LocalDate.of(2024, 3, 1),
            status = AnimalStatus.ACTIVE,
            externalSireFolio = "SEM-EXT-7",
            externalDamFolio = null,
        ),
        generations = 3,
        ancestors = listOf(
            PedigreeAncestor(
                generation = 1, path = "M", relationship = "Madre", lineage = Lineage.MATERNAL, registered = true,
                animalId = UUID.randomUUID(), folioInterno = "10", registroUno = "R-10", sex = Sex.FEMALE,
                breed = "Dorper", color = "Blanco", breeder = "Cabaña Los Altos",
                birthDate = LocalDate.of(2020, 1, 1), status = AnimalStatus.ACTIVE,
            ),
            PedigreeAncestor(
                generation = 1, path = "P", relationship = "Padre", lineage = Lineage.PATERNAL, registered = false,
                animalId = null, folioInterno = "SEM-EXT-7", registroUno = null, sex = Sex.MALE,
                breed = null, color = null, breeder = null, birthDate = null, status = null,
            ),
        ),
        offspring = listOf(
            PedigreeOffspring(
                id = UUID.randomUUID(), folioInterno = "101", registroUno = null, sex = Sex.MALE,
                birthDate = LocalDate.of(2026, 8, 1), status = AnimalStatus.DECEASED, deathDate = LocalDate.of(2026, 9, 1),
            ),
        ),
    )
}
