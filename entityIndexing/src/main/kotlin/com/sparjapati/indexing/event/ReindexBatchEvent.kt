package com.sparjapati.indexing.event

import kotlin.reflect.KClass

data class ReindexBatchEvent(val requests: Map<KClass<*>, Set<String>>)
