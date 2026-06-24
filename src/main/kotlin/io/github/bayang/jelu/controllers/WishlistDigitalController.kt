package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.CwaDownloadRequest
import io.github.bayang.jelu.service.CwaSearchResponse
import io.github.bayang.jelu.service.DownloaderService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.FileSystemResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/userbooks/digital")
class WishlistDigitalController(
    private val downloaderService: DownloaderService,
) {
    @GetMapping("/wishlist-search")
    fun wishlistSearch(
        @RequestParam("query") query: String,
        @RequestParam("lang", required = false, defaultValue = "en") lang: String,
        @RequestParam("ext", required = false, defaultValue = "epub") ext: String,
    ): CwaSearchResponse {
        return try {
            downloaderService.search(query, lang, ext)
        } catch (e: Exception) {
            logger.error(e) { "CWA wishlist search failed for query: $query" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Downloader search failed: ${e.message}")
        }
    }

    @PostMapping("/wishlist-download")
    fun wishlistDownload(
        @RequestBody release: CwaDownloadRequest,
    ): Map<String, String> {
        return try {
            downloaderService.triggerDownload(release)
            mapOf("status" to "download_triggered", "title" to (release.title))
        } catch (e: Exception) {
            logger.error(e) { "CWA wishlist download failed for: ${release.title}" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Download failed: ${e.message}")
        }
    }

    @GetMapping("/wishlist-status")
    fun wishlistStatus(): Map<String, Any> {
        return try {
            downloaderService.getStatus()
        } catch (e: Exception) {
            logger.error(e) { "CWA status check failed" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Status check failed: ${e.message}")
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/ebooks-file")
    fun deleteEbookFile(
        @RequestParam("path") filePath: String,
    ): Map<String, String> {
        val cleanPath = filePath.removePrefix("/cwa-book-ingest/")
        val candidates = listOf(
            File("/files/ebooks/$cleanPath"),
            File("/opt/jelu-data/ebooks/$cleanPath"),
            File(filePath),
        )
        val file = candidates.firstOrNull { it.exists() && it.isFile }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $cleanPath")
        if (!file.delete()) throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete file")
        logger.info { "Deleted ebook file: ${file.absolutePath}" }
        return mapOf("status" to "deleted", "filename" to file.name)
    }

    @GetMapping("/ebooks-list")
    fun ebooksList(): List<Map<String, String>> {
        val dir = File("/files/ebooks")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.map { mapOf(
                "filename" to it.name,
                "path" to "/cwa-book-ingest/${it.name}",
                "serverPath" to it.absolutePath,
                "format" to it.extension.lowercase(),
                "size" to "${it.length() / 1024}KB"
            ) }
            ?: emptyList()
    }

    @GetMapping("/wishlist-file")
    fun wishlistFile(
        @RequestParam("path") filePath: String,
        @RequestParam("inline", required = false, defaultValue = "false") inline: Boolean,
    ): ResponseEntity<FileSystemResource> {
        // The path from CWA is /cwa-book-ingest/filename
        // On the host it maps to /opt/jelu-data/ebooks/filename
        // Inside the Jelu container it should be mounted at the same path or we strip prefix
        val cleanPath = filePath.removePrefix("/cwa-book-ingest/")
        // Try common mount locations
        val candidates = listOf(
            File("/opt/jelu-data/ebooks/$cleanPath"),
            File("/files/ebooks/$cleanPath"),
            File(filePath),
        )
        val file = candidates.firstOrNull { it.exists() && it.isFile }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $cleanPath")

        val resource = FileSystemResource(file)
        val filename = file.name
        val mediaType = when (file.extension.lowercase()) {
            "pdf" -> MediaType.APPLICATION_PDF
            "epub" -> MediaType.parseMediaType("application/epub+zip")
            "mobi", "azw3" -> MediaType.APPLICATION_OCTET_STREAM
            else -> MediaType.APPLICATION_OCTET_STREAM
        }

        val disposition = if (inline && file.extension.lowercase() == "pdf") {
            ContentDisposition.inline().filename(filename).build()
        } else {
            ContentDisposition.attachment().filename(filename).build()
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(resource)
    }
}
