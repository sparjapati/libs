package com.annotation

import com.config.EntityValidationConfiguration
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(EntityValidationConfiguration::class)
annotation class EnableEntityValidation
