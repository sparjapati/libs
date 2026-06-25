package com.sparjapati.indexing.core

import kotlin.reflect.KClass

interface IndexConverter<D : Any, I : AbstractEntityIndex> {

    val entityClass: KClass<D>

    fun convert(source: D): I

    fun convertAll(source: List<D>): List<I> = source.map(::convert)
}
