package io.github.bayang.jelu.dao
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.UUID
object DailyReadingLogTable : UUIDTable("daily_reading_log") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val userBook = reference("user_book_id", UserBookTable, onDelete = ReferenceOption.CASCADE)
    val logDate = date("log_date")
    val pagesRead = integer("pages_read")
    val creationDate = timestampWithTimeZone("creation_date")
}
class DailyReadingLog(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DailyReadingLog>(DailyReadingLogTable)
    var user by User referencedOn DailyReadingLogTable.user
    var userBook by UserBook referencedOn DailyReadingLogTable.userBook
    var logDate by DailyReadingLogTable.logDate
    var pagesRead by DailyReadingLogTable.pagesRead
    var creationDate by DailyReadingLogTable.creationDate
}
