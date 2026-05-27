package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.BookService
import io.github.bayang.jelu.service.CwaDownloadRequest
import io.github.bayang.jelu.service.CwaRelease
import io.github.bayang.jelu.service.CwaSearchResponse
import io.github.bayang.jelu.service.DownloaderService
import io.github.bayang.jelu.service.EbookStorageService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
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
    private val downloaderService: DownloaderService,
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

    @GetMapping("/search-candidates")
    fun searchCandidates(
        @PathVariable("userbookId") userbookId: UUID,
        @RequestParam("query", required = false) query: String?,
        @RequestParam("lang", required = false, defaultValue = "en") lang: String,
        @RequestParam("ext", required = false, defaultValue = "epub") ext: String,
    ): CwaSearchResponse {
        val userBook = try {
            bookService.findUserBookById(userbookId)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserBook not found")
        }

        val searchQuery = query ?: buildSearchQuery(userBook)
        return try {
            downloaderService.search(searchQuery, lang, ext)
        } catch (e: Exception) {
            logger.error(e) { "CWA search failed for query: $searchQuery" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Downloader search failed: ${e.message}")
        }
    }

    @PostMapping("/download")
    fun triggerDownload(
        @PathVariable("userbookId") userbookId: UUID,
        @RequestBody release: CwaDownloadRequest,
    ): Map<String, String> {
        val userBook = try {
            bookService.findUserBookById(userbookId)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserBook not found")
        }

        try {
            downloaderService.triggerDownload(release)
            logger.info { "Download triggered for userbook $userbookId: ${release.title}" }
            return mapOf("status" to "download_triggered", "title" to (release.title))
        } catch (e: Exception) {
            logger.error(e) { "CWA download failed for: ${release.title}" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Download failed: ${e.message}")
        }
    }

    @DeleteMapping
    fun deleteDigitalFile(
        @PathVariable("userbookId") userbookId: UUID,
    ): Map<String, String> {
        val userBook = try {
            bookService.findUserBookById(userbookId)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "UserBook not found")
        }

        val bookId = userBook.book.id
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Book has no ID")

        ebookStorageService.delete(bookId)
        bookService.updateDigitalFileFields(userbookId, null, null, null, null)

        return mapOf("status" to "deleted")
    }

    private fun buildSearchQuery(userBook: io.github.bayang.jelu.dto.UserBookLightDto): String {
        val parts = mutableListOf<String>()
        parts.add(userBook.book.title)
        userBook.book.authors?.firstOrNull()?.let { parts.add(it.name) }
        return parts.joinToString(" ")
    }
}
