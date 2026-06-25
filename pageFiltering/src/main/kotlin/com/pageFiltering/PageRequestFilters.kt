package com.pageFiltering

import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PageRequestFilters(
    val filterResourceKey: String,
    val filterClass: KClass<out Enum<*>>,
    val sortParamClass: KClass<out Enum<*>>,
    val searchClass: KClass<out Enum<*>>,
    val defaultSortParam: String,
    val defaultPageSize: Int = 20,
    val defaultSortDirection: String = "ASC",
    val queryParamName: String = "query",
    val filterQueryParamName: String = "filter",
    val sortQueryParamName: String = "sort",
    val pageNumberQueryParam: String = "pageNumber",
    val pageSizeQueryParam: String = "pageSize"
)
