package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.PlayerPromotion
import io.qplay.quickpicksgameservice.domain.entry.PlayerPromotionRepository
import io.qplay.quickpicksgameservice.domain.entry.PromotionType
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import io.qplay.quickpicksgameservice.service.outbox.OutboxService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class FirstEntryFreeService(
    private val promotionRepository: PlayerPromotionRepository,
    private val tenantRepository: TenantRepository,
    private val playerProfileService: PlayerProfileService,
    private val outboxService: OutboxService
) {
    @Transactional(readOnly = true)
    fun getEligiblePromotion(tenantId: String, playerId: String, roundId: UUID): PlayerPromotion? {
        // FIFO: oldest active promotion first, covering both FIRST_ENTRY_FREE and AWARDED_FREE_ENTRY
        return promotionRepository.findFirstByTenantIdAndPlayerIdAndStatusOrderByGrantedAtAsc(
            tenantId, playerId, "ACTIVE"
        )?.takeIf { promo ->
            val expires = promo.expiresAt
            (promo.roundId == null || promo.roundId == roundId) &&
            (expires == null || expires.isAfter(OffsetDateTime.now()))
        }
    }

    @Transactional
    fun consumePromotion(promotion: PlayerPromotion) {
        promotion.status = "CONSUMED"
        promotion.consumedAt = OffsetDateTime.now()
        promotionRepository.save(promotion)
        
        outboxService.trackEvent("free_entry_consumed", mapOf(
            "promotion_id" to promotion.id.toString(),
            "player_id" to promotion.playerId
        ), playerId = promotion.playerId)
    }

    @Transactional
    fun grantPromotion(tenantId: String, playerId: String, roundId: UUID? = null, expiresAt: OffsetDateTime? = null): PlayerPromotion {
        val promo = PlayerPromotion(
            tenantId = tenantId,
            playerId = playerId,
            type = "FREE_ENTRY",
            promotionType = PromotionType.FIRST_ENTRY_FREE,
            roundId = roundId,
            awardedBy = "system",
            expiresAt = expiresAt
        )
        val saved = promotionRepository.save(promo)
        
        outboxService.trackEvent("free_entry_granted", mapOf(
            "promotion_id" to saved.id.toString(),
            "player_id" to playerId,
            "round_id" to roundId?.toString(),
            "expires_at" to expiresAt?.toString()
        ), playerId = playerId)
        return saved
    }

    @Transactional
    fun resolvePromotionsForPlayer(tenantId: String, playerId: String, registeredAt: OffsetDateTime? = null) {
        val tenant = tenantRepository.findById(tenantId).orElse(null) ?: return
        if (!tenant.freeEntryEnabled) return

        val launchedAt = tenant.quickPicksLaunchedAt
        if (launchedAt != null && registeredAt != null && registeredAt.isBefore(launchedAt)) return

        // Check if player has any entries
        val counters = playerProfileService.getCounters(tenantId, playerId)
        val entriesCount = (counters["entries_placed"] as? Number)?.toLong() ?: 0L
        
        if (entriesCount == 0L) {
            // Check if they already have an active promotion
            val existing = promotionRepository.findFirstByTenantIdAndPlayerIdAndTypeAndStatusOrderByGrantedAtAsc(
                tenantId, playerId, "FREE_ENTRY", "ACTIVE"
            )
            if (existing == null) {
                // Grant first entry free promotion
                grantPromotion(
                    tenantId = tenantId,
                    playerId = playerId,
                    expiresAt = OffsetDateTime.now().plusMinutes(tenant.freeEntryGraceMinutes.toLong())
                )
            }
        }
    }

    @Transactional
    fun grantPromotionsToEligiblePlayers(tenantId: String, roundId: UUID) {
        // This could be a background job for specific round promotions
    }

    @Transactional
    fun expirePromotions() {
        val expired = promotionRepository.findAllByStatusAndExpiresAtBefore("ACTIVE", OffsetDateTime.now())
        expired.forEach { it.status = "EXPIRED" }
        promotionRepository.saveAll(expired)
    }
}
