package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.ReadingEventTable
import io.github.bayang.jelu.dao.ReadingEventType
import io.github.bayang.jelu.dao.ReadingGoal
import io.github.bayang.jelu.dao.ReadingGoalTable
import io.github.bayang.jelu.dao.User
import io.github.bayang.jelu.dao.UserBookTable
import io.github.bayang.jelu.dto.CreateReadingGoalDto
import io.github.bayang.jelu.dto.ReadingGoalDto
import io.github.bayang.jelu.utils.nowDateTime
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Component
class ReadingGoalService {

    @Transactional
    fun setGoal(userId: UUID, dto: CreateReadingGoalDto): ReadingGoalDto {
        // Remove existing goal for this year (replace)
        ReadingGoal.find {
            (ReadingGoalTable.user eq userId) and (ReadingGoalTable.year eq dto.year)
        }.forEach { it.delete() }

        ReadingGoal.new {
            this.user = User[userId]
            this.year = dto.year
            this.targetBooks = dto.targetBooks
            this.creationDate = nowDateTime()
        }
        return getGoal(userId, dto.year)!!
    }

    @Transactional
    fun deleteGoal(userId: UUID, year: Int) {
        ReadingGoal.find {
            (ReadingGoalTable.user eq userId) and (ReadingGoalTable.year eq year)
        }.forEach { it.delete() }
    }

    @Transactional
    fun getGoal(userId: UUID, year: Int): ReadingGoalDto? {
        val goal = ReadingGoal.find {
            (ReadingGoalTable.user eq userId) and (ReadingGoalTable.year eq year)
        }.orderBy(ReadingGoalTable.creationDate to SortOrder.DESC).firstOrNull() ?: return null

        val finished = countFinishedInYear(userId, year)
        val target = goal.targetBooks
        val percent = if (target > 0) (finished * 100 / target) else 0

        val today = LocalDate.now(ZoneId.systemDefault())
        val isCurrentYear = today.year == year
        val dayOfYear = if (isCurrentYear) today.dayOfYear else 365
        val daysInYear = if (LocalDate.of(year, 12, 31).isLeapYear) 366 else 365
        val daysRemaining = if (isCurrentYear) max(0, daysInYear - dayOfYear) else 0

        // Projection: at current pace, how many books by year end
        val projection = if (dayOfYear > 0) {
            ((finished.toDouble() / dayOfYear) * daysInYear).roundToInt()
        } else finished

        val booksRemaining = max(0, target - finished)
        // Days per book needed to hit goal
        val pacePerBookDays = if (booksRemaining > 0 && daysRemaining > 0) {
            daysRemaining.toDouble() / booksRemaining
        } else 0.0

        // Behind by: how many books behind the linear pace you should be at now
        val expectedByNow = if (isCurrentYear) {
            ceil(target.toDouble() * dayOfYear / daysInYear).toInt()
        } else target
        val behindBy = max(0, expectedByNow - finished)

        return ReadingGoalDto(
            id = goal.id.value,
            year = year,
            targetBooks = target,
            booksFinished = finished,
            percentComplete = percent,
            onTrackProjection = projection,
            booksRemaining = booksRemaining,
            daysRemaining = daysRemaining,
            pacePerBookDays = pacePerBookDays,
            behindBy = behindBy,
        )
    }

    private fun countFinishedInYear(userId: UUID, year: Int): Int {
        val start = OffsetDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val end = OffsetDateTime.of(year, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC)
        val countCol = UserBookTable.id.countDistinct()
        val query = UserBookTable
            .join(ReadingEventTable, JoinType.INNER, onColumn = UserBookTable.id, otherColumn = ReadingEventTable.userBook)
            .select(countCol)
            .andWhere { UserBookTable.user eq userId }
            .andWhere { ReadingEventTable.eventType eq ReadingEventType.FINISHED }
            .andWhere { ReadingEventTable.endDate greaterEq start }
            .andWhere { ReadingEventTable.endDate lessEq end }
        return query.single()[countCol].toInt()
    }
}
