package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.config.JeluProperties
import io.github.bayang.jelu.dto.assertIsJeluUser
import io.github.bayang.jelu.service.AppSettingsService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.File

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1")
class AppLogoController(
    private val properties: JeluProperties,
    private val appSettingsService: AppSettingsService,
) {
    private val logoFilename = "custom-logo.png"

    @PostMapping(path = ["/app-logo"])
    fun uploadLogo(
        @RequestParam("file") file: MultipartFile,
        principal: Authentication,
    ): Map<String, String> {
        assertIsJeluUser(principal.principal)
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file")
        }
        val dir = File(properties.files.images)
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, logoFilename)
        try {
            file.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save custom logo" }
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save logo")
        }
        appSettingsService.setRaw("custom_logo_enabled", "true")
        logger.info { "Custom logo uploaded to ${dest.absolutePath}" }
        return mapOf("status" to "uploaded", "path" to "/files/$logoFilename")
    }

    @DeleteMapping(path = ["/app-logo"])
    fun resetLogo(principal: Authentication): Map<String, String> {
        assertIsJeluUser(principal.principal)
        val dest = File(File(properties.files.images), logoFilename)
        if (dest.exists()) dest.delete()
        appSettingsService.setRaw("custom_logo_enabled", "false")
        return mapOf("status" to "reset")
    }
}
