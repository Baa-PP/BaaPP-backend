package com.agrotech.oviapp.animals.application

import com.agrotech.oviapp.animals.domain.AnimalPage
import com.agrotech.oviapp.animals.domain.AnimalPolicy
import com.agrotech.oviapp.animals.domain.AnimalRepositoryPort
import com.agrotech.oviapp.animals.domain.ListAnimalsQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListAnimalsUseCase(
    private val animalRepository: AnimalRepositoryPort,
) {
    @Transactional(readOnly = true)
    fun execute(query: ListAnimalsQuery): AnimalPage {
        AnimalPolicy.validatePagination(page = query.page, size = query.size)
        return animalRepository.searchSummaries(query)
    }
}
