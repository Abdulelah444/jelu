package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.ReadingEventType
import io.github.bayang.jelu.dao.ReadingProgressHistory
import io.github.bayang.jelu.dao.ReadingProgressHistoryTable
import io.github.bayang.jelu.dao.UserBook
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class PaceResult(
    val period: String,
    val pagesPerDay: Double,
    val daysInWindow: Double,
    val pagesInWindow: Int,
    val currentPage: Int,
    val pagesRemaining: Int?,
    val daysRemaining: Int?,
    val estimatedFinish: String?,
    val fellBackToSinceStart: Boolean,
    val daysOfHistory: Double,
)

@Service
class ReadingPaceService {

    @Transactional
    fun computePace(userBookId: UUID, period: String): PaceResult? {
        val userBook = UserBook[userBookId]
        val pageCount = userBook.book.pageCount
        val currentPage = userBook.currentPageNumber
            ?: if (userBook.percentRead != null && pageCount != null) {
                (pageCount * userBook.percentRead!!) / 100
            } else null
        if (currentPage == null || currentPage <= 0) return null

        // Find the CURRENTLY_READING start date as the "since start" anchor
        val currentlyReading = userBook.readingEvents
            .firstOrNull { it.eventType == ReadingEventType.CURRENTLY_READING }
        val startDate = currentlyReading?.startDate ?: return null

        val now = OffsetDateTime.now()

        // How many days of recorded history exist (for "available in X days" messaging)
        val earliestRow = ReadingProgressHistory.find {
            ReadingProgressHistoryTable.userBook eq userBookId
        }.orderBy(ReadingProgressHistoryTable.recordedAt to SortOrder.ASC).limit(1).firstOrNull()
        val daysOfHistory = if (earliestRow != null) {
            ChronoUnit.MINUTES.between(earliestRow.recordedAt, now) / 1440.0
        } else 0.0

        // Determine the requested window start
        val requestedWindowStart: OffsetDateTime? = when (period) {
            "day" -> now.minusDays(1)
            "week" -> now.minusWeeks(1)
            "month" -> now.minusMonths(1)
            "since_start" -> null
            else -> null
        }

        var fellBack = false
        // Effective window start: clamp to reading start date
        val effectiveWindowStart: OffsetDateTime = if (requestedWindowStart == null) {
            startDate
        } else if (requestedWindowStart.isBefore(startDate)) {
            fellBack = true
            startDate
        } else {
            requestedWindowStart
        }

        // Page at the window start:
        // If window start == reading start, page is 0.
        // Otherwise, find the latest history row at or before the window start.
        val pageAtWindowStart: Int = if (effectiveWindowStart == startDate) {
            0
        } else {
            val historyRow = ReadingProgressHistory.find {
                (ReadingProgressHistoryTable.userBook eq userBookId) and
                    (ReadingProgressHistoryTable.recordedAt lessEq effectiveWindowStart)
            }.orderBy(ReadingProgressHistoryTable.recordedAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
            // If no row before window start, fall back to since-start (page 0 from reading start)
            if (historyRow?.pageNumber == null) {
                fellBack = true
                0
            } else {
                historyRow.pageNumber!!
            }
        }

        val actualWindowStart = if (fellBack && requestedWindowStart != null) startDate else effectiveWindowStart
        val daysInWindow = maxOf(
            ChronoUnit.MINUTES.between(actualWindowStart, now) / 1440.0,
            0.5,
        )
        val pagesInWindow = currentPage - pageAtWindowStart
        if (pagesInWindow <= 0) {
            return PaceResult(
                period = period,
                pagesPerDay = 0.0,
                daysInWindow = Math.round(daysInWindow * 10) / 10.0,
                pagesInWindow = 0,
                currentPage = currentPage,
                pagesRemaining = pageCount?.let { it - currentPage },
                daysRemaining = null,
                estimatedFinish = null,
                fellBackToSinceStart = fellBack,
                daysOfHistory = Math.round(daysOfHistory * 10) / 10.0,
            )
        }

        val pace = pagesInWindow / daysInWindow
        val pagesRemaining = pageCount?.let { it - currentPage }
        val daysRemaining = if (pagesRemaining != null && pagesRemaining > 0 && pace > 0) {
            Math.ceil(pagesRemaining / pace).toInt()
        } else null
        val estimatedFinish = daysRemaining?.let {
            now.plusDays(it.toLong()).toLocalDate().toString()
        }

        return PaceResult(
            period = period,
            pagesPerDay = Math.round(pace * 10) / 10.0,
            daysInWindow = Math.round(daysInWindow * 10) / 10.0,
            pagesInWindow = pagesInWindow,
            currentPage = currentPage,
            pagesRemaining = pagesRemaining,
            daysRemaining = daysRemaining,
            estimatedFinish = estimatedFinish,
            fellBackToSinceStart = fellBack,
            daysOfHistory = Math.round(daysOfHistory * 10) / 10.0,
        )
    }
}
