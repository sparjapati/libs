package com.statusTransitionHistory.mysql.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity(name = "statusTransitionRecord")
@Table(
    name = "statusTransitionRecord",
    indexes = [
        Index(
            name = "idx_status_transition_record_entity_entity_id_transitioned_at",
            columnList = "entity,entityId,transitionedAt",
        ),
    ],
)
class StatusTransitionRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        private set

    @Column(name = "entity", nullable = false)
    var entity: String = ""
        private set

    @Column(name = "entityId", nullable = false)
    var entityId: String = ""
        private set

    @Column(name = "fromStatus", nullable = false)
    var fromStatus: String = ""
        private set

    @Column(name = "toStatus", nullable = false)
    var toStatus: String = ""
        private set

    @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
    var comment: String = ""
        private set

    @Column(name = "transitionedAt", nullable = false)
    var transitionedAt: Long = 0
        private set

    constructor(
        entity: String,
        entityId: String,
        fromStatus: String,
        toStatus: String,
        comment: String,
        transitionedAt: Long,
    ) {
        this.entity = entity
        this.entityId = entityId
        this.fromStatus = fromStatus
        this.toStatus = toStatus
        this.comment = comment
        this.transitionedAt = transitionedAt
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StatusTransitionRecordEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
