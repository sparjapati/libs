package com.statusTransitionHistory.mysql.repository

import com.statusTransitionHistory.mysql.entity.StatusTransitionRecordEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StatusTransitionRecordJpaRepository : JpaRepository<StatusTransitionRecordEntity, Long> {

    @Query(
        """
        select r from statusTransitionRecord r
        where r.entity = :entity and r.entityId = :entityId
        order by r.transitionedAt desc, r.id desc
        """,
    )
    fun findAllFiltered(
        @Param("entity") entity: String,
        @Param("entityId") entityId: String,
        pageable: Pageable,
    ): Page<StatusTransitionRecordEntity>
}
