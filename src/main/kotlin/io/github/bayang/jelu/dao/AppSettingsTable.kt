package io.github.bayang.jelu.dao

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.UUID

object AppSettingsTable : UUIDTable("app_settings") {
    val settingKey = varchar("setting_key", 100).uniqueIndex()
    val settingValue = text("setting_value").nullable()
    val creationDate = timestampWithTimeZone("creation_date")
    val modificationDate = timestampWithTimeZone("modification_date")
}

class AppSettings(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AppSettings>(AppSettingsTable)

    var settingKey by AppSettingsTable.settingKey
    var settingValue by AppSettingsTable.settingValue
    var creationDate by AppSettingsTable.creationDate
    var modificationDate by AppSettingsTable.modificationDate
}
