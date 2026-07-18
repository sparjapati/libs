package com.statusTransitionHistory.mysql

import com.statusTransitionHistory.history.StatusTransitionRecord
import com.statusTransitionHistory.history.StatusTransitionStore
import com.statusTransitionHistory.mysql.mapping.toDto
import com.statusTransitionHistory.mysql.mapping.toEntity
import com.statusTransitionHistory.mysql.repository.StatusTransitionRecordJpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional

open class MysqlStatusTransitionStore(
    private val repository: StatusTransitionRecordJpaRepository,
) : StatusTransitionStore {

    @Transactional
    override fun record(
        entity: String,
        entityId: String,
        fromStatus: String,
        toStatus: String,
        comment: String?,
    ): StatusTransitionRecord {
        val pending = StatusTransitionRecord.forTransition(entity, entityId, fromStatus, toStatus, comment)
        return repository.save(pending.toEntity()).toDto()
    }

    @Transactional(readOnly = true)
    override fun findAll(entity: String, entityId: String, pageable: Pageable): Page<StatusTransitionRecord> =
        repository.findAllFiltered(entity, entityId, pageable).map { it.toDto() }
}
