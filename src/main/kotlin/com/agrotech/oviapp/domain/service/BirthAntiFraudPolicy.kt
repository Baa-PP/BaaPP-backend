package com.agrotech.oviapp.domain.service

import com.agrotech.oviapp.domain.model.Role
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object BirthAntiFraudPolicy {
    private const val MAX_REGISTRATION_DAYS = 30L

    fun validateInitialRegistration(birthDate: LocalDate, today: LocalDate, photoKey: String?) {
        require(!photoKey.isNullOrBlank()) {
            "Evidencia fotográfica obligatoria para registrar un nacimiento."
        }
        val daysSinceBirth = ChronoUnit.DAYS.between(birthDate, today)
        require(daysSinceBirth in 0..MAX_REGISTRATION_DAYS) {
            "Ventana de 30 días cerrada ($daysSinceBirth días). Solicite el alta auditada a la Asociación."
        }
    }

    fun validateLockedCorrection(actorRole: Role, evidencePhotoKey: String?, reason: String?) {
        require(actorRole == Role.ASSOCIATION_SUPERUSER || actorRole == Role.VET_TECHNICIAN) {
            "Los productores no pueden editar nacimientos guardados; solo Asociación o Técnico."
        }
        require(!evidencePhotoKey.isNullOrBlank() && !reason.isNullOrBlank()) {
            "La corrección de nacimiento requiere fotografía de evidencia y motivo."
        }
    }
}
