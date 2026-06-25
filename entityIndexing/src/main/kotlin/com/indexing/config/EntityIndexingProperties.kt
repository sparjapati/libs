package com.indexing.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "indexing")
data class EntityIndexingProperties(
    val chunkSize: Int = 50,
)
