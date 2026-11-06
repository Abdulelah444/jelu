package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.UpdatePhysicalShelfDto
import io.github.bayang.jelu.utils.nowInstant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.springframework.stereotype.Repository
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Repository
class PhysicalShelfRepository {
    fun findByBookcase(bookcaseId: UUID): List<PhysicalShelf> =
        PhysicalShelfTable
            .selectAll()
            .andWhere { PhysicalShelfTable.bookcase eq bookcaseId }
            .orderBy(PhysicalShelfTable.position, SortOrder.ASC)
            .map { PhysicalShelf.wrapRow(it) }

    fun findById(id: UUID): PhysicalShelf = PhysicalShelf[id]

    fun update(
        id: UUID,
        dto: UpdatePhysicalShelfDto,
    ): PhysicalShelf =
        PhysicalShelf[id].apply {
            dto.label?.let { this.label = it }
            if (dto.tagId != null) {
                this.tag = Tag[dto.tagId]
            }
            dto.position?.let { this.position = it }
            if (dto.bookcaseId != null) {
                this.bookcase = PhysicalBookcase[dto.bookcaseId]
            }
            this.modificationDate = nowInstant()
        }

    fun createShelvesForBookcase(
        bookcaseId: UUID,
        shelfCount: Int,
    ): List<PhysicalShelf> {
        val instant = nowInstant()
        val bookcase = PhysicalBookcase[bookcaseId]
        return (1..shelfCount).map { position ->
            PhysicalShelf.new {
                this.bookcase = bookcase
                this.position = position
                this.label = null
                this.tag = null
                this.creationDate = instant
                this.modificationDate = instant
            }
        }
    }

    fun adjustShelvesForBookcase(
        bookcaseId: UUID,
        oldCount: Int,
        newCount: Int,
    ) {
        if (newCount > oldCount) {
            val instant = nowInstant()
            val bookcase = PhysicalBookcase[bookcaseId]
            ((oldCount + 1)..newCount).forEach { position ->
                PhysicalShelf.new {
                    this.bookcase = bookcase
                    this.position = position
                    this.label = null
                    this.tag = null
                    this.creationDate = instant
                    this.modificationDate = instant
                }
            }
        }
    }

    fun delete(id: UUID) {
        PhysicalShelf[id].delete()
    }
}
