package com.dbStore.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.dbStore.annotation.DbStoreCacheEvict
import com.dbStore.annotation.DbStoreCachePut
import com.dbStore.annotation.DbStoreCacheable
import com.dbStore.annotation.EnableDbStoreCaching
import com.dbStore.aspect.DbStoreCacheEvictAspect
import com.dbStore.aspect.DbStoreCachePutAspect
import com.dbStore.aspect.DbStoreCacheableAspect
import com.dbStore.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.dbStore.mysqlDbstore.service.MysqlDbStoreCacheService
import com.dbStore.service.DbStoreCacheSupport
import com.dbStore.service.DbStoreService
import jakarta.persistence.EntityManagerFactory
import org.springframework.aop.aspectj.AspectJExpressionPointcut
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.ImportAware
import org.springframework.core.type.AnnotationMetadata
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.SharedEntityManagerCreator
import kotlin.reflect.KClass

// Loaded exclusively via @Import from @EnableDbStoreCaching — no @ConditionalOnBean needed.
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class DbStoreCachingConfiguration : ImportAware {

    private var basePackages: Array<String> = emptyArray()

    override fun setImportMetadata(importMetadata: AnnotationMetadata) {
        val attrs = importMetadata.getAnnotationAttributes(EnableDbStoreCaching::class.java.name)
        basePackages = when (val raw = attrs?.get("basePackages")) {
            is Array<*> -> raw.filterIsInstance<String>().toTypedArray()
            else        -> emptyArray()
        }
    }

    @Bean
    fun mysqlDbStoreCacheRepository(entityManagerFactory: EntityManagerFactory): MysqlDbStoreCacheRepository =
        // EntityManager is not a resolvable singleton bean in Spring Boot 4; use EntityManagerFactory.
        JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
            .getRepository(MysqlDbStoreCacheRepository::class.java)

    @Bean
    fun dbStoreService(
        repository: MysqlDbStoreCacheRepository,
        objectMapper: ObjectMapper,
    ): DbStoreService = MysqlDbStoreCacheService(repository, objectMapper)

    @Bean
    fun dbStoreCacheSupport(
        dbStoreService: DbStoreService,
        objectMapper: ObjectMapper,
    ) = DbStoreCacheSupport(dbStoreService, objectMapper)

    @Bean
    fun dbStoreCacheableAdvisor(cacheSupport: DbStoreCacheSupport): DefaultPointcutAdvisor =
        DefaultPointcutAdvisor(buildPointcut(DbStoreCacheable::class), DbStoreCacheableAspect(cacheSupport))

    @Bean
    fun dbStoreCacheEvictAdvisor(cacheSupport: DbStoreCacheSupport): DefaultPointcutAdvisor =
        DefaultPointcutAdvisor(buildPointcut(DbStoreCacheEvict::class), DbStoreCacheEvictAspect(cacheSupport))

    @Bean
    fun dbStoreCachePutAdvisor(cacheSupport: DbStoreCacheSupport): DefaultPointcutAdvisor =
        DefaultPointcutAdvisor(buildPointcut(DbStoreCachePut::class), DbStoreCachePutAspect(cacheSupport))

    // Annotation-driven pointcut: fires only on methods annotated with the given annotation.
    // When basePackages is set the pointcut is further narrowed to within(pkg..*) so beans
    // outside the application's own packages are never considered.
    private fun buildPointcut(annotationClass: KClass<out Annotation>): AspectJExpressionPointcut {
        val annotationExpr = "@annotation(${annotationClass.java.name})"
        val expression = if (basePackages.isNotEmpty()) {
            "(${basePackages.joinToString(" || ") { "within($it..*)" }}) && $annotationExpr"
        } else {
            annotationExpr
        }
        return AspectJExpressionPointcut().apply { this.expression = expression }
    }
}
