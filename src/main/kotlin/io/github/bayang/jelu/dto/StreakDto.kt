package io.github.bayang.jelu.dto

import java.time.LocalDate
import java.util.UUID

data class StreakDto(
    val currentDailyStreak: Int,
    val longestDailyStreak: Int,
    val currentWeeklyStreak: Int,
    val longestWeeklyStreak: Int,
)

data class DailyLogEntryDto(
    val date: LocalDate,
    val pagesRead: Int,
)

data class HeatmapDto(
    val entries: List<DailyLogEntryDto>,
)

data class WeeklyBookReportDto(
    val userBookId: UUID,
    val title: String,
    val image: String?,
    val dailyTargetPages: Int,
    val pagesThisWeek: Int,
    val weeklyTargetPages: Int,
    val compliancePercent: Int,
    val onTrack: Boolean,
    val status: PaceStatus,
)

data class WeeklyReportDto(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val totalPagesThisWeek: Int,
    val perfectWeek: Boolean,
    val books: List<WeeklyBookReportDto>,
    val streak: StreakDto,
)
