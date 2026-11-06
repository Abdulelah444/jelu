package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.dao.BookTable
import io.github.bayang.jelu.dao.UserBookTable
import io.github.bayang.jelu.dao.UserTable
import io.github.bayang.jelu.dao.PhysicalShelfBookRepository
import io.github.bayang.jelu.service.BookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.transaction.annotation.Transactional
import org.jetbrains.exposed.sql.*
import io.github.bayang.jelu.dao.LendingHistory
import io.github.bayang.jelu.dao.LendingHistoryTable
import io.github.bayang.jelu.dao.LendingHistoryDto
import io.github.bayang.jelu.dao.UserBook
import org.jetbrains.exposed.sql.and
import java.util.UUID

@RestController
@RequestMapping("/api/v1/public")
class PublicBookController(
    private val bookService: BookService,
    private val shelfBookRepository: PhysicalShelfBookRepository,
) {
    @PostMapping("/book/{bookId}/return")
    @Transactional
    fun returnBook(@PathVariable bookId: UUID): ResponseEntity<Map<String, String>> {
        val row = UserBookTable
            .join(BookTable, JoinType.INNER, UserBookTable.book, BookTable.id)
            .selectAll()
            .andWhere { BookTable.id eq bookId }
            .firstOrNull() ?: return ResponseEntity.notFound().build()
        val userBookId = row[UserBookTable.id].value
        UserBookTable.update({ UserBookTable.id eq userBookId }) {
            it[UserBookTable.borrowed] = false
            it[UserBookTable.borrowerName] = null
            it[UserBookTable.borrowDate] = null
            it[UserBookTable.expectedReturnDate] = null
        }
        val openLending = LendingHistory.find { LendingHistoryTable.userBook eq userBookId and (LendingHistoryTable.returnDate eq null) }.firstOrNull()
        if (openLending != null) {
            openLending.returnDate = java.time.Instant.now()
        }
        return ResponseEntity.ok(mapOf("status" to "ok", "message" to "Book returned"))
    }

    @PostMapping("/book/{bookId}/borrow")
    @Transactional
    fun borrowBook(@PathVariable bookId: UUID, @RequestBody body: Map<String, String>): ResponseEntity<Map<String, String>> {
        val borrowerName = body["borrowerName"] ?: return ResponseEntity.badRequest().build()
        val row = UserBookTable
            .join(BookTable, JoinType.INNER, UserBookTable.book, BookTable.id)
            .selectAll()
            .andWhere { BookTable.id eq bookId }
            .firstOrNull() ?: return ResponseEntity.notFound().build()
        val userBookId = row[UserBookTable.id].value
        UserBookTable.update({ UserBookTable.id eq userBookId }) {
            it[UserBookTable.borrowed] = true
            it[UserBookTable.borrowerName] = borrowerName
            it[UserBookTable.borrowDate] = java.time.Instant.now()
        }
        LendingHistory.new {
            this.creationDate = java.time.Instant.now()
            this.userBook = UserBook[userBookId]
            this.borrowerName = borrowerName
            this.lendDate = java.time.Instant.now()
        }
        return ResponseEntity.ok(mapOf("status" to "ok", "message" to "Book borrowed by $borrowerName"))
    }


    @GetMapping("/book/{bookId}")
    @Transactional
    fun getBookPublicInfo(@PathVariable bookId: UUID): ResponseEntity<Map<String, Any?>> {
        val row = UserBookTable
            .join(BookTable, JoinType.INNER, UserBookTable.book, BookTable.id)
            .join(UserTable, JoinType.INNER, UserBookTable.user, UserTable.id)
            .selectAll()
            .andWhere { BookTable.id eq bookId }
            .firstOrNull() ?: return ResponseEntity.notFound().build()

        val userBookId = row[UserBookTable.id].value
        val location = try { shelfBookRepository.resolveLocation(userBookId) } catch (e: Exception) { null }

        val info = mapOf(
            "title" to row[BookTable.title],
            "image" to row[BookTable.image],
            "pageCount" to row[BookTable.pageCount],
            "publisher" to row[BookTable.publisher],
            "language" to row[BookTable.language],
            "owner" to row[UserTable.login],
            "owned" to row[UserBookTable.owned],
            "borrowed" to row[UserBookTable.borrowed],
            "borrowerName" to row[UserBookTable.borrowerName],
            "ownerName" to row[UserBookTable.ownerName],
            "lastReadingEvent" to row[UserBookTable.lastReadingEvent]?.name,
            "location" to location?.displayString,
            "bookcaseName" to location?.bookcaseName,
            "shelfLabel" to location?.shelfLabel,
        )
        return ResponseEntity.ok(info)
    }
    @GetMapping("/lending-history")
    @Transactional
    fun getLendingHistory(): List<LendingHistoryDto> {
        return LendingHistory.all().sortedByDescending { it.lendDate }.map { it.toDto() }
    }
}
