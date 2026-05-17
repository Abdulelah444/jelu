package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.PhysicalBookcaseRepository
import io.github.bayang.jelu.dao.PhysicalLocationRepository
import io.github.bayang.jelu.dao.PhysicalShelfBookRepository
import io.github.bayang.jelu.dao.PhysicalShelfRepository
import io.github.bayang.jelu.dto.AssignBookToShelfDto
import io.github.bayang.jelu.dto.BulkAssignBooksToShelfDto
import io.github.bayang.jelu.dto.CreatePhysicalBookcaseDto
import io.github.bayang.jelu.dto.CreatePhysicalLocationDto
import io.github.bayang.jelu.dto.PhysicalBookcaseDto
import io.github.bayang.jelu.dto.PhysicalLocationDto
import io.github.bayang.jelu.dto.PhysicalShelfBookDto
import io.github.bayang.jelu.dto.PhysicalShelfDto
import io.github.bayang.jelu.dto.ReorderShelfBooksDto
import io.github.bayang.jelu.dto.ShelfLocationDto
import io.github.bayang.jelu.dto.UpdatePhysicalBookcaseDto
import io.github.bayang.jelu.dto.UpdatePhysicalLocationDto
import io.github.bayang.jelu.dto.UpdatePhysicalShelfDto
import io.github.bayang.jelu.dto.UserBookLightDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class PhysicalLibraryService(
    private val locationRepository: PhysicalLocationRepository,
    private val bookcaseRepository: PhysicalBookcaseRepository,
    private val shelfRepository: PhysicalShelfRepository,
    private val shelfBookRepository: PhysicalShelfBookRepository,
) {
    @Transactional
    fun findAllLocations(): List<PhysicalLocationDto> = locationRepository.findAll().map { it.toDto() }

    @Transactional
    fun findLocationById(id: UUID): PhysicalLocationDto = locationRepository.findById(id).toDto()

    @Transactional
    fun createLocation(dto: CreatePhysicalLocationDto): PhysicalLocationDto = locationRepository.save(dto).toDto()

    @Transactional
    fun updateLocation(
        id: UUID,
        dto: UpdatePhysicalLocationDto,
    ): PhysicalLocationDto = locationRepository.update(id, dto).toDto()

    @Transactional
    fun deleteLocation(id: UUID) = locationRepository.delete(id)

    @Transactional
    fun findBookcasesByLocation(locationId: UUID): List<PhysicalBookcaseDto> =
        bookcaseRepository.findByLocation(locationId).map { it.toDto() }

    @Transactional
    fun findBookcaseById(id: UUID): PhysicalBookcaseDto {
        val bookcase = bookcaseRepository.findById(id)
        val shelves = shelfRepository.findByBookcase(id).map { it.toDto() }
        return bookcase.toDto().copy(shelves = shelves)
    }

    @Transactional
    fun createBookcase(
        locationId: UUID,
        dto: CreatePhysicalBookcaseDto,
    ): PhysicalBookcaseDto {
        val bookcase = bookcaseRepository.save(locationId, dto)
        val shelves = shelfRepository.createShelvesForBookcase(bookcase.id.value, dto.shelfCount)
        return bookcase.toDto().copy(shelves = shelves.map { it.toDto() })
    }

    @Transactional
    fun updateBookcase(
        id: UUID,
        dto: UpdatePhysicalBookcaseDto,
    ): PhysicalBookcaseDto {
        val existing = bookcaseRepository.findById(id)
        val oldCount = existing.shelfCount
        val updated = bookcaseRepository.update(id, dto)
        if (dto.shelfCount != null && dto.shelfCount != oldCount) {
            shelfRepository.adjustShelvesForBookcase(id, oldCount, dto.shelfCount)
        }
        val shelves = shelfRepository.findByBookcase(id).map { it.toDto() }
        return updated.toDto().copy(shelves = shelves)
    }

    @Transactional
    fun deleteBookcase(id: UUID) = bookcaseRepository.delete(id)

    @Transactional
    fun findShelvesByBookcase(bookcaseId: UUID): List<PhysicalShelfDto> = shelfRepository.findByBookcase(bookcaseId).map { it.toDto() }

    @Transactional
    fun updateShelf(
        id: UUID,
        dto: UpdatePhysicalShelfDto,
    ): PhysicalShelfDto = shelfRepository.update(id, dto).toDto()

    @Transactional
    fun findBooksOnShelf(shelfId: UUID): List<PhysicalShelfBookDto> = shelfBookRepository.findByShelf(shelfId).map { it.toDto() }

    @Transactional
    fun assignBookToShelf(
        shelfId: UUID,
        dto: AssignBookToShelfDto,
    ): PhysicalShelfBookDto = shelfBookRepository.assign(shelfId, dto.userBookId, dto.position).toDto()

    @Transactional
    fun bulkAssignBooksToShelf(
        shelfId: UUID,
        dto: BulkAssignBooksToShelfDto,
    ): List<PhysicalShelfBookDto> = shelfBookRepository.bulkAssign(shelfId, dto.userBookIds).map { it.toDto() }

    @Transactional
    fun removeBookFromShelf(
        shelfId: UUID,
        userBookId: UUID,
    ) {
        shelfBookRepository.remove(shelfId, userBookId)
    }

    @Transactional
    fun reorderBooksOnShelf(
        shelfId: UUID,
        dto: ReorderShelfBooksDto,
    ) {
        shelfBookRepository.reorder(shelfId, dto.userBookIds)
    }

    @Transactional
    fun findUnassignedBooks(pageable: Pageable): Page<UserBookLightDto> =
        shelfBookRepository.findUnassigned(pageable).map { it.toUserBookLightDto() }

    @Transactional
    fun resolveLocation(userBookId: UUID): ShelfLocationDto? = shelfBookRepository.resolveLocation(userBookId)
}
