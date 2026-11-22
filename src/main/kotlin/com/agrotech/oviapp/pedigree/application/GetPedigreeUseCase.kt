package com.agrotech.oviapp.pedigree.application

import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.pedigree.domain.Pedigree
import com.agrotech.oviapp.pedigree.domain.PedigreeAssembler
import com.agrotech.oviapp.pedigree.domain.PedigreePolicy
import com.agrotech.oviapp.pedigree.domain.PedigreeReadPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetPedigreeUseCase(
    private val pedigreeReadPort: PedigreeReadPort,
) {
    @Transactional(readOnly = true)
    fun execute(ranchId: UUID, animalId: UUID, generations: Int): Pedigree {
        PedigreePolicy.validateGenerations(generations)
        val subject = pedigreeReadPort.findSubject(animalId, ranchId)
            ?: throw ResourceNotFoundException("ANIMAL_NOT_FOUND", "Ejemplar no encontrado en el rancho activo.")
        return PedigreeAssembler.assemble(
            subject = subject,
            ancestorRows = pedigreeReadPort.findAncestors(animalId, ranchId, generations),
            offspring = pedigreeReadPort.findOffspring(animalId, ranchId),
            generations = generations,
        )
    }
}
