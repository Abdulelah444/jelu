package io.github.bayang.jelu.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.bayang.jelu.config.JeluProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class DownloaderService(
    private val properties: JeluProperties,
) {
    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(properties.ebooks.downloaderUrl)
            .build()
    }

    fun search(query: String, lang: String = "en", ext: String = "epub"): CwaSearchResponse {
        logger.info { "Searching CWA for: $query (lang=$lang, ext=$ext)" }
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/releases")
                    .queryParam("source", "direct_download")
                    .queryParam("query", query)
                    .queryParam("lang", lang)
                    .queryParam("ext", ext)
                    .build()
            }
            .retrieve()
            .bodyToMono<CwaSearchResponse>()
            .block(Duration.ofSeconds(properties.ebooks.downloaderTimeoutSeconds.toLong()))
            ?: throw RuntimeException("CWA search returned null")
    }

    fun triggerDownload(release: CwaDownloadRequest) {
        logger.info { "Triggering CWA download: ${release.title} (${release.sourceId})" }
        webClient.post()
            .uri("/api/releases/download")
            .bodyValue(release)
            .retrieve()
            .bodyToMono<String>()
            .block(Duration.ofSeconds(30))
    }

    fun getStatus(): Map<String, Any> {
        return webClient.get()
            .uri("/api/status")
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block(Duration.ofSeconds(10))
            ?: emptyMap()
    }
}

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
