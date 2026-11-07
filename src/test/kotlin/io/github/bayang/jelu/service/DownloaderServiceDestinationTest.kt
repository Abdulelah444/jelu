package io.github.bayang.jelu.service

import io.github.bayang.jelu.config.JeluProperties
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Unit tests for [DownloaderService.checkDestination] — the writability verdict Jelu
 * attaches to Shelfmark's download DESTINATION. Only exercises the filesystem logic, so
 * it builds the service directly without a Spring context or a live Shelfmark.
 */
class DownloaderServiceDestinationTest {

    private fun serviceWithStoragePath(storagePath: String): DownloaderService {
        val props =
            JeluProperties(
                database = JeluProperties.Database(path = ":memory:"),
                files = JeluProperties.Files(images = "/tmp", imports = "/tmp"),
                session = JeluProperties.Session(duration = 60),
                metadataProviders = null,
                ebooks = JeluProperties.Ebooks(storagePath = storagePath),
            )
        return DownloaderService(props, AppSettingsService())
    }

    @Test
    fun `blank destination is flagged not writable`() {
        val service = serviceWithStoragePath(Files.createTempDirectory("jelu-dest").toString())
        val result = service.checkDestination("")
        Assertions.assertFalse(result["writable"] as Boolean)
        Assertions.assertEquals("No download destination is set in Shelfmark.", result["reason"])
    }

    @Test
    fun `blank destination from whitespace is flagged not writable`() {
        val service = serviceWithStoragePath(Files.createTempDirectory("jelu-dest").toString())
        Assertions.assertFalse(service.checkDestination("   ")["writable"] as Boolean)
    }

    @Test
    fun `destination visible inside Jelu and writable reports writable`() {
        val dir = Files.createTempDirectory("jelu-dest").toFile()
        val service = serviceWithStoragePath(Files.createTempDirectory("jelu-shared").toString())
        val result = service.checkDestination(dir.absolutePath)
        Assertions.assertTrue(result["writable"] as Boolean)
        Assertions.assertEquals(dir.absolutePath, result["checkedPath"])
    }

    @Test
    fun `unwritable shared ebooks volume is flagged not writable`() {
        val service = serviceWithStoragePath("/this/path/does/not/exist-for-jelu-dest-check")
        val result = service.checkDestination("/shelfmark/only/path")
        Assertions.assertFalse(result["writable"] as Boolean)
        val reason = result["reason"] as String
        Assertions.assertTrue(reason.contains("/this/path/does/not/exist-for-jelu-dest-check"))
    }

    @Test
    fun `invisible destination falls back to shared ebooks volume and reports writable`() {
        val shared = Files.createTempDirectory("jelu-shared").toFile()
        val service = serviceWithStoragePath(shared.absolutePath)
        // A path Jelu does not mount (Shelfmark-side only): falls back to the shared volume.
        val result = service.checkDestination("/books")
        Assertions.assertTrue(result["writable"] as Boolean)
        Assertions.assertEquals(shared.absolutePath, result["checkedPath"])
    }
}
