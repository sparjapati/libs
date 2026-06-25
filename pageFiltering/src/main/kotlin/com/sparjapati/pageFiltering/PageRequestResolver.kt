package com.sparjapati.pageFiltering

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class PageRequestResolver : HandlerMethodArgumentResolver {
    companion object {
        private const val KEY_VALUE_SEPARATOR: String = ":"
        private const val MULTI_VALUED_FILTER_SEPARATOR = ","

        private val searchableFieldsCache = ConcurrentHashMap<KClass<*>, Set<SearchableField>>()
        private val sortConstantsCache = ConcurrentHashMap<Class<*>, List<SortParamKey>>()
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.getParameterType() == PageRequestParams::class.java
                && parameter.hasParameterAnnotation(PageRequestFilters::class.java)
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val annotation = parameter.getParameterAnnotation(PageRequestFilters::class.java)
        checkNotNull(annotation) { "No PageRequestFilters annotation found for parameter " + parameter.getParameterName() }

        val query = webRequest.getParameter(annotation.queryParamName)
        val searchableFields = extractSearchableFields(annotation.searchClass)

        val filterStrings = webRequest.getParameterValues(annotation.filterQueryParamName)?.toSet() ?: setOf()
        val filters = parseFilters(filterStrings = filterStrings, enumClass = annotation.filterClass.java)

        val page = webRequest.getParameter(annotation.pageNumberQueryParam)
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0
        val size = webRequest.getParameter(annotation.pageSizeQueryParam)
            ?.toIntOrNull()
            ?.coerceIn(1, 100)
            ?: annotation.defaultPageSize

        val sortEnumClass = annotation.sortParamClass.java
        val sortParameterValues = webRequest.getParameterValues(annotation.sortQueryParamName)
            ?.takeIf { it.isNotEmpty() }
            ?.toSet()
            ?: setOf("${annotation.defaultSortParam}:${annotation.defaultSortDirection}")
        val sortOrders = parseSort(
            sortEnumClass = sortEnumClass,
            sortStrings = sortParameterValues
        )

        return PageRequestParams(
            query = query,
            filters = filters,
            sort = sortOrders,
            page = page,
            size = size,
            searchableFields = searchableFields,
        )
    }

    private fun <F> parseFilters(
        filterStrings: Set<String>,
        enumClass: Class<F>,
    ): Set<Filter> where F : Enum<*> {
        if (filterStrings.isEmpty()) {
            return setOf()
        }

        val constants = enumClass.getEnumConstants()
        require(constants.isNotEmpty()) {
            "Filter enum must not be empty"
        }
        val lookup = constants.filterIsInstance<FilterKey>().associateBy { it.filterQueryParamName }

        return buildSet {
            for (input in filterStrings) {
                val parts = input.split(KEY_VALUE_SEPARATOR.toRegex(), limit = 2).toTypedArray()
                val key = parts[0]
                if (parts.size < 2)
                    error("Blank filter value for key $key")
                val rawValue = parts[1]

                val filterKey = lookup[key]
                requireNotNull(filterKey) { "No filter key $key found in filters ${enumClass.name}" }

                val filter = createFilter(filterKey = filterKey, rawValue = rawValue.trim())
                add(filter)
            }
        }
    }

    private fun <S> parseSort(sortEnumClass: Class<S>, sortStrings: Set<String>): Set<SortOrder> where S : Enum<*> {
        if (sortStrings.isEmpty()) return emptySet()
        val constants = sortConstantsCache.getOrPut(sortEnumClass) {
            sortEnumClass.getEnumConstants().map { it as SortParamKey }
        }
        require(constants.isNotEmpty()) {
            "SortParam enum ${sortEnumClass.name} must not be empty"
        }
        return buildSet {
            for (s in sortStrings) {
                val parts = s.split(KEY_VALUE_SEPARATOR.toRegex(), limit = 2).toTypedArray()
                val field = parts[0]
                val sortEnumValue = constants.find { c -> c.queryParamName == field }
                requireNotNull(sortEnumValue) { "No sortParamKey $field found in sortParams ${sortEnumClass.name}" }

                val order = if (parts.size > 1 && parts[1].startsWith("d", ignoreCase = true))
                    SortOrder.Desc(paramName = field, fieldKey = sortEnumValue.field)
                else
                    SortOrder.Asc(paramName = field, fieldKey = sortEnumValue.field)

                add(order)
            }
        }
    }

    private fun extractSearchableFields(
        searchClass: KClass<out Enum<*>>,
    ): Set<SearchableField> = searchableFieldsCache.getOrPut(searchClass) {
        val constants = searchClass.java.enumConstants ?: error("${searchClass.simpleName} is not an enum")
        require(constants.all { it is SearchableField }) {
            "Search enum ${searchClass.simpleName} must implement SearchableField"
        }
        constants.map { it as SearchableField }.toSet()
    }

    private fun createFilter(filterKey: FilterKey, rawValue: String): Filter {
        val filterValue = try {
            when (filterKey.filterValueType) {
                FilterValue.Single::class -> FilterValue.Single(value = parseValue(value = rawValue, type = filterKey.dataType))
                FilterValue.Multi::class -> FilterValue.Multi(values = parseMulti(raw = rawValue, type = filterKey.dataType))
                FilterValue.Range::class -> parseRange(raw = rawValue, type = filterKey.dataType)
                else -> error("Unknown filter value type: ${filterKey.filterValueType}")
            }
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid ${filterKey.field} value '$rawValue' for type ${filterKey.filterValueType.simpleName}")
        }

        filterKey.enumClass?.let { enumKClass ->
            val validNames = enumKClass.java.enumConstants.mapTo(HashSet()) { it.name }
            val valuesToCheck: List<String> = when (filterValue) {
                is FilterValue.Single<*> -> (filterValue.value as? String)?.let { listOf(it) } ?: emptyList()
                is FilterValue.Multi<*> -> filterValue.values.filterIsInstance<String>()
                is FilterValue.Range<*> -> listOfNotNull(filterValue.from as? String, filterValue.to as? String)
            }
            val invalid = valuesToCheck.firstOrNull { it !in validNames }
            require(invalid == null) {
                "Invalid value '$invalid' for filter '${filterKey.filterQueryParamName}'. Accepted values: ${validNames.sorted().joinToString()}"
            }
        }

        return Filter(
            filterKey = filterKey.field,
            filterValue = filterValue
        )
    }

    private fun parseValue(value: String, type: KClass<*>): Any {
        return when (type) {
            Long::class -> value.toLong()
            String::class -> value
            Int::class -> value.toInt()
            Double::class -> value.toDouble()
            Boolean::class -> value.toBoolean()
            java.util.Date::class -> java.util.Date(value.toLong())
            else -> error("Unsupported filter data type: $type")
        }
    }

    private fun parseMulti(raw: String, type: KClass<*>): List<Any> {
        return raw.split(MULTI_VALUED_FILTER_SEPARATOR.toRegex())
            .dropLastWhile { it.isEmpty() }
            .map { v -> parseValue(value = v.trim(), type = type) }
    }

    private fun parseRange(raw: String, type: KClass<*>): FilterValue.Range<Any> {
        val parts = raw.split(MULTI_VALUED_FILTER_SEPARATOR.toRegex(), limit = 2)
            .dropLastWhile { it.isEmpty() }
        require(parts.size == 2) { "Range filter requires exactly two comma-separated values, got: '$raw'" }
        return FilterValue.Range(
            from = parseValue(value = parts[0].trim(), type = type),
            to = parseValue(value = parts[1].trim(), type = type),
        )
    }
}
