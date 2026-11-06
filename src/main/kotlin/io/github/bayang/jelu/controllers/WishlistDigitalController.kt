package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.CwaDownloadRequest
import io.github.bayang.jelu.service.CwaSearchResponse
import io.github.bayang.jelu.service.DownloaderService
import io.github.bayang.jelu.utils.ebookRelativePath
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.FileSystemResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.io.File

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
    ): CwaSearchResponse =
        try {
            downloaderService.search(query, lang, ext)
        } catch (e: Exception) {
            logger.error(e) { "CWA wishlist search failed for query: $query" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Downloader search failed: ${e.message}")
        }

    @PostMapping("/wishlist-download")
    fun wishlistDownload(
        @RequestBody release: CwaDownloadRequest,
    ): Map<String, String> =
        try {
            downloaderService.triggerDownload(release)
            mapOf("status" to "download_triggered", "title" to (release.title))
        } catch (e: Exception) {
            logger.error(e) { "CWA wishlist download failed for: ${release.title}" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Download failed: ${e.message}")
        }

    @GetMapping("/wishlist-status")
    fun wishlistStatus(
        @RequestParam("url", required = false) url: String?,
    ): Map<String, Any> {
        val target = url?.trim()?.takeIf { it.isNotEmpty() }
        if (target != null && !target.startsWith("http://") && !target.startsWith("https://")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Shelfmark URL must start with http:// or https://")
        }
        return try {
            downloaderService.getStatus(target)
        } catch (e: Exception) {
            logger.error(e) { "CWA status check failed" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Status check failed: ${e.message}")
        }
    }

    private companion object {
        val SETTINGS_TABS = setOf("mirrors", "network", "downloads")
    }

    @GetMapping("/shelfmark-settings")
    fun shelfmarkSettings(
        @RequestParam("tab") tab: String,
    ): Map<String, Any?> {
        if (tab !in SETTINGS_TABS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Shelfmark settings tab: $tab")
        }
        return try {
            downloaderService.getSettings(tab)
        } catch (e: Exception) {
            logger.error(e) { "CWA settings read failed (tab=$tab)" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Downloader settings read failed: ${e.message}")
        }
    }

    @PutMapping("/shelfmark-settings")
    fun updateShelfmarkSettings(
        @RequestBody body: Map<String, Any>,
    ): Map<String, Any?> {
        val tab =
            body["tab"] as? String
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "tab is required")
        if (tab !in SETTINGS_TABS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Shelfmark settings tab: $tab")
        }
        val values =
            body["values"]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "values is required")

        @Suppress("UNCHECKED_CAST")
        val typedValues = values as Map<String, Any?>
        return try {
            downloaderService.updateSettings(tab, typedValues)
        } catch (e: Exception) {
            logger.error(e) { "CWA settings update failed (tab=$tab)" }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Downloader settings update failed: ${e.message}")
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/ebooks-file")
    fun deleteEbookFile(
        @RequestParam("path") filePath: String,
    ): Map<String, String> {
        val cleanPath = ebookRelativePath(filePath)
        val candidates =
            listOf(
                File("/files/ebooks/$cleanPath"),
                File("/opt/jelu-data/ebooks/$cleanPath"),
                File(filePath),
            )
        val file =
            candidates.firstOrNull { it.exists() && it.isFile }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $cleanPath")
        if (!file.delete()) throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete file")
        logger.info { "Deleted ebook file: ${file.absolutePath}" }
        return mapOf("status" to "deleted", "filename" to file.name)
    }

    @GetMapping("/ebooks-list")
    fun ebooksList(): List<Map<String, String>> {
        val dir = File("/files/ebooks")
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir
            .listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.map {
                mapOf(
                    "filename" to it.name,
                    // Report the prefix Shelfmark currently writes to; both are accepted on the
                    // way back in, so either works for older and newer downloads.
                    "path" to "/books/${it.name}",
                    "serverPath" to it.absolutePath,
                    "format" to it.extension.lowercase(),
                    "size" to "${it.length() / 1024}KB",
                )
            }
            ?: emptyList()
    }

    @GetMapping("/wishlist-file")
    fun wishlistFile(
        @RequestParam("path") filePath: String,
        @RequestParam("inline", required = false, defaultValue = "false") inline: Boolean,
    ): ResponseEntity<FileSystemResource> {
        // The path from Shelfmark is <ingest dir>/filename, historically /cwa-book-ingest/ and
        // now /books/. On the host it maps to /opt/jelu-data/ebooks/filename; inside this
        // container that folder is /files/ebooks.
        val cleanPath = ebookRelativePath(filePath)
        // Try common mount locations
        val candidates =
            listOf(
                File("/opt/jelu-data/ebooks/$cleanPath"),
                File("/files/ebooks/$cleanPath"),
                File(filePath),
            )
        val file =
            candidates.firstOrNull { it.exists() && it.isFile }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $cleanPath")

        val resource = FileSystemResource(file)
        val filename = file.name
        val mediaType =
            when (file.extension.lowercase()) {
                "pdf" -> MediaType.APPLICATION_PDF
                "epub" -> MediaType.parseMediaType("application/epub+zip")
                "mobi", "azw3" -> MediaType.APPLICATION_OCTET_STREAM
                else -> MediaType.APPLICATION_OCTET_STREAM
            }

        val disposition =
            if (inline && file.extension.lowercase() == "pdf") {
                ContentDisposition.inline().filename(filename).build()
            } else {
                ContentDisposition.attachment().filename(filename).build()
            }

        return ResponseEntity
            .ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(resource)
    }
}
