package io.github.bayang.jelu.controllers

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
import io.github.bayang.jelu.service.PhysicalLibraryService
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PhysicalLibraryController(
    private val physicalLibraryService: PhysicalLibraryService,
) {

    @GetMapping(path = ["/physical-locations"])
    fun getAllLocations(principal: Authentication): List<PhysicalLocationDto> =
        physicalLibraryService.findAllLocations()

    @GetMapping(path = ["/physical-locations/{id}"])
    fun getLocationById(
        @PathVariable("id") id: UUID,
        principal: Authentication,
    ): PhysicalLocationDto = physicalLibraryService.findLocationById(id)

    @PostMapping(path = ["/physical-locations"])
    fun createLocation(
        @RequestBody @Valid dto: CreatePhysicalLocationDto,
        principal: Authentication,
    ): PhysicalLocationDto = physicalLibraryService.createLocation(dto)

    @PutMapping(path = ["/physical-locations/{id}"])
    fun updateLocation(
        @PathVariable("id") id: UUID,
        @RequestBody @Valid dto: UpdatePhysicalLocationDto,
        principal: Authentication,
    ): PhysicalLocationDto = physicalLibraryService.updateLocation(id, dto)

    @ApiResponse(responseCode = "204", description = "Deleted the location")
    @DeleteMapping(path = ["/physical-locations/{id}"])
    fun deleteLocation(
        @PathVariable("id") id: UUID,
        principal: Authentication,
    ): ResponseEntity<Unit> {
        physicalLibraryService.deleteLocation(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping(path = ["/physical-locations/{locId}/bookcases"])
    fun getBookcasesByLocation(
        @PathVariable("locId") locId: UUID,
        principal: Authentication,
    ): List<PhysicalBookcaseDto> = physicalLibraryService.findBookcasesByLocation(locId)

    @GetMapping(path = ["/physical-bookcases/{id}"])
    fun getBookcaseById(
        @PathVariable("id") id: UUID,
        principal: Authentication,
    ): PhysicalBookcaseDto = physicalLibraryService.findBookcaseById(id)

    @PostMapping(path = ["/physical-locations/{locId}/bookcases"])
    fun createBookcase(
        @PathVariable("locId") locId: UUID,
        @RequestBody @Valid dto: CreatePhysicalBookcaseDto,
        principal: Authentication,
    ): PhysicalBookcaseDto = physicalLibraryService.createBookcase(locId, dto)

    @PutMapping(path = ["/physical-bookcases/{id}"])
    fun updateBookcase(
        @PathVariable("id") id: UUID,
        @RequestBody @Valid dto: UpdatePhysicalBookcaseDto,
        principal: Authentication,
    ): PhysicalBookcaseDto = physicalLibraryService.updateBookcase(id, dto)

    @ApiResponse(responseCode = "204", description = "Deleted the bookcase")
    @DeleteMapping(path = ["/physical-bookcases/{id}"])
    fun deleteBookcase(
        @PathVariable("id") id: UUID,
        principal: Authentication,
    ): ResponseEntity<Unit> {
        physicalLibraryService.deleteBookcase(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping(path = ["/physical-bookcases/{bcId}/shelves"])
    fun getShelvesByBookcase(
        @PathVariable("bcId") bcId: UUID,
        principal: Authentication,
    ): List<PhysicalShelfDto> = physicalLibraryService.findShelvesByBookcase(bcId)

    @PutMapping(path = ["/physical-shelves/{id}"])
    fun updateShelf(
        @PathVariable("id") id: UUID,
        @RequestBody @Valid dto: UpdatePhysicalShelfDto,
        principal: Authentication,
    ): PhysicalShelfDto = physicalLibraryService.updateShelf(id, dto)

    @GetMapping(path = ["/physical-shelves/{id}/books"])
    fun getBooksOnShelf(
        @PathVariable("id") id: UUID,
        principal: Authentication,
    ): List<PhysicalShelfBookDto> = physicalLibraryService.findBooksOnShelf(id)

    @PostMapping(path = ["/physical-shelves/{shelfId}/books"])
    fun assignBookToShelf(
        @PathVariable("shelfId") shelfId: UUID,
        @RequestBody @Valid dto: AssignBookToShelfDto,
        principal: Authentication,
    ): PhysicalShelfBookDto = physicalLibraryService.assignBookToShelf(shelfId, dto)

    @PostMapping(path = ["/physical-shelves/{shelfId}/books/bulk"])
    fun bulkAssignBooks(
        @PathVariable("shelfId") shelfId: UUID,
        @RequestBody @Valid dto: BulkAssignBooksToShelfDto,
        principal: Authentication,
    ): List<PhysicalShelfBookDto> = physicalLibraryService.bulkAssignBooksToShelf(shelfId, dto)

    @PutMapping(path = ["/physical-shelves/{shelfId}/books/reorder"])
    fun reorderBooks(
        @PathVariable("shelfId") shelfId: UUID,
        @RequestBody @Valid dto: ReorderShelfBooksDto,
        principal: Authentication,
    ): ResponseEntity<Unit> {
        physicalLibraryService.reorderBooksOnShelf(shelfId, dto)
        return ResponseEntity.noContent().build()
    }

    @ApiResponse(responseCode = "204", description = "Removed the book from the shelf")
    @DeleteMapping(path = ["/physical-shelves/{shelfId}/books/{userBookId}"])
    fun removeBookFromShelf(
        @PathVariable("shelfId") shelfId: UUID,
        @PathVariable("userBookId") userBookId: UUID,
        principal: Authentication,
    ): ResponseEntity<Unit> {
        physicalLibraryService.removeBookFromShelf(shelfId, userBookId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping(path = ["/userbooks/unassigned"])
    fun getUnassignedBooks(
        @PageableDefault(page = 0, size = 20, direction = Sort.Direction.DESC, sort = ["modificationDate"]) @ParameterObject pageable: Pageable,
        principal: Authentication,
    ): Page<UserBookLightDto> = physicalLibraryService.findUnassignedBooks(pageable)

    @GetMapping(path = ["/userbooks/{id}/physical-location"])
    fun getBookPhysicalLocation(
        @PathVariable("id") userBookId: UUID,
        principal: Authentication,
    ): ResponseEntity<ShelfLocationDto> {
        val location = physicalLibraryService.resolveLocation(userBookId)
        return if (location != null) {
            ResponseEntity.ok(location)
        } else {
            ResponseEntity.noContent().build()
        }
    }
}
