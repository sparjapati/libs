package com.dbStore.annotation

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class DbStoreCacheEvict(
    val cacheName: String = "",
    val key: String = "",
    /**
     * When true, all cache entries whose keys start with [cacheName] are deleted.
     * Requires [cacheName] to be set. [key] is ignored when this is true.
     */
    val allEntries: Boolean = false,
)
