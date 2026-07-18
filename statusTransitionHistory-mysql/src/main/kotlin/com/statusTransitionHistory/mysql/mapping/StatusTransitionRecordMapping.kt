package com.statusTransitionHistory.mysql.mapping

import com.statusTransitionHistory.history.StatusTransitionRecord
import com.statusTransitionHistory.mysql.entity.StatusTransitionRecordEntity

fun StatusTransitionRecordEntity.toDto(): StatusTransitionRecord = StatusTransitionRecord(
    id = id,
    entity = entity,
    entityId = entityId,
    fromStatus = fromStatus,
    toStatus = toStatus,
    comment = comment,
    transitionedAt = transitionedAt,
)

fun StatusTransitionRecord.toEntity(): StatusTransitionRecordEntity = StatusTransitionRecordEntity(
    entity = entity,
    entityId = entityId,
    fromStatus = fromStatus,
    toStatus = toStatus,
    comment = comment,
    transitionedAt = transitionedAt,
)
