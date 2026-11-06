package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.CreatePhysicalLocationDto
import io.github.bayang.jelu.dto.UpdatePhysicalLocationDto
import io.github.bayang.jelu.utils.nowInstant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.springframework.stereotype.Repository
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Repository
class PhysicalLocationRepository {
    fun findAll(): List<PhysicalLocation> =
        PhysicalLocationTable
            .selectAll()
            .orderBy(PhysicalLocationTable.name, SortOrder.ASC)
            .map { PhysicalLocation.wrapRow(it) }

    fun findById(id: UUID): PhysicalLocation = PhysicalLocation[id]

    fun save(dto: CreatePhysicalLocationDto): PhysicalLocation {
        val instant = nowInstant()
        return PhysicalLocation.new {
            this.name = dto.name
            this.creationDate = instant
            this.modificationDate = instant
        }
    }

    fun update(
        id: UUID,
        dto: UpdatePhysicalLocationDto,
    ): PhysicalLocation =
        PhysicalLocation[id].apply {
            this.name = dto.name
            this.modificationDate = nowInstant()
        }

    fun delete(id: UUID) {
        PhysicalLocation[id].delete()
    }
}
