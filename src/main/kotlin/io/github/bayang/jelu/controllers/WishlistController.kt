package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.dto.CreateWishlistItemDto
import io.github.bayang.jelu.dto.JeluUser
import io.github.bayang.jelu.dto.WishlistItemDto
import io.github.bayang.jelu.service.WishlistService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class WishlistController(
    private val wishlistService: WishlistService,
) {
    @GetMapping("/wishlist")
    fun getAll(
        principal: Authentication,
        @RequestParam(name = "itemType", required = false) itemType: String?,
    ): List<WishlistItemDto> {
        val userId = (principal.principal as JeluUser).user.id!!
        return wishlistService.findAll(userId, itemType)
    }

    @PostMapping("/wishlist")
    fun create(
        principal: Authentication,
        @RequestBody dto: CreateWishlistItemDto,
    ): WishlistItemDto {
        return wishlistService.create(dto, (principal.principal as JeluUser).user)
    }

    @DeleteMapping("/wishlist/{id}")
    fun delete(
        principal: Authentication,
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        val userId = (principal.principal as JeluUser).user.id!!
        wishlistService.delete(id, userId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/wishlist/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): WishlistItemDto {
        return wishlistService.findById(id)
    }
}
