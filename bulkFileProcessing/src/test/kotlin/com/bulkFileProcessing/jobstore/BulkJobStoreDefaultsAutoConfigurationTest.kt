package com.bulkFileProcessing.jobstore

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BulkJobStoreDefaultsAutoConfigurationTest {

    private val config = BulkJobStoreDefaultsAutoConfiguration()

    @Test
    fun `noOpBulkJobStore bean is a NoOpBulkJobStore`() {
        assertTrue(config.noOpBulkJobStore() is NoOpBulkJobStore)
    }

    @Test
    fun `inMemoryBulkJobStore bean is an InMemoryBulkJobStore`() {
        assertTrue(config.inMemoryBulkJobStore() is InMemoryBulkJobStore)
    }
}
