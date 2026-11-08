package io.github.bayang.jelu.service

import io.github.bayang.jelu.dto.BookCreateDto
import io.github.bayang.jelu.search.LuceneEntity
import io.github.bayang.jelu.search.LuceneHelper
import org.apache.lucene.index.Term
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import javax.sql.DataSource

/**
 * The startup index rebuild reads every book through findAllNoFilters. A single row whose
 * binary(16) id holds fewer than 16 bytes used to make Exposed throw a BufferUnderflowException,
 * taking the whole page — and therefore the whole application — down with it. This proves such a
 * row is now skipped and reported instead.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchIndexServiceTest(
    @Autowired private val searchIndexService: SearchIndexService,
    @Autowired private val luceneHelper: LuceneHelper,
    @Autowired private val bookService: BookService,
    @Autowired private val dataSource: DataSource,
) {
    private val badTitle = "Test Book"
    private val goodTitle = "A well-formed book"

    @AfterAll
    fun cleanUp() {
        // remove the rows this test can leave behind, so the shared database stays usable
        deleteBooksByTitle(badTitle, goodTitle)
        luceneHelper.getIndexWriter().use { indexWriter ->
            indexWriter.deleteDocuments(Term(LuceneEntity.TYPE, LuceneEntity.Book.type))
        }
    }

    @Test
    fun `rebuildIndex skips a book whose id is not a 16 byte UUID`() {
        // a good book, so we can prove healthy rows are still indexed
        val goodBook = bookService.save(BookCreateDto(title = goodTitle), null)

        // the malformed row: the id column holds 8 bytes instead of the 16 a UUID needs.
        // Copy the timestamps from the good row so they use the exact SQLite format Exposed writes.
        val shortId = ByteArray(8) { 0xBB.toByte() }
        dataSource.connection.use { con ->
            val insertSql =
                "INSERT INTO book (id, creation_date, modification_date, title) " +
                    "SELECT ?, creation_date, modification_date, ? FROM book WHERE title = ?"
            val statement = con.prepareStatement(insertSql)
            statement.use { ps ->
                ps.setBytes(1, shortId)
                ps.setString(2, badTitle)
                ps.setString(3, goodTitle)
                Assertions.assertEquals(1, ps.executeUpdate(), "the malformed row should be inserted")
            }
        }

        // used to throw BufferUnderflowException here, crashing startup
        Assertions.assertDoesNotThrow {
            searchIndexService.rebuildIndex(setOf(LuceneEntity.Book))
        }

        // the well-formed book is indexed, the malformed one is skipped
        val goodHits = luceneHelper.searchEntitiesIds(goodTitle, LuceneEntity.Book).orEmpty()
        Assertions.assertEquals(
            listOf(goodBook.id.toString()),
            goodHits,
            "the well-formed book must still be indexed",
        )
        val badHits = luceneHelper.searchEntitiesIds(badTitle, LuceneEntity.Book).orEmpty()
        Assertions.assertTrue(badHits.isEmpty(), "the malformed row must not have been indexed")

        // remove the malformed row now so the shared test database stays clean even if the
        // assertions above are changed later
        dataSource.connection.use { con ->
            con.createStatement().use { st ->
                st.executeUpdate("DELETE FROM book WHERE title = '$badTitle'")
            }
        }
        bookService.deleteBookById(UUID.fromString(goodBook.id.toString()))
    }

    private fun deleteBooksByTitle(vararg titles: String) {
        dataSource.connection.use { con ->
            titles.forEach { title ->
                val statement = con.prepareStatement("DELETE FROM book WHERE title = ?")
                statement.use { ps ->
                    ps.setString(1, title)
                    ps.executeUpdate()
                }
            }
        }
    }
}
