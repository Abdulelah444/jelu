package io.github.bayang.jelu.service

import io.github.bayang.jelu.config.JeluProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class EbookStorageService(
    private val properties: JeluProperties,
) {
    fun storagePath(): Path = Paths.get(properties.ebooks.storagePath)

    fun init() {
        val path = storagePath()
        if (!Files.exists(path)) {
            Files.createDirectories(path)
            logger.info { "Created ebooks storage directory at $path" }
        }
    }

    fun save(bookId: UUID, file: MultipartFile): EbookFileInfo {
        init()
        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast('.', "").lowercase()

        // Validate extension
        require(extension in properties.ebooks.allowedExtensions) {
            "File extension '$extension' not allowed. Allowed: ${properties.ebooks.allowedExtensions}"
        }

        // Validate size
        val maxBytes = properties.ebooks.maxFileSizeMb.toLong() * 1024 * 1024
        require(file.size <= maxBytes) {
            "File size ${file.size} exceeds maximum ${properties.ebooks.maxFileSizeMb} MB"
        }

        // Validate bookId is a real UUID (no path traversal)
        val safeId = bookId.toString()
        require(safeId.matches(Regex("^[0-9a-fA-F-]+$"))) {
            "Invalid book ID format"
        }

        val filename = "$safeId.$extension"
        val targetPath = storagePath().resolve(filename)

        // Delete any existing file for this book (different extension)
        deleteExistingFiles(bookId)

        file.inputStream.use { input ->
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }

        logger.info { "Saved ebook for book $bookId: $filename (${file.size} bytes)" }

        return EbookFileInfo(
            path = targetPath.toString(),
            format = extension,
            sizeBytes = file.size,
            originalFilename = originalFilename,
        )
    }

    fun delete(bookId: UUID) {
        deleteExistingFiles(bookId)
        logger.info { "Deleted ebook files for book $bookId" }
    }

    private fun deleteExistingFiles(bookId: UUID) {
        val safeId = bookId.toString()
        properties.ebooks.allowedExtensions.forEach { ext ->
            val path = storagePath().resolve("$safeId.$ext")
            if (Files.exists(path)) {
                Files.delete(path)
                logger.debug { "Deleted existing ebook: $path" }
            }
        }
    }

    fun getFilePath(bookId: UUID, format: String): Path {
        return storagePath().resolve("${bookId}.$format")
    }
}

data class EbookFileInfo(
    val path: String,
    val format: String,
    val sizeBytes: Long,
    val originalFilename: String,
)
