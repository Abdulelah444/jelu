package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.ReadingEventTable
import io.github.bayang.jelu.dao.ReadingEventType
import io.github.bayang.jelu.dao.ReadingGoal
import io.github.bayang.jelu.dao.ReadingGoalTable
import io.github.bayang.jelu.dao.User
import io.github.bayang.jelu.dao.UserBookTable
import io.github.bayang.jelu.dto.CreateReadingGoalDto
import io.github.bayang.jelu.dto.GoalPeriodType
import io.github.bayang.jelu.dto.ReadingGoalDto
import io.github.bayang.jelu.utils.nowDateTime
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.countDistinct
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Component
class ReadingGoalService {

    @Transactional
    fun setGoal(userId: UUID, dto: CreateReadingGoalDto): ReadingGoalDto {
        val today = LocalDate.now()
        val start = dto.startDate ?: today

        // Compute end date based on period type if not explicitly given
        val end = dto.endDate ?: when (dto.periodType) {
            GoalPeriodType.ANNUAL -> start.plusYears(1).minusDays(1)
            GoalPeriodType.MONTHLY -> start.plusMonths(1).minusDays(1)
        }

        // Replace any existing goal for this user (single active goal model)
        ReadingGoal.find { ReadingGoalTable.user eq userId }.forEach { it.delete() }

        ReadingGoal.new {
            this.user = User[userId]
            this.year = start.year
            this.targetBooks = dto.targetBooks
            this.periodType = dto.periodType.name
            this.startDate = start
            this.endDate = end
            this.repeating = dto.repeating
            this.creationDate = nowDateTime()
        }
        return getGoal(userId)!!
    }

    @Transactional
    fun deleteGoal(userId: UUID) {
        ReadingGoal.find { ReadingGoalTable.user eq userId }.forEach { it.delete() }
    }

    @Transactional
    fun getGoal(userId: UUID): ReadingGoalDto? {
        val goal = ReadingGoal.find { ReadingGoalTable.user eq userId }
            .orderBy(ReadingGoalTable.creationDate to SortOrder.DESC)
            .firstOrNull() ?: return null

        val periodType = try { GoalPeriodType.valueOf(goal.periodType) } catch (e: Exception) { GoalPeriodType.ANNUAL }
        val today = LocalDate.now()

        var start = goal.startDate ?: LocalDate.of(goal.year, 1, 1)
        var end = goal.endDate ?: LocalDate.of(goal.year, 12, 31)

        // For repeating goals, roll the window forward to the current period
        if (goal.repeating && today.isAfter(end)) {
            when (periodType) {
                GoalPeriodType.ANNUAL -> {
                    while (today.isAfter(end)) {
                        start = start.plusYears(1)
                        end = end.plusYears(1)
                    }
                }
                GoalPeriodType.MONTHLY -> {
                    while (today.isAfter(end)) {
                        start = start.plusMonths(1)
                        end = end.plusMonths(1)
                    }
                }
            }
        }

        val finished = countFinishedBetween(userId, start, end)
        val target = goal.targetBooks
        val percent = if (target > 0) (finished * 100 / target) else 0

        val periodLengthDays = max(1, ChronoUnit.DAYS.between(start, end).toInt() + 1)
        val daysElapsed = when {
            today.isBefore(start) -> 0
            today.isAfter(end) -> periodLengthDays
            else -> ChronoUnit.DAYS.between(start, today).toInt() + 1
        }
        val daysRemaining = max(0, periodLengthDays - daysElapsed)

        val projection = if (daysElapsed > 0) {
            ((finished.toDouble() / daysElapsed) * periodLengthDays).roundToInt()
        } else finished

        val booksRemaining = max(0, target - finished)
        val expectedByNow = if (daysElapsed > 0) {
            ceil(target.toDouble() * daysElapsed / periodLengthDays).toInt()
        } else 0
        val behindBy = max(0, expectedByNow - finished)

        val periodLabel = when (periodType) {
            GoalPeriodType.ANNUAL ->
                if (start.year == end.year) "${start.year} Reading Challenge"
                else "${start.year}-${end.year} Challenge"
            GoalPeriodType.MONTHLY ->
                start.month.name.lowercase().replaceFirstChar { it.uppercase() } + " Goal"
        }

        return ReadingGoalDto(
            id = goal.id.value,
            targetBooks = target,
            periodType = periodType,
            startDate = start,
            endDate = end,
            repeating = goal.repeating,
            booksFinished = finished,
            percentComplete = percent,
            onTrackProjection = projection,
            booksRemaining = booksRemaining,
            daysRemaining = daysRemaining,
            daysElapsed = daysElapsed,
            periodLengthDays = periodLengthDays,
            behindBy = behindBy,
            periodLabel = periodLabel,
        )
    }

    private fun countFinishedBetween(userId: UUID, start: LocalDate, end: LocalDate): Int {
        val startTs = OffsetDateTime.of(start.year, start.monthValue, start.dayOfMonth, 0, 0, 0, 0, ZoneOffset.UTC)
        val endTs = OffsetDateTime.of(end.year, end.monthValue, end.dayOfMonth, 23, 59, 59, 0, ZoneOffset.UTC)
        val countCol = UserBookTable.id.countDistinct()
        val query = UserBookTable
            .join(ReadingEventTable, JoinType.INNER, onColumn = UserBookTable.id, otherColumn = ReadingEventTable.userBook)
            .select(countCol)
            .andWhere { UserBookTable.user eq userId }
            .andWhere { ReadingEventTable.eventType eq ReadingEventType.FINISHED }
            .andWhere { ReadingEventTable.endDate greaterEq startTs }
            .andWhere { ReadingEventTable.endDate lessEq endTs }
        return query.single()[countCol].toInt()
    }
}
