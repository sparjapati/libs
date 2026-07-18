package com.statusTransitionHistory.mysql.autoconfigure

import com.statusTransitionHistory.mysql.repository.StatusTransitionRecordJpaRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class StatusTransitionHistoryMysqlAutoConfigurationTest {

    private val repository: StatusTransitionRecordJpaRepository = mockk()
    private val config = StatusTransitionHistoryMysqlAutoConfiguration()

    @Test
    fun `mysqlStatusTransitionStore bean is created`() {
        assertNotNull(config.mysqlStatusTransitionStore(repository))
    }
}
