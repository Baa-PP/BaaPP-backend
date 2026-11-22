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
import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.ConceptionMethod
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ParturitionType
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import com.agrotech.oviapp.presentation.security.RanchAccessAuthorizer
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CreateAnimalRequest(
    val id: UUID? = null,

    @field:NotBlank
    @field:Size(max = 60)
    val folioInterno: String = "",

    val siniigaChip: String? = null,

    @field:Size(max = 60)
    val registroUno: String? = null,

    @field:Size(max = 60)
    val adn: String? = null,

    @field:NotBlank
    @field:Size(max = 80)
    val breed: String = "",

    @field:NotBlank
    @field:Size(max = 60)
    val color: String = "",

    @field:NotNull
    val sex: Sex? = null,

    @field:NotNull
    val birthDate: LocalDate? = null,

    val weaningDate: LocalDate? = null,
    val entryReason: EntryReason = EntryReason.BIRTH,

    @field:Size(max = 150)
    val criador: String? = null,

    val parturitionType: ParturitionType = ParturitionType.SIMPLE,
    val conceptionMethod: ConceptionMethod = ConceptionMethod.MN,
    val sireId: UUID? = null,
    val damId: UUID? = null,

    @field:Size(max = 60)
    val externalSireFolio: String? = null,

    @field:Size(max = 60)
    val externalDamFolio: String? = null,

    @field:NotNull
    val lifeStage: LifeStage? = null,

    @field:NotNull
    val reproductiveStatus: ReproductiveStatus? = null,

    val status: AnimalStatus = AnimalStatus.ACTIVE,
    val mortalityDate: LocalDate? = null,
    val mortalityCause: String? = null,
    val lifeStageAtDeath: LifeStage? = null,
    val photoS3Key: String? = null,
)

data class UpdateAnimalRequest(
    @field:NotBlank
    @field:Size(max = 60)
    val folioInterno: String = "",

    val siniigaChip: String? = null,

    @field:Size(max = 60)
    val registroUno: String? = null,

    @field:Size(max = 60)
    val adn: String? = null,

    @field:NotBlank
    @field:Size(max = 80)
    val breed: String = "",

    @field:NotBlank
    @field:Size(max = 60)
    val color: String = "",

    @field:NotNull
    val sex: Sex? = null,

    @field:NotNull
    val birthDate: LocalDate? = null,

    val weaningDate: LocalDate? = null,
    val entryReason: EntryReason? = null,

    @field:Size(max = 150)
    val criador: String? = null,

    val parturitionType: ParturitionType? = null,
    val conceptionMethod: ConceptionMethod? = null,
    val sireId: UUID? = null,
    val damId: UUID? = null,

    @field:Size(max = 60)
    val externalSireFolio: String? = null,

    @field:Size(max = 60)
    val externalDamFolio: String? = null,

    @field:NotNull
    val lifeStage: LifeStage? = null,

    @field:NotNull
    val reproductiveStatus: ReproductiveStatus? = null,

    val status: AnimalStatus? = null,
    val mortalityDate: LocalDate? = null,
    val mortalityCause: String? = null,
    val lifeStageAtDeath: LifeStage? = null,
    val photoS3Key: String? = null,
)

data class AnimalSummaryResponse(
    val id: UUID,
    val ranchId: UUID,
    val folioInterno: String,
    val siniigaChip: String?,
    val registroUno: String?,
    val sex: Sex,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val breed: String,
    val color: String,
    val birthDate: LocalDate,
    val ageDays: Int,
    val weaningDate: LocalDate?,
    val entryReason: EntryReason,
    val criador: String?,
    val propietario: String,
    val photoS3Key: String?,
    val latestWeightKg: BigDecimal?,
    val latestWeightDate: LocalDate?,
    val birthCount: Int,
    val pregnant: Boolean,
    val status: AnimalStatus,
    val mortalityDate: LocalDate?,
    val mortalityCause: String?,
    val sireId: UUID?,
    val damId: UUID?,
    val updatedAt: Instant,
)

data class AnimalPageResponse(
    val items: List<AnimalSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class AnimalWeightResponse(
    val id: UUID,
    val milestone: String,
    val weightKg: BigDecimal,
    val scrotalCircumferenceCm: BigDecimal?,
    val date: LocalDate,
)

data class AnimalDetailResponse(
    val id: UUID,
    val ranchId: UUID,
    val folioInterno: String,
    val siniigaChip: String?,
    val registroUno: String?,
    val adn: String?,
    val breed: String,
    val color: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val ageDays: Int,
    val weaningDate: LocalDate?,
    val entryReason: EntryReason,
    val criador: String?,
    val propietario: String,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val status: AnimalStatus,
    val parturitionType: ParturitionType,
    val conceptionMethod: ConceptionMethod,
    val pregnant: Boolean,
    val birthCount: Int,
    val mortalityDate: LocalDate?,
    val mortalityCause: String?,
    val lifeStageAtDeath: LifeStage?,
    val sireId: UUID?,
    val damId: UUID?,
    val externalSireFolio: String?,
    val externalDamFolio: String?,
    val birthEventId: UUID?,
    val latestWeightKg: BigDecimal?,
    val latestWeightDate: LocalDate?,
    val weights: List<AnimalWeightResponse>,
    val photoS3Key: String?,
    val photoUrl: String?,
    val updatedAt: Instant,
)

data class AnimalResponse(
    val id: UUID,
    val ranchId: UUID,
    val folioInterno: String,
    val siniigaChip: String?,
    val registroUno: String?,
    val adn: String?,
    val breed: String,
    val color: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val weaningDate: LocalDate?,
    val entryReason: EntryReason,
    val criador: String?,
    val parturitionType: ParturitionType,
    val conceptionMethod: ConceptionMethod,
    val sireId: UUID?,
    val damId: UUID?,
    val externalSireFolio: String?,
    val externalDamFolio: String?,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val status: AnimalStatus,
    val mortalityDate: LocalDate?,
    val mortalityCause: String?,
    val lifeStageAtDeath: LifeStage?,
    val photoS3Key: String?,
    val birthEventId: UUID?,
    val updatedAt: Instant,
)

@RestController
@RequestMapping("/api/v1/animals")
class AnimalsController(
    private val ranchAccessAuthorizer: RanchAccessAuthorizer,
    private val listAnimalsUseCase: ListAnimalsUseCase,
    private val getAnimalDetailUseCase: GetAnimalDetailUseCase,
    private val createAnimalUseCase: CreateAnimalUseCase,
    private val updateAnimalUseCase: UpdateAnimalUseCase,
) {

    @GetMapping
    fun listAnimals(
        authentication: Authentication?,
        @RequestParam(name = "q", required = false) search: String?,
        @RequestParam(name = "sexo", required = false) sex: Sex?,
        @RequestParam(name = "etapa_vida", required = false) lifeStage: LifeStage?,
        @RequestParam(name = "estado_reproductivo", required = false) reproductiveStatus: ReproductiveStatus?,
        @RequestParam(name = "raza", required = false) breed: String?,
        @RequestParam(name = "estado", required = false) status: AnimalStatus?,
        @RequestParam(name = "updated_after", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        updatedAfter: OffsetDateTime?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
    ): AnimalPageResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return listAnimalsUseCase.execute(
            ListAnimalsQuery(
                ranchId = ranchId,
                search = search,
                sex = sex,
                lifeStage = lifeStage,
                reproductiveStatus = reproductiveStatus,
                breed = breed,
                status = status,
                updatedAfter = updatedAfter?.toInstant(),
                page = page,
                size = size,
            ),
        ).toResponse()
    }

    @GetMapping("/{id}")
    fun getAnimalDetail(
        authentication: Authentication?,
        @PathVariable("id") animalId: UUID,
    ): AnimalDetailResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return getAnimalDetailUseCase.execute(ranchId = ranchId, animalId = animalId).toResponse()
    }

    @PostMapping
    fun createAnimal(
        authentication: Authentication?,
        @Valid @RequestBody request: CreateAnimalRequest,
    ): ResponseEntity<AnimalResponse> {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        val created = createAnimalUseCase.execute(
            CreateAnimalCommand(
                id = request.id,
                ranchId = ranchId,
                folioInterno = request.folioInterno,
                siniigaChip = request.siniigaChip,
                registroUno = request.registroUno,
                adn = request.adn,
                breed = request.breed,
                color = request.color,
                sex = request.sex ?: throw invalidPayload("sex"),
                birthDate = request.birthDate ?: throw invalidPayload("birthDate"),
                weaningDate = request.weaningDate,
                entryReason = request.entryReason,
                criador = request.criador,
                parturitionType = request.parturitionType,
                conceptionMethod = request.conceptionMethod,
                sireId = request.sireId,
                damId = request.damId,
                externalSireFolio = request.externalSireFolio,
                externalDamFolio = request.externalDamFolio,
                lifeStage = request.lifeStage ?: throw invalidPayload("lifeStage"),
                reproductiveStatus = request.reproductiveStatus ?: throw invalidPayload("reproductiveStatus"),
                status = request.status,
                mortalityDate = request.mortalityDate,
                mortalityCause = request.mortalityCause,
                lifeStageAtDeath = request.lifeStageAtDeath,
                photoS3Key = request.photoS3Key,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
    }

    @PutMapping("/{id}")
    fun updateAnimal(
        authentication: Authentication?,
        @PathVariable("id") animalId: UUID,
        @Valid @RequestBody request: UpdateAnimalRequest,
    ): AnimalResponse {
        val ranchId = ranchAccessAuthorizer.requireAuthorizedRanchId(authentication)
        return updateAnimalUseCase.execute(
            UpdateAnimalCommand(
                animalId = animalId,
                ranchId = ranchId,
                folioInterno = request.folioInterno,
                siniigaChip = request.siniigaChip,
                registroUno = request.registroUno,
                adn = request.adn,
                breed = request.breed,
                color = request.color,
                sex = request.sex ?: throw invalidPayload("sex"),
                birthDate = request.birthDate ?: throw invalidPayload("birthDate"),
                weaningDate = request.weaningDate,
                entryReason = request.entryReason,
                criador = request.criador,
                parturitionType = request.parturitionType,
                conceptionMethod = request.conceptionMethod,
                sireId = request.sireId,
                damId = request.damId,
                externalSireFolio = request.externalSireFolio,
                externalDamFolio = request.externalDamFolio,
                lifeStage = request.lifeStage ?: throw invalidPayload("lifeStage"),
                reproductiveStatus = request.reproductiveStatus ?: throw invalidPayload("reproductiveStatus"),
                status = request.status,
                mortalityDate = request.mortalityDate,
                mortalityCause = request.mortalityCause,
                lifeStageAtDeath = request.lifeStageAtDeath,
                photoS3Key = request.photoS3Key,
            ),
        ).toResponse()
    }

    private fun invalidPayload(field: String) =
        DomainValidationException("VALIDATION_ERROR", "Campo obligatorio faltante: $field.")

    private fun AnimalPage.toResponse() = AnimalPageResponse(
        items = items.map { it.toResponse() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )

    private fun AnimalSummary.toResponse() = AnimalSummaryResponse(
        id = id,
        ranchId = ranchId,
        folioInterno = folioInterno,
        siniigaChip = siniigaChip,
        registroUno = registroUno,
        sex = sex,
        lifeStage = lifeStage,
        reproductiveStatus = reproductiveStatus,
        breed = breed,
        color = color,
        birthDate = birthDate,
        ageDays = ageDays,
        weaningDate = weaningDate,
        entryReason = entryReason,
        criador = criador,
        propietario = propietario,
        photoS3Key = photoS3Key,
        latestWeightKg = latestWeightKg,
        latestWeightDate = latestWeightDate,
        birthCount = birthCount,
        pregnant = pregnant,
        status = status,
        mortalityDate = mortalityDate,
        mortalityCause = mortalityCause,
        sireId = sireId,
        damId = damId,
        updatedAt = updatedAt,
    )

    private fun AnimalWeightRecord.toResponse() = AnimalWeightResponse(
        id = id,
        milestone = milestone,
        weightKg = weightKg,
        scrotalCircumferenceCm = scrotalCircumferenceCm,
        date = date,
    )

    private fun AnimalDetail.toResponse() = AnimalDetailResponse(
        id = id,
        ranchId = ranchId,
        folioInterno = folioInterno,
        siniigaChip = siniigaChip,
        registroUno = registroUno,
        adn = adn,
        breed = breed,
        color = color,
        sex = sex,
        birthDate = birthDate,
        ageDays = ageDays,
        weaningDate = weaningDate,
        entryReason = entryReason,
        criador = criador,
        propietario = propietario,
        lifeStage = lifeStage,
        reproductiveStatus = reproductiveStatus,
        status = status,
        parturitionType = parturitionType,
        conceptionMethod = conceptionMethod,
        pregnant = pregnant,
        birthCount = birthCount,
        mortalityDate = mortalityDate,
        mortalityCause = mortalityCause,
        lifeStageAtDeath = lifeStageAtDeath,
        sireId = sireId,
        damId = damId,
        externalSireFolio = externalSireFolio,
        externalDamFolio = externalDamFolio,
        birthEventId = birthEventId,
        latestWeightKg = latestWeightKg,
        latestWeightDate = latestWeightDate,
        weights = weights.map { it.toResponse() },
        photoS3Key = photoS3Key,
        photoUrl = photoUrl,
        updatedAt = updatedAt,
    )

    private fun AnimalRecord.toResponse() = AnimalResponse(
        id = id,
        ranchId = ranchId,
        folioInterno = folioInterno,
        siniigaChip = siniigaChip,
        registroUno = registroUno,
        adn = adn,
        breed = breed,
        color = color,
        sex = sex,
        birthDate = birthDate,
        weaningDate = weaningDate,
        entryReason = entryReason,
        criador = criador,
        parturitionType = parturitionType,
        conceptionMethod = conceptionMethod,
        sireId = sireId,
        damId = damId,
        externalSireFolio = externalSireFolio,
        externalDamFolio = externalDamFolio,
        lifeStage = lifeStage,
        reproductiveStatus = reproductiveStatus,
        status = status,
        mortalityDate = mortalityDate,
        mortalityCause = mortalityCause,
        lifeStageAtDeath = lifeStageAtDeath,
        photoS3Key = photoS3Key,
        birthEventId = birthEventId,
        updatedAt = updatedAt,
    )
}
