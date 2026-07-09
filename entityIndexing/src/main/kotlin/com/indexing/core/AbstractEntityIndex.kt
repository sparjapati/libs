package com.indexing.core

abstract class AbstractEntityIndex(
    val id: String,
    var lastModified: Long,
    var lastModifiedBy: String,
    var isDeleted: Boolean = false,
)
