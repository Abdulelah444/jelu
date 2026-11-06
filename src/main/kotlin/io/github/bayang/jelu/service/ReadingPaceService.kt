package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.ReadingProgressHistory
import io.github.bayang.jelu.dao.ReadingProgressHistoryTable
import io.github.bayang.jelu.dao.UserBook
import org.jetbrains.exposed.sql.and
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

data class PaceResult(
    val period: String,
    val available: Boolean,
    val pagesPerDay: Double,
    val daysInWindow: Double,
    val pagesInWindow: Int,
    val currentPage: Int,
    val pagesRemaining: Int?,
    val daysRemaining: Int?,
    val estimatedFinish: String?,
)

data class ProgressEntryDto(
    val id: UUID?,
    val pageNumber: Int?,
    val pagesDelta: Int?,
    val recordedAt: OffsetDateTime,
)

data class ProgressEntryInput(
    val pageNumber: Int,
    val recordedAt: OffsetDateTime,
)

data class ReplaceProgressDto(
    val entries: List<ProgressEntryInput>,
)

@Service
class ReadingPaceService {

    @Transactional
    fun getHistory(userBookId: UUID): List<ProgressEntryDto> =
        ReadingProgressHistory.find { ReadingProgressHistoryTable.userBook eq userBookId }
            .sortedBy { it.recordedAt }
            .map { ProgressEntryDto(it.id.value, it.pageNumber, it.pagesDelta, it.recordedAt) }

    @Transactional
    fun pagesReadForBookSince(userBookId: UUID, since: OffsetDateTime): Int {
        return ReadingProgressHistory.find {
            (ReadingProgressHistoryTable.userBook eq userBookId) and
                (ReadingProgressHistoryTable.recordedAt greaterEq since) and
                (ReadingProgressHistoryTable.pagesDelta greater 0)
        }.sumOf { it.pagesDelta ?: 0 }
    }

    @Transactional
    fun pagesReadSince(userId: UUID, since: OffsetDateTime): Int {
        // Sum positive page deltas for this user's books recorded on/after `since`
        return ReadingProgressHistory.find {
            (ReadingProgressHistoryTable.recordedAt greaterEq since) and
                (ReadingProgressHistoryTable.pagesDelta greater 0)
        }
            .filter { it.userBook.user.id.value == userId }
            .sumOf { it.pagesDelta ?: 0 }
    }

    @Transactional
    fun replaceHistory(userBookId: UUID, dto: ReplaceProgressDto): List<ProgressEntryDto> {
        val userBook = UserBook[userBookId]
        // Delete existing rows for this userbook
        ReadingProgressHistory.find { ReadingProgressHistoryTable.userBook eq userBookId }
            .forEach { it.delete() }
        // Sort incoming by date, recompute deltas (first = 0, rest = page - prevPage)
        val sorted = dto.entries.sortedBy { it.recordedAt }
        var prev: Int? = null
        for (e in sorted) {
            val delta = if (prev == null) 0 else e.pageNumber - prev!!
            ReadingProgressHistory.new {
                this.userBook = userBook
                this.pageNumber = e.pageNumber
                this.percentRead = null
                this.pagesDelta = delta
                this.recordedAt = e.recordedAt
            }
            prev = e.pageNumber
        }
        // Sync the userbook's current page to the latest entry
        if (sorted.isNotEmpty()) {
            userBook.currentPageNumber = sorted.last().pageNumber
        }
        return getHistory(userBookId)
    }

    @Transactional
    fun computeAllPaces(userBookId: UUID): Map<String, PaceResult?> =
        mapOf(
            "day" to computePace(userBookId, "day"),
            "week" to computePace(userBookId, "week"),
            "month" to computePace(userBookId, "month"),
            "since_start" to computePace(userBookId, "since_start"),
        )

    @Transactional
    fun computePace(userBookId: UUID, period: String): PaceResult? {
        val userBook = UserBook[userBookId]
        val pageCount = userBook.book.pageCount
        val currentPage = userBook.currentPageNumber
            ?: if (userBook.percentRead != null && pageCount != null) {
                (pageCount * userBook.percentRead!!) / 100
            } else null
        if (currentPage == null || currentPage <= 0) return null

        val now = OffsetDateTime.now()
        val windowDays: Double? = when (period) {
            "day" -> 1.0
            "week" -> 7.0
            "month" -> 30.0
            "quarter" -> 90.0
            "year" -> 365.0
            "since_start" -> null
            else -> null
        }

        // Window start time
        val windowStart: OffsetDateTime? = windowDays?.let { now.minusMinutes((it * 1440).toLong()) }

        // Sum the signed deltas recorded within the window
        val rows = if (windowStart == null) {
            ReadingProgressHistory.find { ReadingProgressHistoryTable.userBook eq userBookId }
        } else {
            ReadingProgressHistory.find {
                (ReadingProgressHistoryTable.userBook eq userBookId) and
                    (ReadingProgressHistoryTable.recordedAt greaterEq windowStart)
            }
        }
        val pagesInWindow = rows.sumOf { it.pagesDelta ?: 0 }

        // Span of recorded reading (earliest delta to now), used to cap window length
        val earliest = ReadingProgressHistory.find {
            ReadingProgressHistoryTable.userBook eq userBookId
        }.minByOrNull { it.recordedAt }
        val spanDays = if (earliest != null) {
            maxOf(java.time.temporal.ChronoUnit.MINUTES.between(earliest.recordedAt, now) / 1440.0, 1.0)
        } else 1.0
        // Cap trailing windows at the actual reading span so longer windows aren't diluted
        val daysInWindow = if (windowDays == null) spanDays else maxOf(minOf(windowDays, spanDays), 1.0)

        val pace = if (pagesInWindow > 0 && daysInWindow > 0) pagesInWindow / daysInWindow else 0.0
        val pagesRemaining = pageCount?.let { it - currentPage }
        val daysRemaining = if (pagesRemaining != null && pagesRemaining > 0 && pace > 0) {
            Math.ceil(pagesRemaining / pace).toInt()
        } else null
        val estimatedFinish = daysRemaining?.let { now.plusDays(it.toLong()).toLocalDate().toString() }

        return PaceResult(
            period = period,
            available = true,
            pagesPerDay = Math.round(pace * 10) / 10.0,
            daysInWindow = Math.round(daysInWindow * 10) / 10.0,
            pagesInWindow = pagesInWindow,
            currentPage = currentPage,
            pagesRemaining = pagesRemaining,
            daysRemaining = daysRemaining,
            estimatedFinish = estimatedFinish,
        )
    }
}
