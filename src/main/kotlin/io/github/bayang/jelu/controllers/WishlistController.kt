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
    ): WishlistItemDto = wishlistService.create(dto, (principal.principal as JeluUser).user)

    @DeleteMapping("/wishlist/{id}")
    fun delete(
        principal: Authentication,
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        val userId = (principal.principal as JeluUser).user.id!!
        wishlistService.delete(id, userId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/wishlist/{id}")
    fun update(
        @org.springframework.web.bind.annotation.PathVariable id: java.util.UUID,
        @org.springframework.web.bind.annotation.RequestBody dto: io.github.bayang.jelu.dto.CreateWishlistItemDto,
        principal: org.springframework.security.core.Authentication,
    ): io.github.bayang.jelu.dto.WishlistItemDto {
        io.github.bayang.jelu.dto
            .assertIsJeluUser(principal.principal)
        val user = principal.principal as io.github.bayang.jelu.dto.JeluUser
        return wishlistService.update(id, dto, user.user.id!!)
    }

    @GetMapping("/wishlist/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): WishlistItemDto = wishlistService.findById(id)
}
