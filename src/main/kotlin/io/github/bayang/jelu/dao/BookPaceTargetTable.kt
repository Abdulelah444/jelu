package io.github.bayang.jelu.dao
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.UUID
object BookPaceTargetTable : UUIDTable("book_pace_target") {
    val userBook = reference("user_book_id", UserBookTable, onDelete = ReferenceOption.CASCADE)
    val dailyTargetPages = integer("daily_target_pages")
    val startDate = timestampWithTimeZone("start_date")
    val plannedFinishDate = timestampWithTimeZone("planned_finish_date").nullable()
    val creationDate = timestampWithTimeZone("creation_date")
}
class BookPaceTarget(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BookPaceTarget>(BookPaceTargetTable)
    var userBook by UserBook referencedOn BookPaceTargetTable.userBook
    var dailyTargetPages by BookPaceTargetTable.dailyTargetPages
    var startDate by BookPaceTargetTable.startDate
    var plannedFinishDate by BookPaceTargetTable.plannedFinishDate
    var creationDate by BookPaceTargetTable.creationDate
}
