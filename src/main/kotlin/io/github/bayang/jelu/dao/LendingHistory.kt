package io.github.bayang.jelu.dao

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

object LendingHistoryTable : UUIDTable("lending_history") {
    val creationDate: Column<Instant> = timestamp("creation_date").default(Instant.now())
    val userBook = reference("user_book", UserBookTable)
    val borrowerName: Column<String> = varchar("borrower_name", 500)
    val lendDate: Column<Instant> = timestamp("lend_date")
    val returnDate: Column<Instant?> = timestamp("return_date").nullable()
    val notes: Column<String?> = varchar("notes", 5000).nullable()
}

class LendingHistory(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<LendingHistory>(LendingHistoryTable)
    var creationDate by LendingHistoryTable.creationDate
    var userBook by UserBook referencedOn LendingHistoryTable.userBook
    var borrowerName by LendingHistoryTable.borrowerName
    var lendDate by LendingHistoryTable.lendDate
    var returnDate by LendingHistoryTable.returnDate
    var notes by LendingHistoryTable.notes

    fun toDto(): LendingHistoryDto = LendingHistoryDto(
        id = this.id.value,
        creationDate = this.creationDate,
        userBookId = this.userBook.id.value,
        bookTitle = this.userBook.book.title,
        bookId = this.userBook.book.id.value,
        bookImage = this.userBook.book.image,
        borrowerName = this.borrowerName,
        lendDate = this.lendDate,
        returnDate = this.returnDate,
        notes = this.notes,
    )
}

data class LendingHistoryDto(
    val id: UUID,
    val creationDate: Instant,
    val userBookId: UUID,
    val bookTitle: String,
    val bookId: UUID,
    val bookImage: String?,
    val borrowerName: String,
    val lendDate: Instant,
    val returnDate: Instant?,
    val notes: String?,
)
