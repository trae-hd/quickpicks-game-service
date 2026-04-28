package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.analytics.AnalyticsImpression
import io.qplay.quickpicksgameservice.domain.analytics.AnalyticsImpressionRepository
import io.qplay.quickpicksgameservice.domain.draft.PlayerDraftRepository
import io.qplay.quickpicksgameservice.domain.entry.EntryRepository
import io.qplay.quickpicksgameservice.domain.entry.EntryResultRepository
import io.qplay.quickpicksgameservice.domain.promotion.PromotionSlotClickRepository
import io.qplay.quickpicksgameservice.domain.round.PrizeTier
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

data class EntryMetrics(val total: Long, val paid: Long, val free: Long)

data class RevenueMetrics(val grossStakePence: Long, val freeEntryCount: Long)

data class PoolMetrics(
    val jackpotPoolPence: Long,
    val elevenPoolPence: Long,
    val tenPoolPence: Long,
    val rolloverInPence: Long,
    val remainderPence: Long
)

data class SettlementMetrics(
    val jackpotWinners: Int,
    val runnerUpWinners: Int,
    val consolationWinners: Int,
    val totalPrizePaidPence: Long,
    val settledAt: Instant?
)

data class CrossSellMetrics(val roundImpressions: Long, val roundClicks: Long, val ctr: Double)

data class FunnelMetrics(
    val draftsCreated: Long?,
    val entryCount: Long,
    val draftToEntryConversionPct: Double?
)

data class JackpotRolloverMetrics(
    val consecutiveRoundsWithoutJackpot: Int,
    val currentJackpotPence: Long
)

data class RoundAnalytics(
    val roundId: UUID,
    val roundStatus: RoundStatus,
    val entries: EntryMetrics,
    val revenue: RevenueMetrics,
    val pools: PoolMetrics,
    val settlement: SettlementMetrics?,
    val crossSell: CrossSellMetrics,
    val funnel: FunnelMetrics,
    val jackpotRollover: JackpotRolloverMetrics
)

@Service
class AnalyticsService(
    private val roundRepository: RoundRepository,
    private val entryRepository: EntryRepository,
    private val entryResultRepository: EntryResultRepository,
    private val analyticsImpressionRepository: AnalyticsImpressionRepository,
    private val promotionSlotClickRepository: PromotionSlotClickRepository,
    private val playerDraftRepository: PlayerDraftRepository
) {
    @Transactional
    fun recordImpression(roundId: UUID, hostPlayerId: String, impressionType: String) {
        val tenantId = TenantContext.getRequiredTenantId()
        analyticsImpressionRepository.save(
            AnalyticsImpression(
                tenantId = tenantId,
                roundId = roundId,
                hostPlayerId = hostPlayerId,
                impressionType = impressionType
            )
        )
    }

    @Transactional(readOnly = true)
    fun getRoundAnalytics(roundId: UUID): RoundAnalytics {
        val tenantId = TenantContext.getRequiredTenantId()
        val round = roundRepository.findById(roundId)
            .orElseThrow { IllegalArgumentException("Round not found") }
        require(round.tenantId == tenantId) { "Round not found" }

        val entries = entryRepository.findByRoundId(roundId)
        val paidCount = entries.count { !it.isFreeEntry }
        val freeCount = entries.count { it.isFreeEntry }
        val grossStakePence = entries.sumOf { it.stakePence }

        val entryIds = entries.mapNotNull { it.id }
        val results = if (entryIds.isNotEmpty()) entryResultRepository.findByEntryIdIn(entryIds) else emptyList()

        val settlement: SettlementMetrics? = if (round.status == RoundStatus.SETTLED && results.isNotEmpty()) {
            SettlementMetrics(
                jackpotWinners = results.count { it.prizeTier == PrizeTier.JACKPOT_12_12 },
                runnerUpWinners = results.count { it.prizeTier == PrizeTier.RUNNER_UP_11_12 },
                consolationWinners = results.count { it.prizeTier == PrizeTier.CONSOLATION_10_12 },
                totalPrizePaidPence = results.sumOf { it.prizePence },
                settledAt = round.settledAt
            )
        } else null

        val roundImpressions = analyticsImpressionRepository.countByRoundId(roundId)
        val roundClicks = promotionSlotClickRepository.countByRoundId(roundId)
        val ctr = if (roundImpressions > 0) {
            Math.round((roundClicks.toDouble() / roundImpressions.toDouble()) * 1000.0) / 10.0
        } else 0.0

        val draftsCreated: Long? = if (round.status == RoundStatus.OPEN) {
            playerDraftRepository.countByTenantIdAndRoundId(tenantId, roundId)
        } else null
        val draftToEntryConversionPct: Double? = if (draftsCreated != null && draftsCreated > 0) {
            Math.round((entries.size.toDouble() / draftsCreated.toDouble()) * 1000.0) / 10.0
        } else null

        val jackpotRoundIds = entryResultRepository.findRoundIdsWithJackpotWinner(tenantId).toSet()
        val settledRounds = roundRepository.findByTenantIdAndStatusOrderBySettledAtDesc(tenantId, RoundStatus.SETTLED)
        var consecutiveWithoutJackpot = 0
        for (r in settledRounds) {
            if (requireNotNull(r.id) { "Settled round ID must not be null" } in jackpotRoundIds) break
            consecutiveWithoutJackpot++
        }

        return RoundAnalytics(
            roundId = roundId,
            roundStatus = round.status,
            entries = EntryMetrics(
                total = entries.size.toLong(),
                paid = paidCount.toLong(),
                free = freeCount.toLong()
            ),
            revenue = RevenueMetrics(
                grossStakePence = grossStakePence,
                freeEntryCount = freeCount.toLong()
            ),
            pools = PoolMetrics(
                jackpotPoolPence = round.jackpotPoolPence,
                elevenPoolPence = round.elevenPoolPence,
                tenPoolPence = round.tenPoolPence,
                rolloverInPence = round.rolloverInPence,
                remainderPence = round.remainderPence
            ),
            settlement = settlement,
            crossSell = CrossSellMetrics(
                roundImpressions = roundImpressions,
                roundClicks = roundClicks,
                ctr = ctr
            ),
            funnel = FunnelMetrics(
                draftsCreated = draftsCreated,
                entryCount = entries.size.toLong(),
                draftToEntryConversionPct = draftToEntryConversionPct
            ),
            jackpotRollover = JackpotRolloverMetrics(
                consecutiveRoundsWithoutJackpot = consecutiveWithoutJackpot,
                currentJackpotPence = round.jackpotPoolPence
            )
        )
    }
}
