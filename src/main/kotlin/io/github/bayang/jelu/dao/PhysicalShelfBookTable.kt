package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.PhysicalShelfBookDto
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.UUID

object PhysicalShelfBookTable : UUIDTable("physical_shelf_book") {
    val shelf = reference("shelf", PhysicalShelfTable, onDelete = ReferenceOption.CASCADE)
    val userBook = reference("user_book", UserBookTable, onDelete = ReferenceOption.CASCADE)
    val position: Column<Int> = integer("position").default(0)

    init {
        uniqueIndex("uq_physical_shelf_book_user_book", userBook)
    }
}

class PhysicalShelfBook(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PhysicalShelfBook>(PhysicalShelfBookTable)

    var shelf by PhysicalShelf referencedOn PhysicalShelfBookTable.shelf
    var userBook by UserBook referencedOn PhysicalShelfBookTable.userBook
    var position by PhysicalShelfBookTable.position

    fun toDto(): PhysicalShelfBookDto =
        PhysicalShelfBookDto(
            id = this.id.value,
            shelfId = this.shelf.id.value,
            userBookId = this.userBook.id.value,
            position = this.position,
        )
}
