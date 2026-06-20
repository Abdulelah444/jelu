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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ReadingGoalController(
    private val readingGoalService: ReadingGoalService,
) {
    @Operation(description = "set or update the reading goal")
    @PostMapping(path = ["/userbooks/reading-goal"])
    fun setGoal(
        @RequestBody dto: CreateReadingGoalDto,
        principal: Authentication,
    ): ReadingGoalDto {
        assertIsJeluUser(principal.principal)
        return readingGoalService.setGoal((principal.principal as JeluUser).user.id!!, dto)
    }

    @Operation(description = "get the reading goal with progress")
    @GetMapping(path = ["/userbooks/reading-goal"])
    fun getGoal(
        principal: Authentication,
    ): ResponseEntity<ReadingGoalDto> {
        assertIsJeluUser(principal.principal)
        val goal = readingGoalService.getGoal((principal.principal as JeluUser).user.id!!)
        return if (goal != null) ResponseEntity.ok(goal) else ResponseEntity.noContent().build()
    }

    @Operation(description = "delete the reading goal")
    @DeleteMapping(path = ["/userbooks/reading-goal"])
    fun deleteGoal(
        principal: Authentication,
    ): ResponseEntity<Void> {
        assertIsJeluUser(principal.principal)
        readingGoalService.deleteGoal((principal.principal as JeluUser).user.id!!)
        return ResponseEntity.noContent().build()
    }
}
