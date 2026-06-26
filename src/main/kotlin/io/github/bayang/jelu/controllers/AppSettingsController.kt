package io.github.bayang.jelu.controllers

import io.github.bayang.jelu.dto.assertIsJeluUser
import io.github.bayang.jelu.service.AppSettingsService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AppSettingsController(
    private val appSettingsService: AppSettingsService,
) {
    @Operation(description = "Get all app settings as key/value map")
    @GetMapping(path = ["/app-settings"])
    fun getAppSettings(principal: Authentication): Map<String, String?> {
        assertIsJeluUser(principal.principal)
        return appSettingsService.getAll()
    }

    @Operation(description = "Update app settings (batch upsert)")
    @PutMapping(path = ["/app-settings"])
    fun updateAppSettings(
        @RequestBody settings: Map<String, String?>,
        principal: Authentication,
    ): Map<String, String?> {
        assertIsJeluUser(principal.principal)
        appSettingsService.setMany(settings)
        return appSettingsService.getAll()
    }
}
