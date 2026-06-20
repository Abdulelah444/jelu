package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.BookPaceTarget
import io.github.bayang.jelu.dao.BookPaceTargetTable
import io.github.bayang.jelu.dao.DailyReadingLog
import io.github.bayang.jelu.dao.DailyReadingLogTable
import io.github.bayang.jelu.dao.ReadingProgressHistory
import io.github.bayang.jelu.dao.ReadingProgressHistoryTable
import io.github.bayang.jelu.dao.User
import io.github.bayang.jelu.dao.UserBook
import io.github.bayang.jelu.dao.UserBookTable
import io.github.bayang.jelu.dto.DailyLogEntryDto
import io.github.bayang.jelu.dto.HeatmapDto
import io.github.bayang.jelu.dto.PaceStatus
import io.github.bayang.jelu.dto.StreakDto
import io.github.bayang.jelu.dto.WeeklyBookReportDto
import io.github.bayang.jelu.dto.WeeklyReportDto
import io.github.bayang.jelu.utils.nowDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max

@Component
class StreakService {

    /**
     * Rebuild the daily_reading_log for a user from their progress history.
     * Aggregates page deltas per (userbook, day).
     */
    @Transactional
    fun rebuildDailyLog(userId: UUID) {
        // Clear existing log for this user
        DailyReadingLog.find { DailyReadingLogTable.user eq userId }.forEach { it.delete() }

        val user = User[userId]
        val now = nowDateTime()

        // Get all userbooks for this user
        val userBookIds = UserBook.find { UserBookTable.user eq userId }.map { it.id.value }

        for (ubId in userBookIds) {
            val history = ReadingProgressHistory
                .find { ReadingProgressHistoryTable.userBook eq ubId }
                .orderBy(ReadingProgressHistoryTable.recordedAt to SortOrder.ASC)
                .toList()

            if (history.isEmpty()) continue

            // Aggregate pages read per day from deltas
            val perDay = mutableMapOf<LocalDate, Int>()
            var lastPage = 0
            for (h in history) {
                val day = h.recordedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                val pages = h.pageNumber ?: 0
                val delta = h.pagesDelta ?: max(0, pages - lastPage)
                if (delta > 0) {
                    perDay[day] = (perDay[day] ?: 0) + delta
                }
                if (pages > 0) lastPage = pages
            }

            val userBook = UserBook[ubId]
            for ((day, pages) in perDay) {
                if (pages <= 0) continue
                DailyReadingLog.new {
                    this.user = user
                    this.userBook = userBook
                    this.logDate = day
                    this.pagesRead = pages
                    this.creationDate = now
                }
            }
        }
    }

    @Transactional
    fun getHeatmap(userId: UUID, fromDate: LocalDate, toDate: LocalDate): HeatmapDto {
        val logs = DailyReadingLog.find {
            (DailyReadingLogTable.user eq userId) and
                (DailyReadingLogTable.logDate greaterEq fromDate) and
                (DailyReadingLogTable.logDate lessEq toDate)
        }.toList()

        val perDay = mutableMapOf<LocalDate, Int>()
        for (log in logs) {
            perDay[log.logDate] = (perDay[log.logDate] ?: 0) + log.pagesRead
        }

        val entries = perDay.entries
            .sortedBy { it.key }
            .map { DailyLogEntryDto(it.key, it.value) }
        return HeatmapDto(entries)
    }

    /**
     * Total pages read by a user on a given day (across all books).
     */
    private fun pagesOnDay(userId: UUID, day: LocalDate): Int {
        return DailyReadingLog
            .find { (DailyReadingLogTable.user eq userId) and (DailyReadingLogTable.logDate eq day) }
            .sumOf { it.pagesRead }
    }

    /**
     * Pages read for a specific book within a date range (inclusive).
     */
    private fun pagesForBookInRange(userBookId: UUID, from: LocalDate, to: LocalDate): Int {
        return DailyReadingLog
            .find {
                (DailyReadingLogTable.userBook eq userBookId) and
                    (DailyReadingLogTable.logDate greaterEq from) and
                    (DailyReadingLogTable.logDate lessEq to)
            }
            .sumOf { it.pagesRead }
    }

    /**
     * Compute streaks. Daily streak = consecutive days with any reading.
     * Weekly streak = consecutive Mon-Sun weeks where total pages met the combined weekly goal.
     */
    @Transactional
    fun getStreaks(userId: UUID): StreakDto {
        val today = LocalDate.now(ZoneId.systemDefault())

        // ---- Daily streak (any reading) ----
        var currentDaily = 0
        var cursor = today
        // allow today to be unlogged without breaking (count from yesterday if today empty)
        if (pagesOnDay(userId, cursor) == 0) {
            cursor = cursor.minusDays(1)
        }
        while (pagesOnDay(userId, cursor) > 0) {
            currentDaily++
            cursor = cursor.minusDays(1)
        }

        // longest daily streak: scan all logged days
        val allDays = DailyReadingLog.find { DailyReadingLogTable.user eq userId }
            .map { it.logDate }
            .distinct()
            .sorted()
        var longestDaily = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in allDays) {
            run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
            if (run > longestDaily) longestDaily = run
            prev = d
        }

        // ---- Weekly streak (combined daily goal across in-progress books) ----
        val weeklyGoal = currentCombinedWeeklyGoal(userId)
        var currentWeekly = 0
        var longestWeekly = 0
        if (weeklyGoal > 0) {
            var weekStart = today.with(DayOfWeek.MONDAY)
            // walk backwards week by week
            var streaking = true
            var wk = weekStart
            var wrun = 0
            // current streak: from this week (or last completed) backwards
            // Use last fully started week
            var checkWeek = weekStart
            while (true) {
                val weekEnd = checkWeek.plusDays(6)
                val pages = DailyReadingLog.find {
                    (DailyReadingLogTable.user eq userId) and
                        (DailyReadingLogTable.logDate greaterEq checkWeek) and
                        (DailyReadingLogTable.logDate lessEq weekEnd)
                }.sumOf { it.pagesRead }
                val isCurrentWeek = checkWeek == weekStart
                if (pages >= weeklyGoal) {
                    currentWeekly++
                } else if (isCurrentWeek) {
                    // current week not done yet, don't break streak; just skip
                } else {
                    break
                }
                checkWeek = checkWeek.minusWeeks(1)
                // safety stop after 5 years
                if (checkWeek.isBefore(today.minusYears(5))) break
            }
            longestWeekly = currentWeekly // simplified; refined later
        }

        return StreakDto(
            currentDailyStreak = currentDaily,
            longestDailyStreak = longestDaily,
            currentWeeklyStreak = currentWeekly,
            longestWeeklyStreak = max(longestWeekly, currentWeekly),
        )
    }

    /**
     * Sum of daily targets across all currently-reading books with a pace target.
     * Multiplied by 7 gives the combined weekly goal.
     */
    private fun currentCombinedWeeklyGoal(userId: UUID): Int {
        val userBookIds = UserBook.find { UserBookTable.user eq userId }.map { it.id.value }
        var dailySum = 0
        for (ubId in userBookIds) {
            val target = BookPaceTarget
                .find { BookPaceTargetTable.userBook eq ubId }
                .orderBy(BookPaceTargetTable.creationDate to SortOrder.DESC)
                .firstOrNull()
            if (target != null) dailySum += target.dailyTargetPages
        }
        return dailySum * 7
    }

    /**
     * Build the weekly report for the current Mon-Sun week.
     */
    @Transactional
    fun getWeeklyReport(userId: UUID): WeeklyReportDto {
        val today = LocalDate.now(ZoneId.systemDefault())
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)

        val books = mutableListOf<WeeklyBookReportDto>()
        var totalPages = 0
        var allCompliant = true
        var anyBook = false

        val userBooks = UserBook.find { UserBookTable.user eq userId }.toList()
        for (ub in userBooks) {
            val target = BookPaceTarget
                .find { BookPaceTargetTable.userBook eq ub.id.value }
                .orderBy(BookPaceTargetTable.creationDate to SortOrder.DESC)
                .firstOrNull() ?: continue

            anyBook = true
            val pagesThisWeek = pagesForBookInRange(ub.id.value, weekStart, weekEnd)
            totalPages += pagesThisWeek
            val weeklyTarget = target.dailyTargetPages * 7
            val compliance = if (weeklyTarget > 0) (pagesThisWeek * 100 / weeklyTarget) else 0
            val onTrack = pagesThisWeek >= weeklyTarget
            if (!onTrack) allCompliant = false

            val status = when {
                compliance >= 110 -> PaceStatus.AHEAD
                onTrack -> PaceStatus.ON_PACE
                else -> PaceStatus.BEHIND
            }

            books.add(
                WeeklyBookReportDto(
                    userBookId = ub.id.value,
                    title = ub.book.title,
                    image = ub.book.image,
                    dailyTargetPages = target.dailyTargetPages,
                    pagesThisWeek = pagesThisWeek,
                    weeklyTargetPages = weeklyTarget,
                    compliancePercent = compliance,
                    onTrack = onTrack,
                    status = status,
                ),
            )
        }

        return WeeklyReportDto(
            weekStart = weekStart,
            weekEnd = weekEnd,
            totalPagesThisWeek = totalPages,
            perfectWeek = anyBook && allCompliant,
            books = books,
            streak = getStreaks(userId),
        )
    }
}
