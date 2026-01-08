package com.sparjapati.annotation

import com.sparjapati.config.EntityValidationConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(EntityValidationConfiguration::class)
annotation class EnableEntityValidation
