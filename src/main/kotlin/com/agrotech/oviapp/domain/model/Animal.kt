package com.agrotech.oviapp.domain.model

import java.time.LocalDate
import java.util.UUID

enum class Sex { FEMALE, MALE }
enum class LifeStage { LAMB_LACTATING, LAMB_FATTENING, EWE_REPLACEMENT, ADULT }
enum class ReproductiveStatus { EMPTY, PREGNANT, LACTATING, STUD_RAM }
enum class AnimalStatus { ACTIVE, SOLD, DECEASED }
enum class ParturitionType { SIMPLE, TWIN, TRIPLET, QUADRUPLET, QUINTUPLET }
enum class ConceptionMethod { MN, IA, TE }
enum class Role { OWNER, WORKER_BRANCH, VET_TECHNICIAN, ASSOCIATION_SUPERUSER }

data class Animal(
    val id: UUID,
    val ranchId: UUID,
    val folioInterno: String,
    val siniigaChip: String? = null,
    val registroUno: String? = null,
    val adn: String? = null,
    val breed: String,
    val color: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val weaningDate: LocalDate? = null,
    val parturitionType: ParturitionType = ParturitionType.SIMPLE,
    val conceptionMethod: ConceptionMethod = ConceptionMethod.MN,
    val sireId: UUID? = null,
    val damId: UUID? = null,
    val externalSireFolio: String? = null,
    val externalDamFolio: String? = null,
    val lifeStage: LifeStage,
    val reproductiveStatus: ReproductiveStatus,
    val status: AnimalStatus = AnimalStatus.ACTIVE
)
