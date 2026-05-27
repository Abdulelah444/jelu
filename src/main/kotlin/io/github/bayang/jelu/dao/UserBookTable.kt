package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.UserBookDto
import io.github.bayang.jelu.dto.UserBookLightDto
import io.github.bayang.jelu.dto.UserBookLightWithoutBookDto
import io.github.bayang.jelu.dto.UserBookWithoutEventsAndUserDto
import io.github.bayang.jelu.dto.UserBookWithoutEventsDto
import io.github.bayang.jelu.utils.centsToDouble
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

object UserBookTable : UUIDTable("user_book") {
    val creationDate = timestamp("creation_date")
    val modificationDate = timestamp("modification_date")
    val user = reference("user", UserTable, onDelete = ReferenceOption.CASCADE)
    val book = reference("book", BookTable, onDelete = ReferenceOption.CASCADE)
    val lastReadingEvent: Column<ReadingEventType?> = enumerationByName("last_reading_event", 200, ReadingEventType::class).nullable()
    val lastReadingEventDate: Column<OffsetDateTime?> = timestampWithTimeZone("last_reading_event_date").nullable()
    val personalNotes: Column<String?> = varchar("notes", 5000).nullable()
    val owned: Column<Boolean?> = bool("is_owned").nullable()
    val toRead: Column<Boolean?> = bool("to_read").nullable()
    val percentRead: Column<Int?> = integer(name = "percent_read").nullable()
    val currentPageNumber: Column<Int?> = integer(name = "current_page_number").nullable()
    val borrowed: Column<Boolean?> = bool("is_borrowed").nullable()
    val priceInCents: Column<Long?> = long("price_in_cents").nullable()
    val borrowerName: Column<String?> = varchar("borrower_name", 500).nullable()
    val borrowDate: Column<java.time.Instant?> = timestamp("borrow_date").nullable()
    val expectedReturnDate: Column<java.time.Instant?> = timestamp("expected_return_date").nullable()
    val ownerName: Column<String?> = varchar("owner_name", 500).nullable()
    val digitalFilePath: Column<String?> = varchar("digital_file_path", 1000).nullable()
    val digitalFileFormat: Column<String?> = varchar("digital_file_format", 20).nullable()
    val digitalFileSizeBytes: Column<Long?> = long("digital_file_size_bytes").nullable()
    val digitalFileAddedDate: Column<java.time.Instant?> = timestamp("digital_file_added_date").nullable()
    val lastSentToReaderDate: Column<java.time.Instant?> = timestamp("last_sent_to_reader_date").nullable()
}

class UserBook(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserBook>(UserBookTable)

    var creationDate by UserBookTable.creationDate
    var modificationDate by UserBookTable.modificationDate
    var user by User referencedOn UserBookTable.user
    var book by Book referencedOn UserBookTable.book
    var personalNotes by UserBookTable.personalNotes
    var owned by UserBookTable.owned
    var toRead by UserBookTable.toRead
    val readingEvents by ReadingEvent referrersOn ReadingEventTable.userBook // make sure to use val and referrersOn
    var lastReadingEventDate by UserBookTable.lastReadingEventDate
    var lastReadingEvent by UserBookTable.lastReadingEvent
    var percentRead by UserBookTable.percentRead
    var currentPageNumber by UserBookTable.currentPageNumber
    var borrowed by UserBookTable.borrowed
    var avgRating: Double? = null
    var userAvgRating: Double? = null
    var priceInCents by UserBookTable.priceInCents
    var borrowerName by UserBookTable.borrowerName
    var borrowDate by UserBookTable.borrowDate
    var expectedReturnDate by UserBookTable.expectedReturnDate
    var ownerName by UserBookTable.ownerName
    var digitalFilePath by UserBookTable.digitalFilePath
    var digitalFileFormat by UserBookTable.digitalFileFormat
    var digitalFileSizeBytes by UserBookTable.digitalFileSizeBytes
    var digitalFileAddedDate by UserBookTable.digitalFileAddedDate
    var lastSentToReaderDate by UserBookTable.lastSentToReaderDate

    fun toUserBookDto(): UserBookDto =
        UserBookDto(
            id = this.id.value,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
            user = this.user.toUserDto(),
            book = this.book.toBookDto(),
            lastReadingEvent = this.lastReadingEvent,
            lastReadingEventDate = this.lastReadingEventDate,
            owned = this.owned,
            toRead = this.toRead,
            personalNotes = this.personalNotes,
            percentRead = this.percentRead,
            currentPageNumber = this.currentPageNumber,
            borrowed = this.borrowed,
            borrowerName = this.borrowerName,
            borrowDate = this.borrowDate,
            expectedReturnDate = this.expectedReturnDate,
            ownerName = this.ownerName,
            digitalFilePath = this.digitalFilePath,
            digitalFileFormat = this.digitalFileFormat,
            digitalFileSizeBytes = this.digitalFileSizeBytes,
            digitalFileAddedDate = this.digitalFileAddedDate,
            lastSentToReaderDate = this.lastSentToReaderDate,
            readingEvents = this.readingEvents.map { it.toReadingEventWithoutUserBookDto() },
            price = centsToDouble(this.priceInCents),
        )

    fun toUserBookLightDto(): UserBookLightDto =
        UserBookLightDto(
            id = this.id.value,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
            book = this.book.toBookDto(),
            owned = this.owned,
            toRead = this.toRead,
            personalNotes = this.personalNotes,
            lastReadingEvent = this.lastReadingEvent,
            lastReadingEventDate = this.lastReadingEventDate,
            percentRead = this.percentRead,
            currentPageNumber = this.currentPageNumber,
            borrowed = this.borrowed,
            borrowerName = this.borrowerName,
            borrowDate = this.borrowDate,
            expectedReturnDate = this.expectedReturnDate,
            ownerName = this.ownerName,
            digitalFilePath = this.digitalFilePath,
            digitalFileFormat = this.digitalFileFormat,
            digitalFileSizeBytes = this.digitalFileSizeBytes,
            digitalFileAddedDate = this.digitalFileAddedDate,
            lastSentToReaderDate = this.lastSentToReaderDate,
            readingEvents = this.readingEvents.map { it.toReadingEventWithoutUserBookDto() },
            price = centsToDouble(this.priceInCents),
        )

    fun toUserBookLightWithoutBookDto(): UserBookLightWithoutBookDto =
        UserBookLightWithoutBookDto(
            id = this.id.value,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
            owned = this.owned,
            toRead = this.toRead,
            personalNotes = this.personalNotes,
            lastReadingEvent = this.lastReadingEvent,
            lastReadingEventDate = this.lastReadingEventDate,
            percentRead = this.percentRead,
            currentPageNumber = this.currentPageNumber,
            borrowed = this.borrowed,
            borrowerName = this.borrowerName,
            borrowDate = this.borrowDate,
            expectedReturnDate = this.expectedReturnDate,
            ownerName = this.ownerName,
            digitalFilePath = this.digitalFilePath,
            digitalFileFormat = this.digitalFileFormat,
            digitalFileSizeBytes = this.digitalFileSizeBytes,
            digitalFileAddedDate = this.digitalFileAddedDate,
            lastSentToReaderDate = this.lastSentToReaderDate,
            readingEvents = this.readingEvents.map { it.toReadingEventWithoutUserBookDto() },
            price = centsToDouble(this.priceInCents),
        )

    fun toUserBookWthoutEventsAndUserDto(): UserBookWithoutEventsAndUserDto =
        UserBookWithoutEventsAndUserDto(
            id = this.id.value,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
            book = this.book.toBookDto(),
            owned = this.owned,
            toRead = this.toRead,
            personalNotes = this.personalNotes,
            lastReadingEvent = this.lastReadingEvent,
            lastReadingEventDate = this.lastReadingEventDate,
            percentRead = this.percentRead,
            currentPageNumber = this.currentPageNumber,
            borrowed = this.borrowed,
            borrowerName = this.borrowerName,
            borrowDate = this.borrowDate,
            expectedReturnDate = this.expectedReturnDate,
            ownerName = this.ownerName,
            digitalFilePath = this.digitalFilePath,
            digitalFileFormat = this.digitalFileFormat,
            digitalFileSizeBytes = this.digitalFileSizeBytes,
            digitalFileAddedDate = this.digitalFileAddedDate,
            lastSentToReaderDate = this.lastSentToReaderDate,
            avgRating = this.avgRating,
            userAvgRating = this.userAvgRating,
            price = centsToDouble(this.priceInCents),
        )

    fun toUserBookWithoutEventsDto(): UserBookWithoutEventsDto =
        UserBookWithoutEventsDto(
            id = this.id.value,
            creationDate = this.creationDate,
            modificationDate = this.modificationDate,
            book = this.book.toBookDto(),
            owned = this.owned,
            toRead = this.toRead,
            personalNotes = this.personalNotes,
            user = this.user.toUserDto(),
            percentRead = this.percentRead,
            currentPageNumber = this.currentPageNumber,
            borrowed = this.borrowed,
            borrowerName = this.borrowerName,
            borrowDate = this.borrowDate,
            expectedReturnDate = this.expectedReturnDate,
            ownerName = this.ownerName,
            digitalFilePath = this.digitalFilePath,
            digitalFileFormat = this.digitalFileFormat,
            digitalFileSizeBytes = this.digitalFileSizeBytes,
            digitalFileAddedDate = this.digitalFileAddedDate,
            lastSentToReaderDate = this.lastSentToReaderDate,
            price = centsToDouble(this.priceInCents),
        )
}
