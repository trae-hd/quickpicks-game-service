package io.qplay.quickpicksgameservice.domain.entry

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

enum class PromotionType {
    FIRST_ENTRY_FREE,
    AWARDED_FREE_ENTRY,
}

@Entity
@Table(name = "player_promotions")
class PlayerPromotion(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "player_id", nullable = false)
    val playerId: String,

    @Column(nullable = false)
    val type: String = "FREE_ENTRY",

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_type", nullable = false)
    val promotionType: PromotionType = PromotionType.FIRST_ENTRY_FREE,

    @Column(nullable = false)
    var status: String = "ACTIVE", // ACTIVE, CONSUMED, EXPIRED

    @Column(name = "round_id")
    val roundId: UUID? = null,

    @Column(name = "awarded_by")
    val awardedBy: String? = null,

    @Column(name = "award_reason")
    val awardReason: String? = null,

    @Column(name = "campaign_id")
    val campaignId: String? = null,

    @Column(name = "granted_at", nullable = false)
    val grantedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "consumed_at")
    var consumedAt: OffsetDateTime? = null,

    @Column(name = "expires_at")
    val expiresAt: OffsetDateTime? = null
)

@Repository
interface PlayerPromotionRepository : JpaRepository<PlayerPromotion, UUID> {

    // Legacy query used by FirstEntryFreeService — kept for backward compat
    fun findFirstByTenantIdAndPlayerIdAndTypeAndStatusOrderByGrantedAtAsc(
        tenantId: String, playerId: String, type: String, status: String
    ): PlayerPromotion?

    // FIFO query across all promotion types — oldest first
    fun findFirstByTenantIdAndPlayerIdAndStatusOrderByGrantedAtAsc(
        tenantId: String, playerId: String, status: String
    ): PlayerPromotion?

    fun findByTenantIdAndPlayerId(tenantId: String, playerId: String): List<PlayerPromotion>

    fun findAllByStatusAndExpiresAtBefore(status: String, now: OffsetDateTime): List<PlayerPromotion>

    @Query(
        "SELECT COUNT(p) FROM PlayerPromotion p WHERE p.tenantId = :tenantId AND p.playerId = :playerId " +
        "AND p.status = 'ACTIVE' AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)"
    )
    fun countNonExpiredActiveByTenantIdAndPlayerId(tenantId: String, playerId: String): Long
}
