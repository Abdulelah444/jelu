package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.PhysicalBookcaseDto
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PhysicalBookcaseTable : UUIDTable("physical_bookcase") {
    val name: Column<String> = varchar("name", 1000)
    val location = reference("location", PhysicalLocationTable, onDelete = ReferenceOption.CASCADE)
    val shelfCount: Column<Int> = integer("shelf_count")
    val description: Column<String?> = varchar("description", 5000).nullable()
    val sortOrder: Column<Int> = integer("sort_order").default(0)
    val creationDate = timestamp("creation_date")
    val modificationDate = timestamp("modification_date")
}

class PhysicalBookcase(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PhysicalBookcase>(PhysicalBookcaseTable)

    var name by PhysicalBookcaseTable.name
    var location by PhysicalLocation referencedOn PhysicalBookcaseTable.location
    var shelfCount by PhysicalBookcaseTable.shelfCount
    var description by PhysicalBookcaseTable.description
    var sortOrder by PhysicalBookcaseTable.sortOrder
    var creationDate by PhysicalBookcaseTable.creationDate
    var modificationDate by PhysicalBookcaseTable.modificationDate

    fun toDto(): PhysicalBookcaseDto =
        PhysicalBookcaseDto(
            id = this.id.value,
            name = this.name,
            locationId = this.location.id.value,
            locationName = this.location.name,
            shelfCount = this.shelfCount,
            description = this.description,
            sortOrder = this.sortOrder,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
        )
}
