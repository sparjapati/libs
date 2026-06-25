package com.pageFiltering

/**
 * Full schema describing how a frontend should build its filter/sort/search UI for a paginated resource.
 *
 * @property filters Filterable fields available on the resource.
 * @property sortParams Sort options available on the resource.
 * @property searchable Whether the resource supports full-text search via [queryParamName].
 * @property queryParamName Query-string key for the search term.
 * @property filterQueryParamName Query-string key for filter values.
 * @property sortQueryParamName Query-string key for sort selection.
 * @property pageNumberQueryParam Query-string key for the requested page number.
 * @property pageSizeQueryParam Query-string key for the requested page size.
 */
data class PageRequestMetaDto(
    val filters: List<FilterMetaDto>,
    val sortParams: List<SortParamName>,
    val searchable: Boolean,
    val queryParamName: String,
    val filterQueryParamName: String,
    val sortQueryParamName: String,
    val pageNumberQueryParam: String,
    val pageSizeQueryParam: String,
)

/**
 * Metadata for a single filterable field.
 *
 * @property paramName Query-string parameter name the frontend should use.
 * @property dataType Primitive type of the filter value: "string", "int", "long", "boolean", "decimal".
 * @property allowedValues Exhaustive list of accepted values when the filter is backed by an enum; null means unrestricted.
 */
data class FilterMetaDto(
    val paramName: String,
    val dataType: String,
    val allowedValues: List<String>?,
)
