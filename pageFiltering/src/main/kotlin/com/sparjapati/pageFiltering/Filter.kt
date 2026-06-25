package com.sparjapati.pageFiltering

data class Filter(
    val filterKey: String,
    val filterValue: FilterValue<*>
)

sealed class FilterValue<out T> {
    data class Single<T>(val value: T) : FilterValue<T>()
    data class Multi<T>(val values: List<T>) : FilterValue<T>()
    data class Range<T>(val from: T, val to: T) : FilterValue<T>()
}

sealed class SortOrder(open val paramName: String, open val fieldKey: String) {
    data class Asc(override val paramName: String, override val fieldKey: String) : SortOrder(paramName, fieldKey)
    data class Desc(override val paramName: String, override val fieldKey: String) : SortOrder(paramName, fieldKey)
}
