package com.indexing.annotation

import com.indexing.config.EntityIndexingConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(EntityIndexingConfiguration::class)
annotation class EnableEntityIndexing
