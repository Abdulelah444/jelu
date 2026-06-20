package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.dto.CreateReadingGoalDto
import io.github.bayang.jelu.dto.JeluUser
import io.github.bayang.jelu.dto.ReadingGoalDto
import io.github.bayang.jelu.dto.assertIsJeluUser
import io.github.bayang.jelu.service.ReadingGoalService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1")
class ReadingGoalController(
    private val readingGoalService: ReadingGoalService,
) {
    @Operation(description = "set or update the yearly reading goal")
    @PostMapping(path = ["/userbooks/reading-goal"])
    fun setGoal(
        @RequestBody dto: CreateReadingGoalDto,
        principal: Authentication,
    ): ReadingGoalDto {
        assertIsJeluUser(principal.principal)
        return readingGoalService.setGoal((principal.principal as JeluUser).user.id!!, dto)
    }

    @Operation(description = "get the yearly reading goal with progress")
    @GetMapping(path = ["/userbooks/reading-goal"])
    fun getGoal(
        @RequestParam(name = "year", required = false) year: Int?,
        principal: Authentication,
    ): ResponseEntity<ReadingGoalDto> {
        assertIsJeluUser(principal.principal)
        val y = year ?: LocalDate.now().year
        val goal = readingGoalService.getGoal((principal.principal as JeluUser).user.id!!, y)
        return if (goal != null) ResponseEntity.ok(goal) else ResponseEntity.noContent().build()
    }

    @Operation(description = "delete the yearly reading goal")
    @DeleteMapping(path = ["/userbooks/reading-goal"])
    fun deleteGoal(
        @RequestParam(name = "year", required = false) year: Int?,
        principal: Authentication,
    ): ResponseEntity<Void> {
        assertIsJeluUser(principal.principal)
        val y = year ?: LocalDate.now().year
        readingGoalService.deleteGoal((principal.principal as JeluUser).user.id!!, y)
        return ResponseEntity.noContent().build()
    }
}
