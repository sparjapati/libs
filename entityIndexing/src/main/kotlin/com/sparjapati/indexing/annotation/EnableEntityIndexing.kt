package com.sparjapati.indexing.annotation

import com.sparjapati.indexing.config.EntityIndexingConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(EntityIndexingConfiguration::class)
annotation class EnableEntityIndexing
