package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.PaceResult
import io.github.bayang.jelu.service.ReadingPaceService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class ReadingPaceController(
    private val paceService: ReadingPaceService,
) {
    @GetMapping("/userbooks/{id}/pace")
    fun getPace(
        @PathVariable("id") userBookId: UUID,
        @RequestParam(defaultValue = "since_start") period: String,
        principal: Authentication,
    ): ResponseEntity<PaceResult> {
        val result = paceService.computePace(userBookId, period)
        return if (result != null) ResponseEntity.ok(result) else ResponseEntity.noContent().build()
    }

    @GetMapping("/userbooks/{id}/pace-all")
    fun getAllPaces(
        @PathVariable("id") userBookId: UUID,
        principal: Authentication,
    ): ResponseEntity<Map<String, io.github.bayang.jelu.service.PaceResult?>> {
        return ResponseEntity.ok(paceService.computeAllPaces(userBookId))
    }
}
