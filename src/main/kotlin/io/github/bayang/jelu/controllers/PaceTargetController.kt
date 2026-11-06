package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.dto.CreatePaceTargetDto
import io.github.bayang.jelu.dto.PaceStatusDto
import io.github.bayang.jelu.dto.PaceTargetDto
import io.github.bayang.jelu.dto.assertIsJeluUser
import io.github.bayang.jelu.service.PaceTargetService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1")
class PaceTargetController(
    private val paceTargetService: PaceTargetService,
) {
    @Operation(description = "set a daily page target for a book")
    @PostMapping(path = ["/userbooks/{userBookId}/pace-target"])
    fun setTarget(
        @PathVariable userBookId: UUID,
        @RequestBody dto: CreatePaceTargetDto,
        principal: Authentication,
    ): PaceTargetDto {
        assertIsJeluUser(principal.principal)
        val req = dto.copy(userBookId = userBookId)
        return paceTargetService.setTarget(req)
    }

    @Operation(description = "get the pace target for a book")
    @GetMapping(path = ["/userbooks/{userBookId}/pace-target"])
    fun getTarget(
        @PathVariable userBookId: UUID,
        principal: Authentication,
    ): ResponseEntity<PaceTargetDto> {
        assertIsJeluUser(principal.principal)
        val target = paceTargetService.getTarget(userBookId)
        return if (target != null) ResponseEntity.ok(target) else ResponseEntity.noContent().build()
    }

    @Operation(description = "get the pace status (ahead/on-pace/behind) for a book")
    @GetMapping(path = ["/userbooks/{userBookId}/pace-status"])
    fun getStatus(
        @PathVariable userBookId: UUID,
        principal: Authentication,
    ): ResponseEntity<PaceStatusDto> {
        assertIsJeluUser(principal.principal)
        val status = paceTargetService.getStatus(userBookId)
        return if (status != null) ResponseEntity.ok(status) else ResponseEntity.noContent().build()
    }

    @Operation(description = "delete the pace target for a book")
    @DeleteMapping(path = ["/userbooks/{userBookId}/pace-target"])
    fun deleteTarget(
        @PathVariable userBookId: UUID,
        principal: Authentication,
    ): ResponseEntity<Void> {
        assertIsJeluUser(principal.principal)
        paceTargetService.deleteTarget(userBookId)
        return ResponseEntity.noContent().build()
    }
}
