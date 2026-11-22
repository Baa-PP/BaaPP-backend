package com.agrotech.oviapp.animals.application

import com.agrotech.oviapp.animals.domain.AnimalDetail
import com.agrotech.oviapp.animals.domain.AnimalPhotoUrlPort
import com.agrotech.oviapp.animals.domain.AnimalRepositoryPort
import com.agrotech.oviapp.animals.domain.AnimalWeightReadPort
import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetAnimalDetailUseCase(
    private val animalRepository: AnimalRepositoryPort,
    private val animalWeightReadPort: AnimalWeightReadPort,
    private val animalPhotoUrlPort: AnimalPhotoUrlPort,
) {
    @Transactional(readOnly = true)
    fun execute(ranchId: UUID, animalId: UUID): AnimalDetail {
        val detail = animalRepository.findDetailByIdAndRanchId(animalId, ranchId)
            ?: throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo.")

        val weights = animalWeightReadPort.findAllByAnimalAndRanchOrderedByDateAsc(animalId, ranchId)
        val photoUrl = detail.photoS3Key
            ?.takeIf { it.isNotBlank() }
            ?.let { key -> animalPhotoUrlPort.generatePresignedDownloadUrl(ranchId, key) }

        return detail.copy(
            weights = weights,
            photoUrl = photoUrl,
        )
    }
}
