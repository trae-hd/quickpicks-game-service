package io.qplay.quickpicksgameservice.domain.entry

import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface EntryRepository : JpaRepository<Entry, UUID> {
    fun findByRoundId(roundId: UUID): List<Entry>
    fun findByPlayerId(playerId: String): List<Entry>
    fun findByPlayerId(playerId: String, pageable: Pageable): Page<Entry>
    fun findByRoundIdAndPlayerId(roundId: UUID, playerId: String): List<Entry>
    fun findByPlayerIdAndStatusIn(playerId: String, statuses: List<EntryStatus>, pageable: Pageable): Page<Entry>
    fun findByTenantIdAndIdempotencyKey(tenantId: String, idempotencyKey: String): Entry?

    @Query("SELECT e FROM Entry e WHERE e.tenantId = :tenantId AND e.playerId = :playerId AND e.round.status IN :statuses")
    fun findUnsettledByTenantIdAndPlayerId(
        @Param("tenantId") tenantId: String,
        @Param("playerId") playerId: String,
        @Param("statuses") statuses: List<RoundStatus>
    ): List<Entry>
}

interface EntryResultRepository : JpaRepository<EntryResult, UUID> {
    fun findByEntryIdIn(entryIds: List<UUID>): List<EntryResult>

    @Query("SELECT DISTINCT er.entry.round.id FROM EntryResult er WHERE er.tenantId = :tenantId AND er.isJackpotWinner = true")
    fun findRoundIdsWithJackpotWinner(tenantId: String): List<UUID>
}
