package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.BookService
import io.github.bayang.jelu.service.EbookStorageService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/userbooks/{userbookId}/digital")
class DigitalBookController(
    private val bookService: BookService,
    private val ebookStorageService: EbookStorageService,
) {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDigitalFile(
        @PathVariable("userbookId") userbookId: UUID,
        @RequestPart("file") file: MultipartFile,
    ): Map<String, Any?> {
        val userBook = try {
            bookService.findUserBookById(userbookId)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserBook not found")
        }

        val bookId = userBook.book.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Book has no ID")

        try {
            val info = ebookStorageService.save(bookId, file)
            bookService.updateDigitalFileFields(
                userbookId,
                info.path,
                info.format,
                info.sizeBytes,
                Instant.now(),
            )

            logger.info { "Uploaded ebook for userbook $userbookId: ${info.format}, ${info.sizeBytes} bytes" }

            return mapOf(
                "digitalFilePath" to info.path,
                "digitalFileFormat" to info.format,
                "digitalFileSizeBytes" to info.sizeBytes,
                "digitalFileAddedDate" to Instant.now().toString(),
                "originalFilename" to info.originalFilename,
            )
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }
}
