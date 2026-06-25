package com.pageFiltering

import kotlin.reflect.KClass

typealias SortParamName = String

interface FilterKey {
    val filterQueryParamName: String
    val field: String
    val dataType: KClass<*>
    val filterValueType: KClass<out FilterValue<*>>
    /** Enum whose constants are the only accepted values for this filter. Null means unrestricted. */
    val enumClass: KClass<out Enum<*>>? get() = null
}

interface SortParamKey {
    val queryParamName: String
    val field: String
}

interface SearchableField {
    val field: String
    val type: KClass<*>
}
