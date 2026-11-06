package io.github.bayang.jelu.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The typed getters call getRaw() as a self-invocation, so Spring's proxy never opens a
 * transaction for them. Without @Transactional on the getters themselves the Exposed read
 * throws "No transaction in context" whenever they are reached from a thread that has no
 * ambient transaction — which is how DownloaderService calls them from a controller.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppSettingsServiceTest(
    @Autowired private val appSettingsService: AppSettingsService,
) {
    @Test
    fun `typed getters work outside an explicit transaction`() {
        appSettingsService.setRaw("test-key", "some-value")

        Assertions.assertEquals("some-value", appSettingsService.getString("test-key", "fallback"))
        Assertions.assertEquals("fallback", appSettingsService.getString("missing-key", "fallback"))
        Assertions.assertEquals(12, appSettingsService.getInt("missing-key", 12))
        Assertions.assertTrue(appSettingsService.getBool("missing-key", true))
        Assertions.assertFalse(appSettingsService.getBool("missing-key", false))

        appSettingsService.delete("test-key")
    }
}
