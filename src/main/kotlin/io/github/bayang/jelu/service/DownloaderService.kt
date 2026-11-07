package io.github.bayang.jelu.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.bayang.jelu.config.JeluProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import java.io.File
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class DownloaderService(
    private val properties: JeluProperties,
    private val appSettingsService: AppSettingsService,
) {
    private fun client(baseUrl: String? = null): WebClient {
        val url = baseUrl ?: appSettingsService.getString("shelfmark_url", properties.ebooks.downloaderUrl)
        val builder =
            WebClient
                .builder()
                .baseUrl(url)
        if (properties.ebooks.apiKey.isNotBlank()) {
            builder.defaultHeader("Authorization", "Bearer ${properties.ebooks.apiKey}")
        }
        return builder.build()
    }

    fun search(
        query: String,
        lang: String = "en",
        ext: String = "epub",
    ): CwaSearchResponse {
        logger.info { "Searching CWA for: $query (lang=$lang, ext=$ext)" }
        return client()
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/releases")
                    .queryParam("source", "direct_download")
                    .queryParam("query", query)
                    .queryParam("lang", lang)
                    .queryParam("ext", ext)
                    .build()
            }.retrieve()
            .bodyToMono<CwaSearchResponse>()
            .block(Duration.ofSeconds(properties.ebooks.downloaderTimeoutSeconds.toLong()))
            ?: throw RuntimeException("CWA search returned null")
    }

    /**
     * Stream release candidates one at a time.
     *
     * Shelfmark's `/api/releases` answers with a single buffered JSON body containing
     * all releases, so the search itself still resolves in one call. Each candidate is
     * then re-emitted as its own event, spaced slightly apart, so the UI can render
     * options as they "arrive" instead of waiting for the whole batch. A final event
     * with type `"done"` tells the client the stream is finished; `"error"` carries a
     * [CwaStreamEvent.message].
     */
    fun searchStream(
        query: String,
        lang: String = "en",
        ext: String = "epub",
    ): Flux<CwaStreamEvent> {
        logger.info { "Streaming CWA search for: $query (lang=$lang, ext=$ext)" }
        val response =
            try {
                search(query, lang, ext)
            } catch (e: Exception) {
                return Flux.just(CwaStreamEvent("error", null, e.message))
            }
        val releases = response.releases
        if (releases.isEmpty()) {
            return Flux.just(CwaStreamEvent("done", null, "No releases found"))
        }
        val releaseEvents =
            Flux
                .fromIterable(releases)
                .map { release -> CwaStreamEvent("release", release, null) }
                .delayElements(Duration.ofMillis(120))
        return releaseEvents.concatWith(Flux.just(CwaStreamEvent("done", null, null)))
    }

    fun triggerDownload(release: CwaDownloadRequest) {
        logger.info { "Triggering CWA download: ${release.title} (${release.sourceId})" }
        client()
            .post()
            .uri("/api/releases/download")
            .bodyValue(release)
            .retrieve()
            .bodyToMono<String>()
            .block(Duration.ofSeconds(30))
    }

    fun getStatus(baseUrl: String? = null): Map<String, Any> =
        client(baseUrl)
            .get()
            .uri("/api/status")
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block(Duration.ofSeconds(10))
            ?: emptyMap()

    fun getSettings(tab: String): Map<String, Any?> =
        client()
            .get()
            .uri("/api/settings/{tab}", tab)
            .retrieve()
            .bodyToMono<Map<String, Any?>>()
            .block(Duration.ofSeconds(properties.ebooks.downloaderTimeoutSeconds.toLong()))
            ?: emptyMap()

    /**
     * Validate Shelfmark's download destination as seen from this (Jelu) container.
     *
     * Shelfmark writes downloaded books to its `DESTINATION` (the shared ebooks volume,
     * mounted inside Jelu at [JeluProperties.Ebooks.storagePath]). Jelu cannot see the
     * path names Shelfmark uses inside its own container, so a true filesystem check of
     * the Shelfmark string is impossible here. What Jelu *can* verify is that the shared
     * ebooks volume it mounts is writable — the exact condition that makes downloads fail
     * with "Cannot create destination (Permission denied)". When the destination path is
     * also visible inside Jelu (same mount), it is checked directly as the stricter signal.
     */
    fun checkDestination(destination: String?): Map<String, Any> {
        if (destination.isNullOrBlank()) {
            return mapOf(
                "writable" to false,
                "reason" to "No download destination is set in Shelfmark.",
            )
        }
        // If Jelu sees the same path (identical mounts), validate it directly.
        val direct = File(destination)
        if (direct.exists() && direct.isDirectory) {
            if (direct.canWrite()) {
                return mapOf("writable" to true, "checkedPath" to destination)
            }
            return mapOf(
                "writable" to false,
                "reason" to "The destination $destination is not writable by this container.",
            )
        }
        // Otherwise fall back to the shared ebooks volume Jelu mounts for Shelfmark.
        val storage = File(properties.ebooks.storagePath)
        val sharedWritable = storage.isDirectory && storage.canWrite()
        if (sharedWritable) {
            return mapOf(
                "writable" to true,
                "checkedPath" to properties.ebooks.storagePath,
                "note" to "Validated the shared ebooks volume ${properties.ebooks.storagePath} that Shelfmark writes into; the Shelfmark-side path $destination itself is only checkable inside the Shelfmark container.",
            )
        }
        return mapOf(
            "writable" to false,
            "reason" to "The shared ebooks folder ${properties.ebooks.storagePath} is not writable by this container; downloads to $destination will fail with permission denied.",
        )
    }

    fun updateSettings(
        tab: String,
        values: Map<String, Any?>,
    ): Map<String, Any?> =
        client()
            .put()
            .uri("/api/settings/{tab}", tab)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(values)
            .retrieve()
            .bodyToMono<Map<String, Any?>>()
            .block(Duration.ofSeconds(properties.ebooks.downloaderTimeoutSeconds.toLong()))
            ?: emptyMap()
}

/**
 * One Server-Sent-Event payload from the candidate search stream. `type` is either
 * `"release"`, `"done"` or `"error"`; [release] is populated only for `"release"` events.
 */
data class CwaStreamEvent(
    val type: String,
    val release: CwaRelease? = null,
    val message: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CwaSearchResponse(
    val releases: List<CwaRelease> = emptyList(),
    val book: CwaBookInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CwaRelease(
    val title: String? = null,
    val format: String? = null,
    val size: String? = null,
    @JsonProperty("size_bytes") val sizeBytes: Long? = null,
    val language: String? = null,
    val source: String? = null,
    @JsonProperty("source_id") val sourceId: String? = null,
    @JsonProperty("info_url") val infoUrl: String? = null,
    @JsonProperty("download_url") val downloadUrl: String? = null,
    val protocol: String? = null,
    @JsonProperty("content_type") val contentType: String? = null,
    val indexer: String? = null,
    val extra: Map<String, Any?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CwaBookInfo(
    val title: String? = null,
    val authors: List<String>? = null,
    @JsonProperty("cover_url") val coverUrl: String? = null,
    @JsonProperty("provider_id") val providerId: String? = null,
)

data class CwaDownloadRequest(
    val source: String,
    @JsonProperty("source_id") val sourceId: String,
    val title: String,
    val author: String? = null,
    val format: String? = null,
    val size: String? = null,
    @JsonProperty("size_bytes") val sizeBytes: Long? = null,
    @JsonProperty("download_url") val downloadUrl: String? = null,
    val protocol: String? = null,
)
