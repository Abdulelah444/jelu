package io.github.bayang.jelu.dto

import java.time.OffsetDateTime
import java.util.UUID

enum class PaceStatus {
    AHEAD,
    ON_PACE,
    BEHIND,
    NOT_STARTED,
}

data class CreatePaceTargetDto(
    val userBookId: UUID,
    val dailyTargetPages: Int,
)

data class PaceTargetDto(
    val id: UUID?,
    val userBookId: UUID,
    val dailyTargetPages: Int,
    val startDate: OffsetDateTime,
    val plannedFinishDate: OffsetDateTime?,
)

data class PaceStatusDto(
    val userBookId: UUID,
    val dailyTargetPages: Int,
    val startDate: OffsetDateTime,
    val plannedFinishDate: OffsetDateTime?,
    val currentPage: Int,
    val pageCount: Int?,
    val expectedPage: Int,
    val pagesAheadBehind: Int,
    val status: PaceStatus,
    val daysElapsed: Int,
    val estimatedFinishDate: OffsetDateTime?,
)
