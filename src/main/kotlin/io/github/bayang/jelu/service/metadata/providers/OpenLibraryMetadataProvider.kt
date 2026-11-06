package io.github.bayang.jelu.service.metadata.providers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.bayang.jelu.dto.MetadataDto
import io.github.bayang.jelu.dto.MetadataRequestDto
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriBuilder
import java.time.Duration
import java.util.Optional

private val logger = KotlinLogging.logger {}

@Service
class OpenLibraryMetadataProvider(
    @Resource(name = "restClient") private val restClient: WebClient,
    private val objectMapper: ObjectMapper,
) : IMetaDataProvider {
    private val name = "openlibrary"

    override fun name(): String = name

    override fun fetchMetadata(
        metadataRequestDto: MetadataRequestDto,
        config: Map<String, String>,
    ): Optional<MetadataDto>? = Optional.empty()

    override fun searchMetadata(
        metadataRequestDto: MetadataRequestDto,
        config: Map<String, String>,
    ): List<MetadataDto> {
        val query = buildQuery(metadataRequestDto)
        if (query.isBlank()) return emptyList()

        val res =
            restClient
                .get()
                .uri { uriBuilder: UriBuilder ->
                    uriBuilder
                        .scheme("https")
                        .host("openlibrary.org")
                        .path("/search.json")
                        .queryParam("q", query)
                        .queryParam("limit", 20)
                        .queryParam(
                            "fields",
                            "title,author_name,isbn,cover_i,first_publish_year,publisher,number_of_pages_median,language,subject,key",
                        ).build()
                }.exchangeToMono {
                    if (it.statusCode() == HttpStatus.OK) {
                        it.bodyToMono(String::class.java).map { bodyString ->
                            val docs = objectMapper.readTree(bodyString).get("docs")
                            if (docs == null || docs.isEmpty) {
                                emptyList<MetadataDto>()
                            } else {
                                docs
                                    .asIterable()
                                    .mapNotNull { doc -> safeParseDoc(doc) }
                                    .distinctBy { dto -> dto.isbn13 ?: dto.title }
                            }
                        }
                    } else {
                        logger.error { "error searching openlibrary: ${it.statusCode()}" }
                        it.bodyToMono(String::class.java).map { emptyList<MetadataDto>() }
                    }
                }.block(Duration.ofSeconds(60))

        return res ?: emptyList()
    }

    private fun buildQuery(metadataRequestDto: MetadataRequestDto): String =
        when {
            !metadataRequestDto.isbn.isNullOrBlank() -> "isbn:${metadataRequestDto.isbn}"
            !metadataRequestDto.title.isNullOrBlank() && !metadataRequestDto.authors.isNullOrBlank() ->
                "title:${metadataRequestDto.title} author:${metadataRequestDto.authors}"
            !metadataRequestDto.title.isNullOrBlank() -> metadataRequestDto.title
            !metadataRequestDto.authors.isNullOrBlank() -> "author:${metadataRequestDto.authors}"
            else -> ""
        }

    private fun safeParseDoc(doc: JsonNode): MetadataDto? {
        return try {
            val title = doc.get("title")?.asText() ?: return null
            if (title.isBlank()) return null

            val authors =
                doc
                    .get("author_name")
                    ?.asIterable()
                    ?.map { it.asText() }
                    ?.toMutableSet() ?: mutableSetOf()

            val isbns = doc.get("isbn")?.asIterable()?.map { it.asText() } ?: emptyList()
            val isbn13 = isbns.firstOrNull { it.length == 13 }
            val isbn10 = isbns.firstOrNull { it.length == 10 }

            val coverId = doc.get("cover_i")?.asLong()
            val image = if (coverId != null) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null

            val year = doc.get("first_publish_year")?.asText()
            val pages = doc.get("number_of_pages_median")?.asInt()
            val publisher =
                doc
                    .get("publisher")
                    ?.asIterable()
                    ?.firstOrNull()
                    ?.asText()
            val language =
                doc
                    .get("language")
                    ?.asIterable()
                    ?.firstOrNull()
                    ?.asText()

            MetadataDto(
                title = title,
                authors = authors,
                isbn13 = isbn13,
                isbn10 = isbn10,
                image = image,
                publishedDate = year,
                pageCount = pages,
                publisher = publisher,
                language = language,
            )
        } catch (e: Exception) {
            logger.debug { "skipping unparseable openlibrary doc: ${e.message}" }
            null
        }
    }
}
