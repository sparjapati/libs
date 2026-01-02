package com.sparjapati.annotation

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class DbStoreCacheable(
    val cacheName: String = "",
    val key: String = "",
    val ttlSeconds: Long = -1L
)
