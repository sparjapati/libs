package com.pageFiltering

data class PageRequestParams(
    val query: String? = null,
    val filters: Set<Filter> = setOf(),
    val sort: Set<SortOrder>,
    val page: Int,
    val size: Int,
    val searchableFields: Set<SearchableField> = emptySet()
)
