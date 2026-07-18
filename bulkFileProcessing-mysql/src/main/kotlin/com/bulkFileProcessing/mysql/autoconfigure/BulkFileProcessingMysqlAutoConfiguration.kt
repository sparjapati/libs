package com.bulkFileProcessing.mysql.autoconfigure

import com.bulkFileProcessing.jobstore.BulkJobStore
import com.bulkFileProcessing.mysql.MysqlBulkJobStore
import com.bulkFileProcessing.mysql.repository.BulkJobRecordJpaRepository
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

// @ConditionalOnMissingBean is type-scoped: BulkJobStore has no common ambient Spring bean of
// that type to collide with.
//
// @AutoConfigureAfter the host app's own default JPA repository scan (Boot 4:
// DataJpaRepositoriesAutoConfiguration) is required: that auto-configuration is itself
// @ConditionalOnMissingBean(JpaRepositoryFactoryBean.class) — if THIS class's own
// @EnableJpaRepositories registers its repository first, the host's own repositories never get
// auto-discovered. Referenced by name since spring-boot-data-jpa isn't a compile dependency of
// this module — only the host app pulls it in transitively.
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = ["org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"])
@EnableJpaRepositories(basePackages = ["com.bulkFileProcessing.mysql.repository"])
@EntityScan(basePackages = ["com.bulkFileProcessing.mysql.entity"])
class BulkFileProcessingMysqlAutoConfiguration {

    @Bean(BulkFileProcessingMysqlBeanNames.JOB_STORE)
    @ConditionalOnMissingBean(BulkJobStore::class)
    fun mysqlBulkJobStore(repository: BulkJobRecordJpaRepository): BulkJobStore =
        MysqlBulkJobStore(repository)
}
