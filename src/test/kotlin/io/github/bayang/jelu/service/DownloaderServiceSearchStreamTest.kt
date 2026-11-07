package io.github.bayang.jelu.service

import com.sun.net.httpserver.HttpServer
import io.github.bayang.jelu.config.JeluProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Tests for [DownloaderService.searchStream]: the shelfmark `/api/releases` body is
 * re-emitted as one `release` event per candidate plus a terminal `done`. Uses a local
 * stub server so no live Shelfmark is needed.
 */
class DownloaderServiceSearchStreamTest {
    private lateinit var server: HttpServer
    private var baseUrl: String = ""

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun stubReleases(body: String) {
        server.createContext("/api/releases") { exchange ->
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    private fun service(): DownloaderService {
        // AppSettingsService touches the database; stub the one setting the service reads
        // (shelfmark_url) so the test needs neither Exposed nor a DB.
        val appSettings =
            mockk<AppSettingsService> {
                every { getString("shelfmark_url", any()) } answers { secondArg() }
            }
        return DownloaderService(
            JeluProperties(
                database = JeluProperties.Database(path = ":memory:"),
                files = JeluProperties.Files(images = "/tmp", imports = "/tmp"),
                session = JeluProperties.Session(duration = 60),
                metadataProviders = null,
                ebooks = JeluProperties.Ebooks(downloaderUrl = baseUrl),
            ),
            appSettings,
        )
    }

    @Test
    fun `streams one release event per candidate then done`() {
        stubReleases(
            """
            {
              "releases": [
                {"title": "Book (epub)", "format": "epub", "source": "direct_download", "source_id": "a"},
                {"title": "Book (pdf)", "format": "pdf", "source": "direct_download", "source_id": "b"}
              ]
            }
            """.trimIndent(),
        )

        val events = service().searchStream("query").collectList().block()!!

        Assertions.assertEquals(3, events.size)
        Assertions.assertEquals("release", events[0].type)
        Assertions.assertEquals("a", events[0].release?.sourceId)
        Assertions.assertEquals("release", events[1].type)
        Assertions.assertEquals("b", events[1].release?.sourceId)
        Assertions.assertEquals("done", events[2].type)
    }

    @Test
    fun `empty search emits a single done event`() {
        stubReleases("""{"releases": []}""")

        val events = service().searchStream("query").collectList().block()!!

        Assertions.assertEquals(1, events.size)
        Assertions.assertEquals("done", events[0].type)
    }

    @Test
    fun `unreachable downloader emits a single error event`() {
        // Do not start the server so the connection is refused.
        server.stop(0)
        val events = service().searchStream("query").collectList().block()!!

        Assertions.assertEquals(1, events.size)
        Assertions.assertEquals("error", events[0].type)
    }

    @Test
    fun `search stream completes reactively`() {
        stubReleases("""{"releases": [{"title": "One", "source_id": "x"}]}""")

        StepVerifier
            .create(service().searchStream("query"))
            .expectNextMatches { it.type == "release" }
            .expectNextMatches { it.type == "done" }
            .verifyComplete()
    }
}
