package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.ShelfLocationDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Repository
class PhysicalShelfBookRepository {

    fun findByShelf(shelfId: UUID): List<PhysicalShelfBook> =
        PhysicalShelfBookTable
            .selectAll()
            .andWhere { PhysicalShelfBookTable.shelf eq shelfId }
            .orderBy(PhysicalShelfBookTable.position, SortOrder.ASC)
            .map { PhysicalShelfBook.wrapRow(it) }

    fun findByUserBook(userBookId: UUID): PhysicalShelfBook? =
        PhysicalShelfBookTable
            .selectAll()
            .andWhere { PhysicalShelfBookTable.userBook eq userBookId }
            .map { PhysicalShelfBook.wrapRow(it) }
            .firstOrNull()

    fun assign(shelfId: UUID, userBookId: UUID, position: Int?): PhysicalShelfBook {
        PhysicalShelfBookTable.deleteWhere {
            PhysicalShelfBookTable.userBook eq userBookId
        }
        val actualPosition = position ?: nextPosition(shelfId)
        return PhysicalShelfBook.new {
            this.shelf = PhysicalShelf[shelfId]
            this.userBook = UserBook[userBookId]
            this.position = actualPosition
        }
    }

    fun bulkAssign(shelfId: UUID, userBookIds: List<UUID>): List<PhysicalShelfBook> {
        var pos = nextPosition(shelfId)
        return userBookIds.map { userBookId ->
            PhysicalShelfBookTable.deleteWhere {
                PhysicalShelfBookTable.userBook eq userBookId
            }
            val assignment = PhysicalShelfBook.new {
                this.shelf = PhysicalShelf[shelfId]
                this.userBook = UserBook[userBookId]
                this.position = pos
            }
            pos++
            assignment
        }
    }

    fun remove(shelfId: UUID, userBookId: UUID): Int =
        PhysicalShelfBookTable.deleteWhere {
            (PhysicalShelfBookTable.shelf eq shelfId) and (PhysicalShelfBookTable.userBook eq userBookId)
        }

    fun reorder(shelfId: UUID, userBookIds: List<UUID>) {
        userBookIds.forEachIndexed { index, userBookId ->
            PhysicalShelfBookTable.update(
                where = {
                    (PhysicalShelfBookTable.shelf eq shelfId) and (PhysicalShelfBookTable.userBook eq userBookId)
                },
            ) {
                it[position] = index
            }
        }
    }

    fun findUnassigned(pageable: Pageable): Page<UserBook> {
        val assignedUserBookIds =
            PhysicalShelfBookTable
                .select(PhysicalShelfBookTable.userBook)
                .map { it[PhysicalShelfBookTable.userBook] }

        val query = UserBookTable.selectAll()
        if (assignedUserBookIds.isNotEmpty()) {
            query.andWhere { UserBookTable.id notInList assignedUserBookIds }
        }

        val total = query.count()
        query.limit(pageable.pageSize)
        query.offset(pageable.offset)
        query.orderBy(UserBookTable.modificationDate, SortOrder.DESC)

        return PageImpl(
            query.map { UserBook.wrapRow(it) },
            pageable,
            total,
        )
    }

    fun resolveLocation(userBookId: UUID): ShelfLocationDto? {
        val row = PhysicalShelfBookTable
            .join(PhysicalShelfTable, JoinType.INNER, PhysicalShelfBookTable.shelf, PhysicalShelfTable.id)
            .join(PhysicalBookcaseTable, JoinType.INNER, PhysicalShelfTable.bookcase, PhysicalBookcaseTable.id)
            .join(PhysicalLocationTable, JoinType.INNER, PhysicalBookcaseTable.location, PhysicalLocationTable.id)
            .selectAll()
            .andWhere { PhysicalShelfBookTable.userBook eq userBookId }
            .firstOrNull() ?: return null

        val locationName = row[PhysicalLocationTable.name]
        val bookcaseName = row[PhysicalBookcaseTable.name]
        val shelfPosition = row[PhysicalShelfTable.position]
        val shelfLabel = row[PhysicalShelfTable.label]

        val shelfDisplay = if (!shelfLabel.isNullOrBlank()) {
            "$shelfLabel (Shelf $shelfPosition)"
        } else {
            "Shelf $shelfPosition"
        }

        return ShelfLocationDto(
            locationName = locationName,
            bookcaseName = bookcaseName,
            shelfLabel = shelfDisplay,
            shelfPosition = shelfPosition,
            displayString = locationName + " > " + bookcaseName + " > " + shelfDisplay,
        )
    }

    private fun nextPosition(shelfId: UUID): Int {
        val maxPos = PhysicalShelfBookTable
            .select(PhysicalShelfBookTable.position.max())
            .andWhere { PhysicalShelfBookTable.shelf eq shelfId }
            .firstOrNull()
            ?.get(PhysicalShelfBookTable.position.max())
        val base: Int = (maxPos as? Int) ?: -1
        return base + 1
    }
}

