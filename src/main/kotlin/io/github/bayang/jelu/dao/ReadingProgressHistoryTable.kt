package io.github.bayang.jelu.dao

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.UUID

object ReadingProgressHistoryTable : UUIDTable("reading_progress_history") {
    val userBook = reference("user_book", UserBookTable, onDelete = ReferenceOption.CASCADE)
    val pageNumber = integer("page_number").nullable()
    val percentRead = integer("percent_read").nullable()
    val pagesDelta = integer("pages_delta").nullable()
    val recordedAt = timestampWithTimeZone("recorded_at")
}

class ReadingProgressHistory(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ReadingProgressHistory>(ReadingProgressHistoryTable)
    var userBook by UserBook referencedOn ReadingProgressHistoryTable.userBook
    var pageNumber by ReadingProgressHistoryTable.pageNumber
    var percentRead by ReadingProgressHistoryTable.percentRead
    var pagesDelta by ReadingProgressHistoryTable.pagesDelta
    var recordedAt by ReadingProgressHistoryTable.recordedAt
}
