package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.PhysicalLocationDto
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PhysicalLocationTable : UUIDTable("physical_location") {
    val name: Column<String> = varchar("name", 1000)
    val creationDate = timestamp("creation_date")
    val modificationDate = timestamp("modification_date")
}

class PhysicalLocation(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PhysicalLocation>(PhysicalLocationTable)

    var name by PhysicalLocationTable.name
    var creationDate by PhysicalLocationTable.creationDate
    var modificationDate by PhysicalLocationTable.modificationDate

    fun toDto(): PhysicalLocationDto =
        PhysicalLocationDto(
            id = this.id.value,
            name = this.name,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
        )
}
