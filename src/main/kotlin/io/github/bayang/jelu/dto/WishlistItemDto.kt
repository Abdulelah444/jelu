package io.github.bayang.jelu.dto

import java.time.Instant
import java.util.UUID

enum class WishlistItemType {
    BOOK,
    CONCEPT,
}

data class WishlistItemDto(
    val id: UUID?,
    val itemType: WishlistItemType,
    val title: String?,
    val description: String?,
    val isbn: String?,
    val authors: String?,
    val image: String?,
    val pageCount: Int?,
    val publisher: String?,
    val language: String?,
    val creationDate: Instant?,
    val modificationDate: Instant?,
)

data class CreateWishlistItemDto(
    val itemType: WishlistItemType,
    val title: String?,
    val description: String?,
    val isbn: String?,
    val authors: String?,
    val image: String?,
    val pageCount: Int?,
    val publisher: String?,
    val language: String?,
)
