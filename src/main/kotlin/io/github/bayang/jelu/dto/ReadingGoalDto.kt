package io.github.bayang.jelu.dto

import java.util.UUID

data class CreateReadingGoalDto(
    val year: Int,
    val targetBooks: Int,
)

data class ReadingGoalDto(
    val id: UUID?,
    val year: Int,
    val targetBooks: Int,
    val booksFinished: Int,
    val percentComplete: Int,
    val onTrackProjection: Int,
    val booksRemaining: Int,
    val daysRemaining: Int,
    val pacePerBookDays: Double,
    val behindBy: Int,
)
