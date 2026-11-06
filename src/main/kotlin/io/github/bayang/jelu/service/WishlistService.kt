package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.User
import io.github.bayang.jelu.dao.WishlistItem
import io.github.bayang.jelu.dao.WishlistItemTable
import io.github.bayang.jelu.dto.CreateWishlistItemDto
import io.github.bayang.jelu.dto.UserDto
import io.github.bayang.jelu.dto.WishlistItemDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WishlistService {
    @Transactional
    fun findAll(
        userId: UUID,
        itemType: String? = null,
    ): List<WishlistItemDto> {
        val query = WishlistItemTable.selectAll().andWhere { WishlistItemTable.user eq userId }
        if (itemType != null) {
            query.andWhere { WishlistItemTable.itemType eq itemType }
        }
        query.orderBy(WishlistItemTable.creationDate to SortOrder.DESC)
        return query.map { WishlistItem.wrapRow(it).toDto() }
    }

    @Transactional
    fun create(
        dto: CreateWishlistItemDto,
        user: UserDto,
    ): WishlistItemDto {
        val item =
            WishlistItem.new {
                this.creationDate = Instant.now()
                this.modificationDate = Instant.now()
                this.user = User[user.id!!]
                this.itemType = dto.itemType.name
                this.title = dto.title
                this.description = dto.description
                this.isbn = dto.isbn
                this.authors = dto.authors
                this.image = dto.image
                this.pageCount = dto.pageCount
                this.publisher = dto.publisher
                this.language = dto.language
            }
        return item.toDto()
    }

    @Transactional
    fun delete(
        id: UUID,
        userId: UUID,
    ) {
        val item = WishlistItem.findById(id) ?: throw IllegalArgumentException("Wishlist item not found")
        if (item.user.id.value != userId) throw IllegalArgumentException("Not your wishlist item")
        item.delete()
    }

    @Transactional
    fun update(
        id: UUID,
        dto: CreateWishlistItemDto,
        userId: UUID,
    ): WishlistItemDto {
        val item = WishlistItem.findById(id) ?: throw IllegalArgumentException("Wishlist item not found")
        if (item.user.id.value != userId) throw IllegalArgumentException("Not your wishlist item")
        item.modificationDate = Instant.now()
        if (dto.title != null) item.title = dto.title
        if (dto.description != null) item.description = dto.description
        if (dto.isbn != null) item.isbn = dto.isbn
        if (dto.authors != null) item.authors = dto.authors
        if (dto.image != null) item.image = dto.image
        if (dto.pageCount != null) item.pageCount = dto.pageCount
        if (dto.publisher != null) item.publisher = dto.publisher
        if (dto.language != null) item.language = dto.language
        return item.toDto()
    }

    @Transactional
    fun findById(id: UUID): WishlistItemDto = WishlistItem[id].toDto()
}
