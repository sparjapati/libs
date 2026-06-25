package com.dbStore.annotation

import com.dbStore.config.DbStoreCachingConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(DbStoreCachingConfiguration::class)
annotation class EnableDbStoreCaching