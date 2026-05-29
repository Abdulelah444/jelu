package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.service.BookService
import io.github.bayang.jelu.service.LabelGeneratorService
import io.github.bayang.jelu.service.LabelSize
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class LabelController(
    private val labelService: LabelGeneratorService,
    private val bookService: BookService,
) {

    @GetMapping("/userbooks/{id}/label.png")
    fun getLabel(
        @PathVariable("id") userBookId: UUID,
        @RequestParam(defaultValue = "40") widthMm: Int,
        @RequestParam(defaultValue = "30") heightMm: Int,
        principal: Authentication,
    ): ResponseEntity<ByteArray> {
        val ub = bookService.findUserBookById(userBookId)
        val size = LabelSize(widthMm, heightMm)
        val url = "/public/book/${ub.book.id}"
        val png = labelService.generateLabel(ub.book.title, url, size)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${ub.book.title}.png\"")
            .contentType(MediaType.IMAGE_PNG)
            .body(png)
    }

    @GetMapping("/labels/bulk.zip")
    fun getBulkLabels(
        @RequestParam(defaultValue = "40") widthMm: Int,
        @RequestParam(defaultValue = "30") heightMm: Int,
        principal: Authentication,
    ): ResponseEntity<ByteArray> {
        val size = LabelSize(widthMm, heightMm)
        val userId = (principal.principal as io.github.bayang.jelu.dto.JeluUser).user.id!!
        val page = bookService.findUserBookByCriteria(
            userId = userId,
            bookId = null,
            eventTypes = null,
            toRead = null,
            owned = true,
            pageable = org.springframework.data.domain.PageRequest.of(0, 500),
        )
        val books = page.content.map { ub ->
            ub.book.title to "/public/book/${ub.book.id}"
        }
        val zip = labelService.generateBulkZip(books, size)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"book-labels.zip\"")
            .header(HttpHeaders.CONTENT_TYPE, "application/zip")
            .body(zip)
    }
}
