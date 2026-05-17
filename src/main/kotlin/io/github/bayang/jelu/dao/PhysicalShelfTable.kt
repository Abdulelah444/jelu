package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.PhysicalShelfDto
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PhysicalShelfTable : UUIDTable("physical_shelf") {
    val bookcase = reference("bookcase", PhysicalBookcaseTable, onDelete = ReferenceOption.CASCADE)
    val position: Column<Int> = integer("position")
    val label: Column<String?> = varchar("label", 1000).nullable()
    val tag = reference("tag", TagTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val creationDate = timestamp("creation_date")
    val modificationDate = timestamp("modification_date")
}

class PhysicalShelf(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PhysicalShelf>(PhysicalShelfTable)

    var bookcase by PhysicalBookcase referencedOn PhysicalShelfTable.bookcase
    var position by PhysicalShelfTable.position
    var label by PhysicalShelfTable.label
    var tag by Tag optionalReferencedOn PhysicalShelfTable.tag
    var creationDate by PhysicalShelfTable.creationDate
    var modificationDate by PhysicalShelfTable.modificationDate

    fun toDto(): PhysicalShelfDto =
        PhysicalShelfDto(
            id = this.id.value,
            bookcaseId = this.bookcase.id.value,
            bookcaseName = this.bookcase.name,
            position = this.position,
            label = this.label,
            tagId = this.tag?.id?.value,
            tagName = this.tag?.name,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
        )
}
