package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.CreatePhysicalBookcaseDto
import io.github.bayang.jelu.dto.UpdatePhysicalBookcaseDto
import io.github.bayang.jelu.utils.nowInstant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.springframework.stereotype.Repository
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Repository
class PhysicalBookcaseRepository {
    fun findByLocation(locationId: UUID): List<PhysicalBookcase> =
        PhysicalBookcaseTable
            .selectAll()
            .andWhere { PhysicalBookcaseTable.location eq locationId }
            .orderBy(PhysicalBookcaseTable.sortOrder, SortOrder.ASC)
            .map { PhysicalBookcase.wrapRow(it) }

    fun findAll(): List<PhysicalBookcase> =
        PhysicalBookcaseTable
            .selectAll()
            .orderBy(PhysicalBookcaseTable.sortOrder, SortOrder.ASC)
            .map { PhysicalBookcase.wrapRow(it) }

    fun findById(id: UUID): PhysicalBookcase = PhysicalBookcase[id]

    fun save(
        locationId: UUID,
        dto: CreatePhysicalBookcaseDto,
    ): PhysicalBookcase {
        val instant = nowInstant()
        return PhysicalBookcase.new {
            this.name = dto.name
            this.location = PhysicalLocation[locationId]
            this.shelfCount = dto.shelfCount
            this.description = dto.description
            this.sortOrder = dto.sortOrder
            this.creationDate = instant
            this.modificationDate = instant
        }
    }

    fun update(
        id: UUID,
        dto: UpdatePhysicalBookcaseDto,
    ): PhysicalBookcase =
        PhysicalBookcase[id].apply {
            dto.name?.let { this.name = it }
            dto.shelfCount?.let { this.shelfCount = it }
            dto.description?.let { this.description = it }
            dto.sortOrder?.let { this.sortOrder = it }
            this.modificationDate = nowInstant()
        }

    fun delete(id: UUID) {
        PhysicalBookcase[id].delete()
    }
}
