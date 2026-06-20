package io.github.bayang.jelu.dao
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.UUID
object ReadingGoalTable : UUIDTable("reading_goal") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val year = integer("year")
    val targetBooks = integer("target_books")
    val creationDate = timestampWithTimeZone("creation_date")
}
class ReadingGoal(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ReadingGoal>(ReadingGoalTable)
    var user by User referencedOn ReadingGoalTable.user
    var year by ReadingGoalTable.year
    var targetBooks by ReadingGoalTable.targetBooks
    var creationDate by ReadingGoalTable.creationDate
}
