package io.qplay.quickpicksgameservice.domain.draft

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.domain.entry.Pick
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

data class DraftPicksPayload @JsonCreator constructor(
    @JsonProperty("picks")
    val picks: List<Pick> = emptyList()
)

@Entity
@Table(name = "player_drafts")
class PlayerDraft(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "round_id", nullable = false)
    val roundId: UUID,

    @Column(name = "host_player_id", nullable = false)
    val hostPlayerId: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "picks_json", nullable = false, columnDefinition = "jsonb")
    var picks: DraftPicksPayload,

    @Column(name = "tiebreaker")
    var tiebreaker: Int? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
)

@Repository
interface PlayerDraftRepository : JpaRepository<PlayerDraft, UUID> {
    fun findByTenantIdAndRoundIdAndHostPlayerId(tenantId: String, roundId: UUID, hostPlayerId: String): PlayerDraft?

    fun countByTenantIdAndRoundId(tenantId: String, roundId: UUID): Long

    @Transactional
    fun deleteByTenantIdAndRoundIdAndHostPlayerId(tenantId: String, roundId: UUID, hostPlayerId: String)

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PlayerDraft d WHERE d.roundId = :roundId")
    fun deleteAllByRoundId(roundId: UUID): Int
}
