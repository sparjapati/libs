package com.indexing.annotation

import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReindexId(val entity: KClass<*>)
