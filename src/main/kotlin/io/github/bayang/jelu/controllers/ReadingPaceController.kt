package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.PaceResult
import io.github.bayang.jelu.service.ProgressEntryDto
import io.github.bayang.jelu.service.ReadingPaceService
import io.github.bayang.jelu.service.ReplaceProgressDto
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import io.github.bayang.jelu.dto.JeluUser
import java.time.OffsetDateTime
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

    @GetMapping("/userbooks/{id}/progress-history")
    fun getProgressHistory(
        @PathVariable("id") userBookId: UUID,
        principal: Authentication,
    ): ResponseEntity<List<ProgressEntryDto>> {
        return ResponseEntity.ok(paceService.getHistory(userBookId))
    }

    @PutMapping("/userbooks/{id}/progress-history")
    fun replaceProgressHistory(
        @PathVariable("id") userBookId: UUID,
        @RequestBody body: ReplaceProgressDto,
        principal: Authentication,
    ): ResponseEntity<List<ProgressEntryDto>> {
        return ResponseEntity.ok(paceService.replaceHistory(userBookId, body))
    }

    @GetMapping("/userbooks/pages-read-since")
    fun pagesRead(
        @RequestParam("since") since: String,
        principal: Authentication,
    ): ResponseEntity<Map<String, Int>> {
        val userId = (principal.principal as JeluUser).user.id!!
        val sinceDate = OffsetDateTime.parse(since)
        return ResponseEntity.ok(mapOf("pages" to paceService.pagesReadSince(userId, sinceDate)))
    }

    @GetMapping("/userbooks/{id}/pages-read-since")
    fun pagesReadForBook(
        @PathVariable("id") userBookId: UUID,
        @RequestParam("since") since: String,
        principal: Authentication,
    ): ResponseEntity<Map<String, Int>> {
        val sinceDate = OffsetDateTime.parse(since)
        return ResponseEntity.ok(mapOf("pages" to paceService.pagesReadForBookSince(userBookId, sinceDate)))
    }
}
