package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.Book
import io.github.bayang.jelu.dao.BookTable
import io.github.bayang.jelu.dao.MessageCategory
import io.github.bayang.jelu.dto.BookDto
import io.github.bayang.jelu.dto.CreateUserMessageDto
import io.github.bayang.jelu.dto.MetadataRequestDto
import io.github.bayang.jelu.dto.UserDto
import io.github.bayang.jelu.service.metadata.FetchMetadataService
import io.github.bayang.jelu.service.metadata.PluginInfoHolder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
class BookEnrichmentService(
    private val userMessageService: UserMessageService,
    private val fetchMetadataService: FetchMetadataService,
    private val pluginInfoHolder: PluginInfoHolder,
) {

    @Async
    @Transactional
    fun enrichBook(book: BookDto, user: UserDto) {
        try {
            val missingPageCount = book.pageCount == null
            val missingSummary = book.summary.isNullOrBlank()
            val missingPublisher = book.publisher.isNullOrBlank()
            val missingLanguage = book.language.isNullOrBlank()
            val missingSeries = book.series.isNullOrEmpty()

            val needsMetadataFetch = missingPageCount || missingSummary || missingPublisher

            if (missingSeries) {
                val titleHint = SeriesDetectionService.detectFromTitle(book.title)
                if (titleHint != null) {
                    logger.info { "Series hint from title for \"${book.title}\": ${titleHint.detail}" }
                    val message = "\"${book.title}\" may be part of a series. ${titleHint.detail}" +
                        (if (titleHint.suggestedPosition != null) " (position ${titleHint.suggestedPosition.toInt()})" else "") +
                        ". Check and assign the series if appropriate."
                    userMessageService.save(
                        CreateUserMessageDto(
                            message = message,
                            link = "/book/${book.id}",
                            category = MessageCategory.INFO,
                        ),
                        user,
                    )
                }
            }

            if (needsMetadataFetch && pluginInfoHolder.plugins().isNotEmpty()) {
                val isbn = book.isbn13 ?: book.isbn10
                val authorsString = book.authors?.joinToString(", ") { it.name }

                if (isbn.isNullOrBlank() && book.title.isBlank()) {
                    logger.debug { "No ISBN or title for book ${book.id}, skipping metadata fetch" }
                    return
                }

                logger.info { "Fetching metadata for \"${book.title}\" (ISBN: $isbn)" }
                val metadata = fetchMetadataService.fetchMetadata(
                    MetadataRequestDto(
                        isbn = isbn,
                        title = book.title,
                        authors = authorsString,
                    ),
                )

                val updates = mutableListOf<String>()

                BookTable.update({ BookTable.id eq book.id }) { stmt ->
                    if (missingPageCount && metadata.pageCount != null && metadata.pageCount!! > 0) {
                        stmt[BookTable.pageCount] = metadata.pageCount
                        updates.add("page count (${metadata.pageCount})")
                    }
                    if (missingSummary && !metadata.summary.isNullOrBlank()) {
                        stmt[BookTable.summary] = metadata.summary
                        updates.add("summary")
                    }
                    if (missingPublisher && !metadata.publisher.isNullOrBlank()) {
                        stmt[BookTable.publisher] = metadata.publisher
                        updates.add("publisher")
                    }
                    if (missingLanguage && !metadata.language.isNullOrBlank()) {
                        stmt[BookTable.language] = metadata.language
                        updates.add("language")
                    }
                }

                if (updates.isNotEmpty()) {
                    logger.info { "Auto-filled for \"${book.title}\": ${updates.joinToString(", ")}" }
                }

                if (missingSeries && !metadata.series.isNullOrBlank()) {
                    val positionText = if (metadata.numberInSeries != null) " (#${metadata.numberInSeries!!.toInt()})" else ""
                    val message = "\"${book.title}\" may be part of series \"${metadata.series}\"$positionText. " +
                        "This was found via metadata lookup. Check and assign the series if appropriate."
                    userMessageService.save(
                        CreateUserMessageDto(
                            message = message,
                            link = "/book/${book.id}",
                            category = MessageCategory.INFO,
                        ),
                        user,
                    )
                    logger.info { "Series suggestion for \"${book.title}\": ${metadata.series}$positionText" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to enrich book \"${book.title}\"" }
        }
    }

    @Async
    @Transactional
    fun enrichAllBooks(user: UserDto) {
        logger.info { "Starting bulk enrichment of all books with missing data..." }
        if (pluginInfoHolder.plugins().isEmpty()) {
            logger.warn { "No metadata plugins configured, skipping bulk enrichment" }
            return
        }

        val booksToEnrich = Book.wrapRows(
            BookTable.selectAll().where {
                (BookTable.pageCount eq null) or
                (BookTable.summary eq null) or
                (BookTable.publisher eq null)
            }
        ).toList()

        logger.info { "Found ${booksToEnrich.size} books to enrich" }
        var enriched = 0
        var failed = 0

        for (book in booksToEnrich) {
            try {
                Thread.sleep(1500)

                val isbn = book.isbn13 ?: book.isbn10
                val authorsString = book.authors.joinToString(", ") { it.name }

                if (isbn.isNullOrBlank() && book.title.isBlank()) {
                    logger.debug { "Skipping book ${book.id}: no ISBN or title" }
                    continue
                }

                logger.info { "Enriching [${enriched + failed + 1}/${booksToEnrich.size}] \"${book.title}\"" }
                val metadata = fetchMetadataService.fetchMetadata(
                    MetadataRequestDto(
                        isbn = isbn,
                        title = book.title,
                        authors = authorsString,
                    ),
                )

                val updates = mutableListOf<String>()

                if (book.pageCount == null && metadata.pageCount != null && metadata.pageCount!! > 0) {
                    book.pageCount = metadata.pageCount
                    updates.add("page count (${metadata.pageCount})")
                }
                if (book.summary.isNullOrBlank() && !metadata.summary.isNullOrBlank()) {
                    book.summary = metadata.summary
                    updates.add("summary")
                }
                if (book.publisher.isNullOrBlank() && !metadata.publisher.isNullOrBlank()) {
                    book.publisher = metadata.publisher
                    updates.add("publisher")
                }
                if (book.language.isNullOrBlank() && !metadata.language.isNullOrBlank()) {
                    book.language = metadata.language
                    updates.add("language")
                }

                if (updates.isNotEmpty()) {
                    enriched++
                    logger.info { "Enriched \"${book.title}\": ${updates.joinToString(", ")}" }
                }
            } catch (e: Exception) {
                failed++
                logger.error(e) { "Failed to enrich \"${book.title}\"" }
            }
        }

        logger.info { "Bulk enrichment complete: $enriched enriched, $failed failed out of ${booksToEnrich.size}" }

        userMessageService.save(
            CreateUserMessageDto(
                message = "Bulk enrichment complete: $enriched books enriched, $failed failed out of ${booksToEnrich.size} total.",
                link = null,
                category = MessageCategory.SUCCESS,
            ),
            user,
        )
    }
}
