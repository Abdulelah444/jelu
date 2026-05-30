package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.BookService
import io.github.bayang.jelu.service.BooxPushService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/boox")
class BooxPushController(
    private val bookService: BookService,
    private val booxService: BooxPushService,
) {
    @GetMapping("/status")
    fun status(principal: Authentication): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(booxService.getStatus())
    }

    @PostMapping("/request-code")
    fun requestCode(
        @RequestBody body: Map<String, String>,
        principal: Authentication,
    ): ResponseEntity<Map<String, Any>> {
        val email = body["email"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "email required"))
        val cloud = body["cloud"] ?: "push.boox.com"
        return ResponseEntity.ok(booxService.requestVerificationCode(email, cloud))
    }

    @PostMapping("/obtain-token")
    fun obtainToken(
        @RequestBody body: Map<String, String>,
        principal: Authentication,
    ): ResponseEntity<Map<String, Any>> {
        val email = body["email"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "email required"))
        val code = body["code"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "code required"))
        val cloud = body["cloud"] ?: "push.boox.com"
        return ResponseEntity.ok(booxService.obtainToken(email, code, cloud))
    }

    @PostMapping("/send/{userbookId}")
    fun sendToBoox(
        @PathVariable userbookId: UUID,
        principal: Authentication,
    ): ResponseEntity<Map<String, Any>> {
        val userBook = bookService.findUserBookById(userbookId)
        val filePath = userBook.digitalFilePath
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "No digital file attached"))

        if (!Files.exists(Paths.get(filePath))) {
            return ResponseEntity.badRequest().body(mapOf("error" to "File not found on disk"))
        }

        val result = booxService.sendFile(filePath)

        if (result["success"] == true) {
            // Update lastSentToReaderDate
            bookService.updateDigitalFileFields(
                userbookId,
                userBook.digitalFilePath,
                userBook.digitalFileFormat,
                userBook.digitalFileSizeBytes,
                userBook.digitalFileAddedDate ?: Instant.now(),
            )
        }

        return ResponseEntity.ok(result)
    }
}
