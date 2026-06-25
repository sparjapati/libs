package com.dbStore.annotation

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class DbStoreCachePut(
    val cacheName: String = "",
    val key: String = "",
    val ttlSeconds: Long = -1L
)
