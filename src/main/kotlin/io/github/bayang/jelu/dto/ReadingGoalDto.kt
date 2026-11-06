package io.github.bayang.jelu.dto

import java.time.LocalDate
import java.util.UUID

enum class GoalPeriodType {
    ANNUAL,
    MONTHLY,
}

data class CreateReadingGoalDto(
    val targetBooks: Int,
    val periodType: GoalPeriodType = GoalPeriodType.ANNUAL,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val repeating: Boolean = false,
    // legacy field, optional
    val year: Int? = null,
)

data class ReadingGoalDto(
    val id: UUID?,
    val targetBooks: Int,
    val periodType: GoalPeriodType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val repeating: Boolean,
    val booksFinished: Int,
    val percentComplete: Int,
    val onTrackProjection: Int,
    val booksRemaining: Int,
    val daysRemaining: Int,
    val daysElapsed: Int,
    val periodLengthDays: Int,
    val behindBy: Int,
    val periodLabel: String,
)
