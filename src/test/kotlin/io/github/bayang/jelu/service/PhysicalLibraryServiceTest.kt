package io.github.bayang.jelu.service

import io.github.bayang.jelu.dto.BookCreateDto
import io.github.bayang.jelu.dto.CreatePhysicalBookcaseDto
import io.github.bayang.jelu.dto.CreatePhysicalLocationDto
import io.github.bayang.jelu.dto.CreateUserBookDto
import io.github.bayang.jelu.dto.CreateUserDto
import io.github.bayang.jelu.dto.AssignBookToShelfDto
import io.github.bayang.jelu.dto.BulkAssignBooksToShelfDto
import io.github.bayang.jelu.dto.UpdatePhysicalLocationDto
import io.github.bayang.jelu.dto.UpdatePhysicalBookcaseDto
import io.github.bayang.jelu.dto.UpdatePhysicalShelfDto
import io.github.bayang.jelu.dto.UserDto
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
class PhysicalLibraryServiceTest(
    @Autowired private val physicalLibraryService: PhysicalLibraryService,
    @Autowired private val bookService: BookService,
    @Autowired private val userService: UserService,
) {

    private lateinit var testUser: UserDto

    @BeforeEach
    fun setup() {
        physicalLibraryService.findAllLocations().forEach {
            physicalLibraryService.deleteLocation(it.id!!)
        }
        try {
            testUser = userService.findByLogin("testphyslib")
        } catch (e: Exception) {
            testUser = userService.save(CreateUserDto(login = "testphyslib", password = "1234", isAdmin = false))
        }
    }

    @Test
    @Transactional
    fun testCreateAndFindLocation() {
        val created = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Living Room"))
        Assertions.assertNotNull(created.id)
        Assertions.assertEquals("Living Room", created.name)
        Assertions.assertNotNull(created.creationDate)

        val found = physicalLibraryService.findLocationById(created.id!!)
        Assertions.assertEquals("Living Room", found.name)

        val all = physicalLibraryService.findAllLocations()
        Assertions.assertTrue(all.any { it.id == created.id })
    }

    @Test
    @Transactional
    fun testUpdateLocation() {
        val created = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Office"))
        val updated = physicalLibraryService.updateLocation(created.id!!, UpdatePhysicalLocationDto("Home Office"))
        Assertions.assertEquals("Home Office", updated.name)
    }

    @Test
    @Transactional
    fun testDeleteLocation() {
        val created = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Garage"))
        physicalLibraryService.deleteLocation(created.id!!)
        val all = physicalLibraryService.findAllLocations()
        Assertions.assertTrue(all.none { it.id == created.id })
    }

    @Test
    @Transactional
    fun testCreateBookcaseAutoCreatesShelves() {
        val location = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Living Room"))
        val bookcase = physicalLibraryService.createBookcase(
            location.id!!,
            CreatePhysicalBookcaseDto(name = "Tall Bookcase", shelfCount = 5)
        )
        Assertions.assertNotNull(bookcase.id)
        Assertions.assertEquals("Tall Bookcase", bookcase.name)
        Assertions.assertEquals(5, bookcase.shelfCount)
        Assertions.assertNotNull(bookcase.shelves)
        Assertions.assertEquals(5, bookcase.shelves!!.size)
        Assertions.assertEquals(listOf(1, 2, 3, 4, 5), bookcase.shelves!!.map { it.position })
    }

    @Test
    @Transactional
    fun testAssignAndResolveLocation() {
        val location = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Office"))
        val bookcase = physicalLibraryService.createBookcase(
            location.id!!,
            CreatePhysicalBookcaseDto(name = "Bookcase A", shelfCount = 3)
        )
        val shelf = bookcase.shelves!![2]

        val userBook = bookService.save(
            CreateUserBookDto(
                book = BookCreateDto(title = "Located Book"),
                lastReadingEvent = null,
                lastReadingEventDate = null,
                personalNotes = null,
                owned = true,
                toRead = false,
                percentRead = null,
                currentPageNumber = null,
                borrowed = null,
                price = null,
            ),
            testUser,
            null,
        )

        physicalLibraryService.assignBookToShelf(shelf.id!!, AssignBookToShelfDto(userBookId = userBook.id!!))

        val resolved = physicalLibraryService.resolveLocation(userBook.id!!)
        Assertions.assertNotNull(resolved)
        Assertions.assertEquals("Office", resolved!!.locationName)
        Assertions.assertEquals("Bookcase A", resolved.bookcaseName)
        Assertions.assertEquals(3, resolved.shelfPosition)
        Assertions.assertTrue(resolved.displayString.contains("Office"))
        Assertions.assertTrue(resolved.displayString.contains("Bookcase A"))
    }

    @Test
    @Transactional
    fun testOneBookOneShelfRule() {
        val location = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Home"))
        val bookcase = physicalLibraryService.createBookcase(
            location.id!!,
            CreatePhysicalBookcaseDto(name = "BC", shelfCount = 2)
        )
        val shelf1 = bookcase.shelves!![0]
        val shelf2 = bookcase.shelves!![1]

        val userBook = bookService.save(
            CreateUserBookDto(
                book = BookCreateDto(title = "Movable Book"),
                lastReadingEvent = null,
                lastReadingEventDate = null,
                personalNotes = null,
                owned = true,
                toRead = false,
                percentRead = null,
                currentPageNumber = null,
                borrowed = null,
                price = null,
            ),
            testUser,
            null,
        )

        physicalLibraryService.assignBookToShelf(shelf1.id!!, AssignBookToShelfDto(userBookId = userBook.id!!))
        var booksOnShelf1 = physicalLibraryService.findBooksOnShelf(shelf1.id!!)
        Assertions.assertEquals(1, booksOnShelf1.size)

        physicalLibraryService.assignBookToShelf(shelf2.id!!, AssignBookToShelfDto(userBookId = userBook.id!!))
        booksOnShelf1 = physicalLibraryService.findBooksOnShelf(shelf1.id!!)
        val booksOnShelf2 = physicalLibraryService.findBooksOnShelf(shelf2.id!!)
        Assertions.assertEquals(0, booksOnShelf1.size)
        Assertions.assertEquals(1, booksOnShelf2.size)
    }

    @Test
    @Transactional
    fun testUnassignedBooks() {
        val location = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Room"))
        val bookcase = physicalLibraryService.createBookcase(
            location.id!!,
            CreatePhysicalBookcaseDto(name = "BC", shelfCount = 1)
        )
        val shelf = bookcase.shelves!!.first()

        val assignedBook = bookService.save(
            CreateUserBookDto(
                book = BookCreateDto(title = "Assigned Book"),
                lastReadingEvent = null, lastReadingEventDate = null,
                personalNotes = null, owned = true, toRead = false,
                percentRead = null, currentPageNumber = null, borrowed = null, price = null,
            ),
            testUser, null,
        )

        val unassignedBook = bookService.save(
            CreateUserBookDto(
                book = BookCreateDto(title = "Floating Book"),
                lastReadingEvent = null, lastReadingEventDate = null,
                personalNotes = null, owned = true, toRead = false,
                percentRead = null, currentPageNumber = null, borrowed = null, price = null,
            ),
            testUser, null,
        )

        physicalLibraryService.assignBookToShelf(shelf.id!!, AssignBookToShelfDto(userBookId = assignedBook.id!!))

        val unassigned = physicalLibraryService.findUnassignedBooks(Pageable.ofSize(100))
        Assertions.assertTrue(unassigned.content.any { it.id == unassignedBook.id })
        Assertions.assertTrue(unassigned.content.none { it.id == assignedBook.id })
    }

    @Test
    @Transactional
    fun testDeleteLocationCascades() {
        val location = physicalLibraryService.createLocation(CreatePhysicalLocationDto("Temp Room"))
        val bookcase = physicalLibraryService.createBookcase(
            location.id!!,
            CreatePhysicalBookcaseDto(name = "Temp BC", shelfCount = 2)
        )
        val shelf = bookcase.shelves!!.first()

        val userBook = bookService.save(
            CreateUserBookDto(
                book = BookCreateDto(title = "Cascade Test Book"),
                lastReadingEvent = null, lastReadingEventDate = null,
                personalNotes = null, owned = true, toRead = false,
                percentRead = null, currentPageNumber = null, borrowed = null, price = null,
            ),
            testUser, null,
        )
        physicalLibraryService.assignBookToShelf(shelf.id!!, AssignBookToShelfDto(userBookId = userBook.id!!))

        physicalLibraryService.deleteLocation(location.id!!)

        val resolved = physicalLibraryService.resolveLocation(userBook.id!!)
        Assertions.assertNull(resolved)
    }
}
