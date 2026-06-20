package io.github.bayang.jelu.dto

import java.time.LocalDate
import java.util.UUID

data class TimelineDayDto(
    val date: LocalDate,
    val pagesRead: Int,
)

data class TimelineBookDto(
    val userBookId: UUID,
    val title: String,
    val image: String?,
    val startDate: LocalDate?,
    val finishDate: LocalDate?,
    val status: String,
    val days: List<TimelineDayDto>,
    val pageCount: Int?,
)

data class TimelineDto(
    val books: List<TimelineBookDto>,
)
