package io.github.bayang.jelu.dto

import java.time.Instant
import java.util.UUID

data class PhysicalLocationDto(
    val id: UUID?,
    val name: String,
    val creationDate: Instant?,
    val modificationDate: Instant?,
)

data class CreatePhysicalLocationDto(
    val name: String,
)

data class UpdatePhysicalLocationDto(
    val name: String,
)

data class PhysicalBookcaseDto(
    val id: UUID?,
    val name: String,
    val locationId: UUID,
    val locationName: String?,
    val shelfCount: Int,
    val description: String?,
    val sortOrder: Int,
    val creationDate: Instant?,
    val modificationDate: Instant?,
    val shelves: List<PhysicalShelfDto>? = null,
)

data class CreatePhysicalBookcaseDto(
    val name: String,
    val shelfCount: Int,
    val description: String? = null,
    val sortOrder: Int = 0,
)

data class UpdatePhysicalBookcaseDto(
    val name: String? = null,
    val shelfCount: Int? = null,
    val description: String? = null,
    val sortOrder: Int? = null,
)

data class PhysicalShelfDto(
    val id: UUID?,
    val bookcaseId: UUID,
    val bookcaseName: String?,
    val position: Int,
    val label: String?,
    val tagId: UUID?,
    val tagName: String?,
    val creationDate: Instant?,
    val modificationDate: Instant?,
)

data class UpdatePhysicalShelfDto(
    val label: String? = null,
    val tagId: UUID? = null,
)

data class PhysicalShelfBookDto(
    val id: UUID?,
    val shelfId: UUID,
    val userBookId: UUID,
    val position: Int,
)

data class AssignBookToShelfDto(
    val userBookId: UUID,
    val position: Int? = null,
)

data class BulkAssignBooksToShelfDto(
    val userBookIds: List<UUID>,
)

data class ReorderShelfBooksDto(
    val userBookIds: List<UUID>,
)

data class ShelfLocationDto(
    val bookcaseNumber: Int,
    val locationName: String,
    val bookcaseName: String,
    val shelfLabel: String,
    val shelfPosition: Int,
    val displayString: String,
)
