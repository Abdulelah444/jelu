package io.github.bayang.jelu.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The downloader's ingest directory changed between versions (/cwa-book-ingest on
 * Shelfmark 1.3.x, /books from 1.6.x). Paths it reports must map to Jelu's mount either
 * way; assuming one prefix is what broke auto-linking after the upgrade.
 */
class FileUtilsTest {
    @Test
    fun `maps the legacy ingest prefix`() {
        assertEquals(
            "/files/ebooks/Dune (1965).epub",
            ebookPathInsideJelu("/cwa-book-ingest/Dune (1965).epub"),
        )
    }

    @Test
    fun `maps the current ingest prefix`() {
        assertEquals(
            "/files/ebooks/Dune (1965).epub",
            ebookPathInsideJelu("/books/Dune (1965).epub"),
        )
    }

    @Test
    fun `keeps a bare relative path`() {
        assertEquals("/files/ebooks/Dune.epub", ebookPathInsideJelu("Dune.epub"))
    }

    @Test
    fun `tolerates a trailing slash on the ebooks root`() {
        assertEquals("/ebooks/Dune.epub", ebookPathInsideJelu("/books/Dune.epub", "/ebooks/"))
    }

    @Test
    fun `relative path strips either prefix`() {
        assertEquals("Author/Title.epub", ebookRelativePath("/books/Author/Title.epub"))
        assertEquals("Author/Title.epub", ebookRelativePath("/cwa-book-ingest/Author/Title.epub"))
    }
}
