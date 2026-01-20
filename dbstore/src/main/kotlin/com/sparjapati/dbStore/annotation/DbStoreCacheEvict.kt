package com.sparjapati.dbStore.annotation

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class DbStoreCacheEvict(
    val cacheName: String = "",
    val key: String = "",
)
