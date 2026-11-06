package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.dto.HeatmapDto
import io.github.bayang.jelu.dto.JeluUser
import io.github.bayang.jelu.dto.StreakDto
import io.github.bayang.jelu.dto.TimelineDto
import io.github.bayang.jelu.dto.WeeklyReportDto
import io.github.bayang.jelu.dto.assertIsJeluUser
import io.github.bayang.jelu.service.StreakService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class StreakController(
    private val streakService: StreakService,
) {
    @Operation(description = "rebuild the daily reading log from progress history")
    @PostMapping(path = ["/userbooks/streaks/rebuild"])
    fun rebuild(principal: Authentication) {
        assertIsJeluUser(principal.principal)
        streakService.rebuildDailyLog((principal.principal as JeluUser).user.id!!)
    }

    @Operation(description = "get current streaks")
    @GetMapping(path = ["/userbooks/streaks"])
    fun streaks(principal: Authentication): StreakDto {
        assertIsJeluUser(principal.principal)
        return streakService.getStreaks((principal.principal as JeluUser).user.id!!)
    }

    @Operation(description = "get reading heatmap for a date range")
    @GetMapping(path = ["/userbooks/streaks/heatmap"])
    fun heatmap(
        @RequestParam(name = "from", required = false) from: String?,
        @RequestParam(name = "to", required = false) to: String?,
        principal: Authentication,
    ): HeatmapDto {
        assertIsJeluUser(principal.principal)
        val toDate = if (to != null) LocalDate.parse(to) else LocalDate.now()
        val fromDate = if (from != null) LocalDate.parse(from) else toDate.minusYears(1)
        return streakService.getHeatmap((principal.principal as JeluUser).user.id!!, fromDate, toDate)
    }

    @Operation(description = "get the weekly reading report")
    @GetMapping(path = ["/userbooks/streaks/weekly-report"])
    fun weeklyReport(principal: Authentication): WeeklyReportDto {
        assertIsJeluUser(principal.principal)
        return streakService.getWeeklyReport((principal.principal as JeluUser).user.id!!)
    }

    @Operation(description = "get pages read today for a specific book")
    @GetMapping(path = ["/userbooks/{userBookId}/today-pages"])
    fun todayPages(
        @org.springframework.web.bind.annotation.PathVariable userBookId: UUID,
        principal: Authentication,
    ): Map<String, Int> {
        assertIsJeluUser(principal.principal)
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        val pages = streakService.getPagesForBookOnDate(userBookId, today)
        return mapOf("pages" to pages)
    }

    @Operation(description = "get the per-book daily reading streak")
    @GetMapping(path = ["/userbooks/{userBookId}/daily-streak"])
    fun bookDailyStreak(
        @org.springframework.web.bind.annotation.PathVariable userBookId: UUID,
        principal: Authentication,
    ): Map<String, Int> {
        assertIsJeluUser(principal.principal)
        return mapOf("streak" to streakService.getBookDailyStreak(userBookId))
    }

    @Operation(description = "get the reading timeline for a date range")
    @GetMapping(path = ["/userbooks/streaks/timeline"])
    fun timeline(
        @RequestParam(name = "from", required = false) from: String?,
        @RequestParam(name = "to", required = false) to: String?,
        principal: Authentication,
    ): TimelineDto {
        assertIsJeluUser(principal.principal)
        val toDate = if (to != null) LocalDate.parse(to) else LocalDate.now()
        val fromDate = if (from != null) LocalDate.parse(from) else toDate.minusYears(1)
        return streakService.getTimeline((principal.principal as JeluUser).user.id!!, fromDate, toDate)
    }
}
