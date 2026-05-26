package io.github.bayang.jelu.dao

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

object WishlistItemTable : UUIDTable("wishlist_item") {
    val creationDate: Column<Instant> = timestamp("creation_date").default(Instant.now())
    val modificationDate: Column<Instant> = timestamp("modification_date").default(Instant.now())
    val user = reference("user", UserTable)
    val itemType: Column<String> = varchar("item_type", 20)
    val title: Column<String?> = varchar("title", 1000).nullable()
    val description: Column<String?> = varchar("description", 5000).nullable()
    val isbn: Column<String?> = varchar("isbn", 30).nullable()
    val authors: Column<String?> = varchar("authors", 1000).nullable()
    val image: Column<String?> = varchar("image", 1000).nullable()
    val pageCount: Column<Int?> = integer("page_count").nullable()
    val publisher: Column<String?> = varchar("publisher", 500).nullable()
    val language: Column<String?> = varchar("language", 50).nullable()
}

class WishlistItem(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<WishlistItem>(WishlistItemTable)
    var creationDate by WishlistItemTable.creationDate
    var modificationDate by WishlistItemTable.modificationDate
    var user by User referencedOn WishlistItemTable.user
    var itemType by WishlistItemTable.itemType
    var title by WishlistItemTable.title
    var description by WishlistItemTable.description
    var isbn by WishlistItemTable.isbn
    var authors by WishlistItemTable.authors
    var image by WishlistItemTable.image
    var pageCount by WishlistItemTable.pageCount
    var publisher by WishlistItemTable.publisher
    var language by WishlistItemTable.language
    fun toDto(): io.github.bayang.jelu.dto.WishlistItemDto = io.github.bayang.jelu.dto.WishlistItemDto(
        id = this.id.value,
        itemType = io.github.bayang.jelu.dto.WishlistItemType.valueOf(this.itemType),
        title = this.title,
        description = this.description,
        isbn = this.isbn,
        authors = this.authors,
        image = this.image,
        pageCount = this.pageCount,
        publisher = this.publisher,
        language = this.language,
        creationDate = this.creationDate,
        modificationDate = this.modificationDate,
    )
}
