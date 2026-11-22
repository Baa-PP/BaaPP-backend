package com.agrotech.oviapp.animals.domain

import com.agrotech.oviapp.domain.exception.DomainValidationException
import com.agrotech.oviapp.domain.model.AnimalStatus
import com.agrotech.oviapp.domain.model.LifeStage
import com.agrotech.oviapp.domain.model.ReproductiveStatus
import com.agrotech.oviapp.domain.model.Sex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class AnimalPolicyTest {

    private val ranchId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRanchId = UUID.fromString("99999999-9999-9999-9999-999999999999")
    private val animalId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `validateSiniigaChip accepts null, blank, and 10 to 15 digit codes starting with 484`() {
        assertNull(AnimalPolicy.normalizeAndValidateSiniigaChip(null))
        assertNull(AnimalPolicy.normalizeAndValidateSiniigaChip(""))
        assertNull(AnimalPolicy.normalizeAndValidateSiniigaChip("   "))
        assertEquals("4841234567", AnimalPolicy.normalizeAndValidateSiniigaChip("4841234567"))
        assertEquals("4841234567", AnimalPolicy.normalizeAndValidateSiniigaChip("  4841234567  "))
        assertEquals("484123456789012", AnimalPolicy.normalizeAndValidateSiniigaChip("484123456789012"))
    }

    @Test
    fun `validateSiniigaChip rejects 9 digits, 16 digits, wrong prefix, and non-digit characters`() {
        val invalidChips = listOf(
            "484123456",        // 9 digits
            "4841234567890123", // 16 digits
            "4851234567",       // wrong country prefix
            "1234567890",       // wrong prefix
            "484123456A",       // letters
            "484-1234567",      // punctuation
            "484 1234567",      // internal space
        )

        invalidChips.forEach { invalid ->
            val ex = assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.normalizeAndValidateSiniigaChip(invalid)
            }
            assertEquals("INVALID_SINIIGA_CHIP", ex.code, "Expected INVALID_SINIIGA_CHIP for '$invalid'")
        }
    }

    @Test
    fun `validateMortality enforces DECEASED requirements and defaults lifeStageAtDeath`() {
        val birthDate = LocalDate.of(2025, 1, 10)
        val deathDate = LocalDate.of(2026, 5, 1)

        val resolvedDefault = AnimalPolicy.validateMortality(
            status = AnimalStatus.DECEASED,
            sex = Sex.FEMALE,
            birthDate = birthDate,
            mortalityDate = deathDate,
            mortalityCause = "  Neumonía aguda  ",
            lifeStageAtDeath = null,
            defaultLifeStageAtDeath = LifeStage.ADULT,
        )
        assertEquals(deathDate, resolvedDefault.mortalityDate)
        assertEquals("Neumonía aguda", resolvedDefault.mortalityCause)
        assertEquals(LifeStage.ADULT, resolvedDefault.lifeStageAtDeath)

        val resolvedExplicit = AnimalPolicy.validateMortality(
            status = AnimalStatus.DECEASED,
            sex = Sex.MALE,
            birthDate = birthDate,
            mortalityDate = deathDate,
            mortalityCause = "Accidente",
            lifeStageAtDeath = LifeStage.LAMB_FATTENING,
            defaultLifeStageAtDeath = LifeStage.ADULT,
        )
        assertEquals(LifeStage.LAMB_FATTENING, resolvedExplicit.lifeStageAtDeath)

        val missingDate = assertThrows(DomainValidationException::class.java) {
            AnimalPolicy.validateMortality(
                status = AnimalStatus.DECEASED,
                sex = Sex.FEMALE,
                birthDate = birthDate,
                mortalityDate = null,
                mortalityCause = "Neumonía",
                lifeStageAtDeath = null,
                defaultLifeStageAtDeath = LifeStage.ADULT,
            )
        }
        assertEquals("MORTALITY_INFO_REQUIRED", missingDate.code)

        val blankCause = assertThrows(DomainValidationException::class.java) {
            AnimalPolicy.validateMortality(
                status = AnimalStatus.DECEASED,
                sex = Sex.FEMALE,
                birthDate = birthDate,
                mortalityDate = deathDate,
                mortalityCause = "   ",
                lifeStageAtDeath = null,
                defaultLifeStageAtDeath = LifeStage.ADULT,
            )
        }
        assertEquals("MORTALITY_INFO_REQUIRED", blankCause.code)
    }

    @Test
    fun `validateMortality rejects mortality fields for ACTIVE and SOLD animals`() {
        val birthDate = LocalDate.of(2025, 1, 10)
        val deathDate = LocalDate.of(2026, 5, 1)

        listOf(AnimalStatus.ACTIVE, AnimalStatus.SOLD).forEach { status ->
            val ok = AnimalPolicy.validateMortality(
                status = status,
                sex = Sex.FEMALE,
                birthDate = birthDate,
                mortalityDate = null,
                mortalityCause = null,
                lifeStageAtDeath = null,
                defaultLifeStageAtDeath = LifeStage.ADULT,
            )
            assertNull(ok.mortalityDate)
            assertNull(ok.mortalityCause)
            assertNull(ok.lifeStageAtDeath)

            val blankCauseOk = AnimalPolicy.validateMortality(
                status = status,
                sex = Sex.FEMALE,
                birthDate = birthDate,
                mortalityDate = null,
                mortalityCause = "   ",
                lifeStageAtDeath = null,
                defaultLifeStageAtDeath = LifeStage.ADULT,
            )
            assertNull(blankCauseOk.mortalityDate)
            assertNull(blankCauseOk.mortalityCause)
            assertNull(blankCauseOk.lifeStageAtDeath)

            assertEquals(
                "MORTALITY_FIELDS_NOT_ALLOWED",
                assertThrows(DomainValidationException::class.java) {
                    AnimalPolicy.validateMortality(status, Sex.FEMALE, birthDate, deathDate, null, null, LifeStage.ADULT)
                }.code,
            )
            assertEquals(
                "MORTALITY_FIELDS_NOT_ALLOWED",
                assertThrows(DomainValidationException::class.java) {
                    AnimalPolicy.validateMortality(status, Sex.FEMALE, birthDate, null, "Causa", null, LifeStage.ADULT)
                }.code,
            )
            assertEquals(
                "MORTALITY_FIELDS_NOT_ALLOWED",
                assertThrows(DomainValidationException::class.java) {
                    AnimalPolicy.validateMortality(status, Sex.FEMALE, birthDate, null, null, LifeStage.ADULT, LifeStage.ADULT)
                }.code,
            )
        }
    }

    @Test
    fun `validateSexAndStates enforces sex vs reproductive status and life stage orthogonality`() {
        AnimalPolicy.validateSexAndStates(Sex.MALE, LifeStage.ADULT, ReproductiveStatus.STUD_RAM)
        AnimalPolicy.validateSexAndStates(Sex.MALE, LifeStage.LAMB_FATTENING, ReproductiveStatus.EMPTY)
        AnimalPolicy.validateSexAndStates(Sex.FEMALE, LifeStage.ADULT, ReproductiveStatus.PREGNANT)
        AnimalPolicy.validateSexAndStates(Sex.FEMALE, LifeStage.ADULT, ReproductiveStatus.LACTATING)
        AnimalPolicy.validateSexAndStates(Sex.FEMALE, LifeStage.EWE_REPLACEMENT, ReproductiveStatus.EMPTY)

        assertEquals(
            "INVALID_REPRODUCTIVE_STATUS",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validateSexAndStates(Sex.MALE, LifeStage.ADULT, ReproductiveStatus.PREGNANT)
            }.code,
        )
        assertEquals(
            "INVALID_REPRODUCTIVE_STATUS",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validateSexAndStates(Sex.MALE, LifeStage.ADULT, ReproductiveStatus.LACTATING)
            }.code,
        )
        assertEquals(
            "INVALID_REPRODUCTIVE_STATUS",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validateSexAndStates(Sex.FEMALE, LifeStage.ADULT, ReproductiveStatus.STUD_RAM)
            }.code,
        )
        assertEquals(
            "INVALID_LIFE_STAGE",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validateSexAndStates(Sex.MALE, LifeStage.EWE_REPLACEMENT, ReproductiveStatus.EMPTY)
            }.code,
        )
    }

    @Test
    fun `validateDates rejects weaningDate or mortalityDate before birthDate`() {
        val birthDate = LocalDate.of(2026, 3, 15)

        AnimalPolicy.validateDates(birthDate = birthDate, weaningDate = birthDate, mortalityDate = birthDate)
        AnimalPolicy.validateDates(
            birthDate = birthDate,
            weaningDate = birthDate.plusDays(75),
            mortalityDate = birthDate.plusDays(120),
        )

        assertEquals(
            "INVALID_WEANING_DATE",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validateDates(birthDate = birthDate, weaningDate = birthDate.minusDays(1), mortalityDate = null)
            }.code,
        )
        assertEquals(
            "INVALID_MORTALITY_DATE",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validateDates(birthDate = birthDate, weaningDate = null, mortalityDate = birthDate.minusDays(1))
            }.code,
        )
    }

    @Test
    fun `validatePedigree rejects self-parenting and identical sire and dam`() {
        val sireId = UUID.randomUUID()
        val damId = UUID.randomUUID()

        AnimalPolicy.validatePedigree(animalId, sireId, damId)
        AnimalPolicy.validatePedigree(animalId, null, null)

        assertEquals(
            "INVALID_PEDIGREE",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validatePedigree(animalId, animalId, damId)
            }.code,
        )
        assertEquals(
            "INVALID_PEDIGREE",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validatePedigree(animalId, sireId, animalId)
            }.code,
        )
        assertEquals(
            "INVALID_PEDIGREE",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validatePedigree(animalId, sireId, sireId)
            }.code,
        )
    }

    @Test
    fun `validatePhotoS3Key enforces active ranch prefix and blocks path traversal`() {
        assertNull(AnimalPolicy.validatePhotoS3Key(null, ranchId))
        assertEquals(
            "$ranchId/animals/profile.webp",
            AnimalPolicy.validatePhotoS3Key("$ranchId/animals/profile.webp", ranchId),
        )

        val invalidKeys = listOf(
            "",
            "   ",
            " $ranchId/animals/profile.webp",
            "$ranchId/animals/profile.webp ",
            "$ranchId/",
            "$ranchId/animals/",
            "$otherRanchId/animals/profile.webp",
            "$ranchId/../$otherRanchId/profile.webp",
            "$ranchId/animals\\profile.webp",
            "$ranchId//animals/profile.webp",
            "$ranchId/animals/pro\u0000file.webp",
        )

        invalidKeys.forEach { key ->
            val ex = assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validatePhotoS3Key(key, ranchId)
            }
            assertEquals("INVALID_PHOTO_S3_KEY", ex.code, "Expected INVALID_PHOTO_S3_KEY for '$key'")
        }
    }

    @Test
    fun `validatePagination enforces non-negative page and size between 1 and 100`() {
        AnimalPolicy.validatePagination(page = 0, size = 1)
        AnimalPolicy.validatePagination(page = 3, size = 100)

        assertEquals(
            "INVALID_PAGINATION",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validatePagination(page = -1, size = 20)
            }.code,
        )
        assertEquals(
            "INVALID_PAGINATION",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validatePagination(page = 0, size = 0)
            }.code,
        )
        assertEquals(
            "INVALID_PAGINATION",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.validatePagination(page = 0, size = 101)
            }.code,
        )
    }

    @Test
    fun `normalizeTextFields trims required and optional fields and enforces max lengths`() {
        val normalized = AnimalPolicy.normalizeTextFields(
            folioInterno = "  OV-01  ",
            siniigaChip = " 4841234567 ",
            registroUno = "   ",
            adn = " ADN-99 ",
            breed = " Dorper ",
            color = " Blanco ",
            criador = " Rancho Origen ",
            externalSireFolio = "",
            externalDamFolio = " EXT-D ",
        )
        assertEquals("OV-01", normalized.folioInterno)
        assertEquals("4841234567", normalized.siniigaChip)
        assertNull(normalized.registroUno)
        assertEquals("ADN-99", normalized.adn)
        assertEquals("Dorper", normalized.breed)
        assertEquals("Blanco", normalized.color)
        assertEquals("Rancho Origen", normalized.criador)
        assertNull(normalized.externalSireFolio)
        assertEquals("EXT-D", normalized.externalDamFolio)

        assertEquals(
            "INVALID_FOLIO_INTERNO",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.normalizeRequiredText("   ", 60, "INVALID_FOLIO_INTERNO", "folioInterno")
            }.code,
        )
        assertEquals(
            "INVALID_FOLIO_INTERNO",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.normalizeRequiredText("A".repeat(61), 60, "INVALID_FOLIO_INTERNO", "folioInterno")
            }.code,
        )
        assertEquals(
            "INVALID_CRIADOR",
            assertThrows(DomainValidationException::class.java) {
                AnimalPolicy.normalizeOptionalText("C".repeat(151), 150, "INVALID_CRIADOR", "criador")
            }.code,
        )
    }
}
