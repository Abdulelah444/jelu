package io.github.bayang.jelu.service.metadata

import io.github.bayang.jelu.dto.MetadataDto
import io.github.bayang.jelu.dto.MetadataRequestDto
import io.github.bayang.jelu.service.metadata.providers.IMetaDataProvider
import io.github.bayang.jelu.utils.PluginInfoComparator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.Optional

private val logger = KotlinLogging.logger {}

@Service
class FetchMetadataService(
    private val providers: List<IMetaDataProvider>,
    private val pluginInfoHolder: PluginInfoHolder,
) {






    fun searchMetadata(
        metadataRequestDto: MetadataRequestDto,
        config: Map<String, String> = mapOf(),
        limit: Int = 20,
        source: String? = null,
    ): List<MetadataDto> {
        val allResults = mutableListOf<MetadataDto>()
        val seenKeys = mutableSetOf<String>()

        fun addResults(results: List<MetadataDto>) {
            for (result in results) {
                val key = result.isbn13 ?: result.isbn10 ?: result.title ?: continue
                if (seenKeys.add(key)) allResults.add(result)
            }
        }

        // If source is specified, only query that provider
        if (!source.isNullOrBlank()) {
            val provider = providers.find { it.name().equals(source, true) }
            if (provider != null) {
                try {
                    addResults(provider.searchMetadata(metadataRequestDto, config))
                } catch (e: Exception) {
                    logger.warn { "provider \${provider.name()} search failed: \${e.message}" }
                }
            }
            return allResults.take(limit)
        }

        // Otherwise fan out to all providers
        var pluginsToUse = if (metadataRequestDto.plugins.isNullOrEmpty()) pluginInfoHolder.plugins() else metadataRequestDto.plugins
        pluginsToUse = pluginsToUse.toMutableList()
        pluginsToUse.sortWith(PluginInfoComparator)
        for (plugin in pluginsToUse) {
            val provider = providers.find { plugin.name.equals(it.name(), true) }
            if (provider != null) {
                try {
                    addResults(provider.searchMetadata(metadataRequestDto, config))
                } catch (e: Exception) {
                    logger.warn { "provider \${provider.name()} search failed: \${e.message}" }
                }
            }
        }
        // Always include openlibrary
        val olProvider = providers.find { it.name().equals("openlibrary", true) }
        if (olProvider != null) {
            try {
                addResults(olProvider.searchMetadata(metadataRequestDto, config))
            } catch (e: Exception) {
                logger.warn { "openlibrary search failed: \${e.message}" }
            }
        }
        return allResults.take(limit)
    }


    fun fetchMetadata(
        metadataRequestDto: MetadataRequestDto,
        config: Map<String, String> = mapOf(),
    ): MetadataDto {
        var pluginsToUse = if (metadataRequestDto.plugins.isNullOrEmpty()) pluginInfoHolder.plugins() else metadataRequestDto.plugins
        pluginsToUse = pluginsToUse.toMutableList()
        // pluginInfoHolder sorts plugins, but plugins received via metadataRequestDto
        // might not be sorted
        pluginsToUse.sortWith(PluginInfoComparator)
        logger.trace { "plugins to use : $pluginsToUse" }
        for (plugin in pluginsToUse) {
            logger.trace { "fetching provider for plugin ${plugin.name} with order ${plugin.order} " }
            val provider = providers.find { plugin.name.equals(it.name(), true) }
            if (provider != null) {
                val res: Optional<MetadataDto>? = provider.fetchMetadata(metadataRequestDto, config)
                if (res != null && res.isPresent) {
                    return res.get()
                }
            } else {
                logger.warn { "could not find provider for plugin info ${plugin.name}" }
            }
        }
        return MetadataDto()
    }
}
