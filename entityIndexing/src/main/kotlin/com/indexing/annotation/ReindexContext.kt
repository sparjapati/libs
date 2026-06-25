package com.indexing.annotation

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReindexContext(
    val propagation: ReindexPropagation = ReindexPropagation.REQUIRED,
)
