package com.sparjapati.annotation

import com.sparjapati.config.DbStoreCachingConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(DbStoreCachingConfiguration::class)
annotation class EnableDbStoreCaching