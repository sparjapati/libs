package com.bulkFileProcessing.mysql.autoconfigure

import com.bulkFileProcessing.mysql.repository.BulkJobRecordJpaRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class BulkFileProcessingMysqlAutoConfigurationTest {

    private val repository: BulkJobRecordJpaRepository = mockk()
    private val config = BulkFileProcessingMysqlAutoConfiguration()

    @Test
    fun `mysqlBulkJobStore bean is created`() {
        assertNotNull(config.mysqlBulkJobStore(repository))
    }
}
