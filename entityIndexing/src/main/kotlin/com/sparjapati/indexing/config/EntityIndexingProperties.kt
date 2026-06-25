package com.sparjapati.indexing.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sparjapati.indexing")
data class EntityIndexingProperties(
    val chunkSize: Int = 50,
)
