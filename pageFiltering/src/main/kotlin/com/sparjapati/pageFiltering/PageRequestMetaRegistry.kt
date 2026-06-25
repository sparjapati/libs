package com.sparjapati.pageFiltering

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.math.BigDecimal
import java.util.Date
import kotlin.reflect.KClass

/**
 * Scans all registered Spring MVC handler methods at startup, finds every method parameter
 * annotated with [PageRequestFilters], and indexes the resulting [PageRequestMetaDto] by
 * [PageRequestFilters.filterResourceKey].
 *
 * Multiple handler methods on the same resource key are allowed; the last one registered wins.
 */
class PageRequestMetaRegistry(
    private val handlerMappings: List<RequestMappingHandlerMapping>,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PageRequestMetaRegistry::class.java)
    }

    private var registry: Map<String, PageRequestMetaDto> = emptyMap()

    @EventListener(ApplicationReadyEvent::class)
    fun init() {
        registry = buildMap {
            handlerMappings
                .flatMap { it.handlerMethods.entries }
                .forEach { (_, handlerMethod) ->
                    val annotation = handlerMethod.methodParameters
                        .mapNotNull { param -> param.getParameterAnnotation(PageRequestFilters::class.java) }
                        .firstOrNull() ?: return@forEach

                    val resourceKey = annotation.filterResourceKey.takeIf { it.isNotBlank() } ?: return@forEach
                    put(resourceKey, annotation.toMeta())
                    LOGGER.debug("Registered page meta for resource '{}'", resourceKey)
                }
        }
        LOGGER.info("PageRequestMetaRegistry initialized with {} resource(s): {}", registry.size, registry.keys)
    }

    fun getMeta(resource: String): PageRequestMetaDto? = registry[resource]

    fun getAll(): Map<String, PageRequestMetaDto> = registry

    private fun PageRequestFilters.toMeta(): PageRequestMetaDto {
        val filters = filterClass.java.enumConstants
            .filterIsInstance<FilterKey>()
            .map { key ->
                FilterMetaDto(
                    paramName = key.filterQueryParamName,
                    dataType = key.dataType.toDataTypeName(),
                    allowedValues = key.enumClass?.java?.enumConstants?.map { it.name },
                )
            }

        val sortParams = sortParamClass.java.enumConstants
            .filterIsInstance<SortParamKey>()
            .map { key -> key.queryParamName }

        val searchable = searchClass.java.enumConstants.isNotEmpty()

        return PageRequestMetaDto(
            filters = filters,
            sortParams = sortParams,
            searchable = searchable,
            queryParamName = queryParamName,
            filterQueryParamName = filterQueryParamName,
            sortQueryParamName = sortQueryParamName,
            pageNumberQueryParam = pageNumberQueryParam,
            pageSizeQueryParam = pageSizeQueryParam,
        )
    }

    private fun KClass<*>.toDataTypeName(): String = when (this) {
        String::class -> "string"
        Int::class -> "int"
        Long::class -> "long"
        Boolean::class -> "boolean"
        Double::class -> "double"
        Float::class -> "float"
        BigDecimal::class -> "decimal"
        Date::class -> "date"
        else -> simpleName?.lowercase() ?: "unknown"
    }
}
