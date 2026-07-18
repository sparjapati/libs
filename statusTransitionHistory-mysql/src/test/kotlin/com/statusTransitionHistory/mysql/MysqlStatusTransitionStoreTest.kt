package com.statusTransitionHistory.mysql

import com.statusTransitionHistory.mysql.entity.StatusTransitionRecordEntity
import com.statusTransitionHistory.mysql.repository.StatusTransitionRecordJpaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class MysqlStatusTransitionStoreTest {

    private val repository: StatusTransitionRecordJpaRepository = mockk(relaxed = true)
    private val store = MysqlStatusTransitionStore(repository)

    @Test
    fun `record persists the mapped entity with the given comment`() {
        var saved: StatusTransitionRecordEntity? = null
        every { repository.save(any<StatusTransitionRecordEntity>()) } answers { saved = firstArg(); firstArg() }

        store.record(
            entity = "Order",
            entityId = "123",
            fromStatus = "PENDING",
            toStatus = "PAID",
            comment = "manually verified by ops",
        )

        assertEquals("Order", saved?.entity)
        assertEquals("123", saved?.entityId)
        assertEquals("PENDING", saved?.fromStatus)
        assertEquals("PAID", saved?.toStatus)
        assertEquals("manually verified by ops", saved?.comment)
    }

    @Test
    fun `record defaults comment to a generated message when omitted`() {
        var saved: StatusTransitionRecordEntity? = null
        every { repository.save(any<StatusTransitionRecordEntity>()) } answers { saved = firstArg(); firstArg() }

        store.record(entity = "Order", entityId = "123", fromStatus = "PENDING", toStatus = "PAID")

        assertEquals("status updated from PENDING to PAID", saved?.comment)
    }

    @Test
    fun `findAll delegates to the filtered repository query and maps results`() {
        val pageable = PageRequest.of(0, 10)
        every {
            repository.findAllFiltered(entity = "Order", entityId = "123", pageable = pageable)
        } returns PageImpl(listOf(sampleEntity()))

        val page = store.findAll(entity = "Order", entityId = "123", pageable = pageable)

        assertEquals(1L, page.totalElements)
        assertEquals("123", page.content.single().entityId)
        verify { repository.findAllFiltered(entity = "Order", entityId = "123", pageable = pageable) }
    }

    private fun sampleEntity() = StatusTransitionRecordEntity(
        entity = "Order",
        entityId = "123",
        fromStatus = "PENDING",
        toStatus = "PAID",
        comment = "status updated from PENDING to PAID",
        transitionedAt = 1L,
    )
}
