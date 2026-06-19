package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.BookPaceTarget
import io.github.bayang.jelu.dao.BookPaceTargetTable
import io.github.bayang.jelu.dao.ReadingEventType
import io.github.bayang.jelu.dao.UserBook
import io.github.bayang.jelu.dto.CreatePaceTargetDto
import io.github.bayang.jelu.dto.PaceStatus
import io.github.bayang.jelu.dto.PaceStatusDto
import io.github.bayang.jelu.dto.PaceTargetDto
import io.github.bayang.jelu.utils.nowDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

@Component
class PaceTargetService {

    @Transactional
    fun setTarget(dto: CreatePaceTargetDto): PaceTargetDto {
        val userBook = UserBook[dto.userBookId]
        val now = nowDateTime()
        val pageCount = userBook.book.pageCount
        val currentPage = userBook.currentPageNumber ?: 0
        val remaining = if (pageCount != null) max(0, pageCount - currentPage) else null
        val plannedFinish: OffsetDateTime? =
            if (remaining != null && dto.dailyTargetPages > 0) {
                val days = ceil(remaining.toDouble() / dto.dailyTargetPages).toLong()
                now.plusDays(days)
            } else {
                null
            }

        // Remove any existing target for this userbook (latest wins)
        BookPaceTarget.find { BookPaceTargetTable.userBook eq dto.userBookId }.forEach { it.delete() }

        val created = BookPaceTarget.new {
            this.userBook = userBook
            this.dailyTargetPages = dto.dailyTargetPages
            this.startDate = now
            this.plannedFinishDate = plannedFinish
            this.creationDate = now
        }
        return created.toDto()
    }

    @Transactional
    fun getTarget(userBookId: UUID): PaceTargetDto? =
        BookPaceTarget
            .find { BookPaceTargetTable.userBook eq userBookId }
            .orderBy(BookPaceTargetTable.creationDate to SortOrder.DESC)
            .firstOrNull()
            ?.toDto()

    @Transactional
    fun deleteTarget(userBookId: UUID) {
        BookPaceTarget.find { BookPaceTargetTable.userBook eq userBookId }.forEach { it.delete() }
    }

    @Transactional
    fun getStatus(userBookId: UUID): PaceStatusDto? {
        val target = BookPaceTarget
            .find { BookPaceTargetTable.userBook eq userBookId }
            .orderBy(BookPaceTargetTable.creationDate to SortOrder.DESC)
            .firstOrNull() ?: return null

        val userBook = UserBook[userBookId]
        val pageCount = userBook.book.pageCount
        val currentPage = userBook.currentPageNumber ?: 0
        val now = nowDateTime()

        // Anchor pace to the actual reading start date (the CURRENTLY_READING event),
        // falling back to the target creation date if none exists.
        val readingStart = userBook.readingEvents
            .filter { it.eventType == ReadingEventType.CURRENTLY_READING }
            .maxByOrNull { it.startDate }
            ?.startDate
            ?: target.startDate

        // Days elapsed since reading start (inclusive of day 1)
        val daysElapsed = max(1, Duration.between(readingStart, now).toDays().toInt() + 1)
        val expectedPage = target.dailyTargetPages * daysElapsed
        val expectedCapped = if (pageCount != null) minOf(expectedPage, pageCount) else expectedPage
        val diff = currentPage - expectedCapped

        val status = when {
            currentPage == 0 && daysElapsed <= 1 -> PaceStatus.NOT_STARTED
            diff >= target.dailyTargetPages -> PaceStatus.AHEAD
            diff <= -target.dailyTargetPages -> PaceStatus.BEHIND
            else -> PaceStatus.ON_PACE
        }

        // Estimate finish based on actual pace so far
        val estimatedFinish: OffsetDateTime? =
            if (pageCount != null && currentPage > 0) {
                val pagesPerDay = currentPage.toDouble() / daysElapsed
                if (pagesPerDay > 0) {
                    val remainingDays = ceil((pageCount - currentPage) / pagesPerDay).toLong()
                    now.plusDays(max(0, remainingDays))
                } else null
            } else null

        return PaceStatusDto(
            userBookId = userBookId,
            dailyTargetPages = target.dailyTargetPages,
            startDate = target.startDate,
            plannedFinishDate = target.plannedFinishDate,
            currentPage = currentPage,
            pageCount = pageCount,
            expectedPage = expectedCapped,
            pagesAheadBehind = diff,
            status = status,
            daysElapsed = daysElapsed,
            estimatedFinishDate = estimatedFinish,
        )
    }

    private fun BookPaceTarget.toDto(): PaceTargetDto = PaceTargetDto(
        id = this.id.value,
        userBookId = this.userBook.id.value,
        dailyTargetPages = this.dailyTargetPages,
        startDate = this.startDate,
        plannedFinishDate = this.plannedFinishDate,
    )
}
