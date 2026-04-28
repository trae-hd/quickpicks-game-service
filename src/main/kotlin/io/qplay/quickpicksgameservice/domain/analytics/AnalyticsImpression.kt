package io.qplay.quickpicksgameservice.domain.analytics

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Entity
@Table(name = "analytics_impressions")
class AnalyticsImpression(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "round_id", nullable = false)
    val roundId: UUID,

    @Column(name = "host_player_id", nullable = false)
    val hostPlayerId: String,

    @Column(name = "impression_type", nullable = false)
    val impressionType: String = "POST_ENTRY_RECEIPT",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Repository
interface AnalyticsImpressionRepository : JpaRepository<AnalyticsImpression, UUID> {
    fun countByRoundId(roundId: UUID): Long

    @Query("SELECT COUNT(DISTINCT ai.hostPlayerId) FROM AnalyticsImpression ai WHERE ai.roundId = :roundId")
    fun countUniquePlayersByRoundId(roundId: UUID): Long
}
