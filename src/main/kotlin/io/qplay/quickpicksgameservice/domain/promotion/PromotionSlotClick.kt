package io.qplay.quickpicksgameservice.domain.promotion

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "promotion_slot_clicks")
class PromotionSlotClick(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "slot_id", nullable = false)
    val slotId: UUID,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "round_id")
    val roundId: UUID? = null,

    @Column(name = "host_player_id", nullable = false)
    val hostPlayerId: String,

    @Column(name = "clicked_at", nullable = false)
    val clickedAt: OffsetDateTime = OffsetDateTime.now()
)

data class ClicksByRound(val roundId: UUID?, val clickCount: Long)
data class TopClicker(val hostPlayerId: String, val clickCount: Long)

@Repository
interface PromotionSlotClickRepository : JpaRepository<PromotionSlotClick, UUID> {

    fun countBySlotId(slotId: UUID): Long
    fun countByRoundId(roundId: UUID): Long

    @Query(
        "SELECT new io.qplay.quickpicksgameservice.domain.promotion.ClicksByRound(c.roundId, COUNT(c)) " +
        "FROM PromotionSlotClick c WHERE c.slotId = :slotId GROUP BY c.roundId ORDER BY COUNT(c) DESC"
    )
    fun clicksByRound(slotId: UUID): List<ClicksByRound>

    @Query(
        "SELECT new io.qplay.quickpicksgameservice.domain.promotion.TopClicker(c.hostPlayerId, COUNT(c)) " +
        "FROM PromotionSlotClick c WHERE c.slotId = :slotId GROUP BY c.hostPlayerId ORDER BY COUNT(c) DESC"
    )
    fun topClickingPlayers(slotId: UUID): List<TopClicker>
}
