package io.github.bayang.jelu.service

import io.github.bayang.jelu.dao.AppSettings
import io.github.bayang.jelu.dao.AppSettingsTable
import io.github.bayang.jelu.utils.nowDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppSettingsService {
    @Transactional
    fun getAll(): Map<String, String?> = AppSettings.all().associate { it.settingKey to it.settingValue }

    @Transactional
    fun getRaw(key: String): String? =
        AppSettings
            .find { AppSettingsTable.settingKey eq key }
            .firstOrNull()
            ?.settingValue

    @Transactional
    fun setRaw(
        key: String,
        value: String?,
    ) {
        val now = nowDateTime()
        val existing = AppSettings.find { AppSettingsTable.settingKey eq key }.firstOrNull()
        if (existing != null) {
            existing.settingValue = value
            existing.modificationDate = now
        } else {
            AppSettings.new {
                this.settingKey = key
                this.settingValue = value
                this.creationDate = now
                this.modificationDate = now
            }
        }
    }

    @Transactional
    fun setMany(values: Map<String, String?>) {
        values.forEach { (k, v) -> setRaw(k, v) }
    }

    @Transactional
    fun delete(key: String) {
        AppSettings.find { AppSettingsTable.settingKey eq key }.forEach { it.delete() }
    }

    // Typed convenience getters with defaults (default = the previously-hardcoded value).
    // These must be @Transactional: they call getRaw() as a self-invocation, so Spring's
    // proxy is bypassed and no transaction would otherwise be opened for the Exposed read.
    @Transactional
    fun getString(
        key: String,
        default: String,
    ): String = getRaw(key) ?: default

    @Transactional
    fun getInt(
        key: String,
        default: Int,
    ): Int = getRaw(key)?.toIntOrNull() ?: default

    @Transactional
    fun getBool(
        key: String,
        default: Boolean,
    ): Boolean = getRaw(key)?.toBooleanStrictOrNull() ?: default
}
