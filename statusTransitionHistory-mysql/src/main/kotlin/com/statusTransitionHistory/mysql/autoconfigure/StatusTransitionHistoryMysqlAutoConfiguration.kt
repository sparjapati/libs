package com.statusTransitionHistory.mysql.autoconfigure

import com.statusTransitionHistory.history.StatusTransitionStore
import com.statusTransitionHistory.mysql.MysqlStatusTransitionStore
import com.statusTransitionHistory.mysql.repository.StatusTransitionRecordJpaRepository
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

// @ConditionalOnMissingBean is type-scoped: StatusTransitionStore has no common ambient Spring
// bean of that type to collide with.
//
// @AutoConfigureAfter the host app's own default JPA repository scan (Boot 4:
// DataJpaRepositoriesAutoConfiguration) is required: that auto-configuration is itself
// @ConditionalOnMissingBean(JpaRepositoryFactoryBean.class) — if THIS class's own
// @EnableJpaRepositories registers its repository first, the host's own repositories never get
// auto-discovered. Referenced by name since spring-boot-data-jpa isn't a compile dependency of
// this module — only the host app pulls it in transitively.
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = ["org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"])
@EnableJpaRepositories(basePackages = ["com.statusTransitionHistory.mysql.repository"])
@EntityScan(basePackages = ["com.statusTransitionHistory.mysql.entity"])
class StatusTransitionHistoryMysqlAutoConfiguration {

    @Bean(StatusTransitionHistoryMysqlBeanNames.STORE)
    @ConditionalOnMissingBean(StatusTransitionStore::class)
    fun mysqlStatusTransitionStore(repository: StatusTransitionRecordJpaRepository): StatusTransitionStore =
        MysqlStatusTransitionStore(repository)
}
