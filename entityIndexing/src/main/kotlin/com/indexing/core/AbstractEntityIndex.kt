package com.indexing.core

import java.time.Instant

abstract class AbstractEntityIndex(
    val id: String,
    var lastModified: Instant,
    var lastModifiedBy: String,
    var isDeleted: Boolean = false,
)
