package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.*
import io.qplay.quickpicksgameservice.domain.round.*
import io.qplay.quickpicksgameservice.domain.sportsfeed.*
import io.qplay.quickpicksgameservice.infra.feed.ApiFootballClient
import io.qplay.quickpicksgameservice.infra.redis.RenewableRedisLock
import io.qplay.quickpicksgameservice.infra.wallet.HmacWalletClient
import io.qplay.quickpicksgameservice.infra.wallet.WalletRequest
import io.qplay.quickpicksgameservice.service.outbox.OutboxService
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class SettlementService(
    private val roundRepository: RoundRepository,
    private val entryRepository: EntryRepository,
    private val entryResultRepository: EntryResultRepository,
    private val tenantRepository: TenantRepository,
    private val walletClient: HmacWalletClient,
    private val redisTemplate: StringRedisTemplate,
    private val outboxService: OutboxService,
    private val apiFootballClient: ApiFootballClient,
    private val feedMapper: FeedMapper,
    private val fieldMappingRepository: FeedFieldMappingRepository,
    private val statusTranslationRepository: FeedStatusTranslationRepository
) {
    private val logger = LoggerFactory.getLogger(SettlementService::class.java)

    @Transactional
    fun settleRoundNow(roundId: UUID) {
        val round = roundRepository.findById(roundId)
            .orElseThrow { IllegalArgumentException("Round not found: $roundId") }
        require(round.status == RoundStatus.LOCKED) { "Only LOCKED rounds can be manually settled" }
        val lock = RenewableRedisLock(redisTemplate, "settlement:round:$roundId")
        if (!lock.tryAcquire()) throw IllegalStateException("Settlement already in progress for round $roundId")
        try {
            settleRound(round, lock)
        } finally {
            lock.release()
        }
    }

    @Transactional
    fun processSettlement() {
        val roundsToSettle = roundRepository.findByStatus(RoundStatus.LOCKED)
            .filter { it.settleAfter != null && it.settleAfter?.isBefore(Instant.now()) == true }

        roundsToSettle.forEach { round ->
            val roundId = requireNotNull(round.id) { "Round ID must not be null for settlement" }
            val lock = RenewableRedisLock(redisTemplate, "settlement:round:$roundId")
            if (lock.tryAcquire()) {
                try {
                    settleRound(round, lock)
                } finally {
                    lock.release()
                }
            }
        }
    }

    private fun settleRound(round: Round, lock: RenewableRedisLock) {
        val roundId = requireNotNull(round.id) { "Round ID must not be null" }
        logger.info("Starting settlement for round $roundId")
        val matches = round.slate.matches
        
        // Critical: Section 18 Post-cooldown sanity check
        // Re-fetch all match results from feed provider to ensure no corrections happened during cooldown
        val mappings = fieldMappingRepository.findByProviderId("api-football").associate { it.canonicalField to it.providerJsonPath }
        val translations = statusTranslationRepository.findByProviderId("api-football").associate { it.providerStatus to it.canonicalStatus }
        
        val refreshedMatches = matches.map { match ->
            val rawJson = apiFootballClient.getFixtureById(match.providerMatchId.toInt())
            feedMapper.mapToMatch(rawJson, mappings, translations)
        }

        // Partition refreshed matches by status
        val postponedMatches = refreshedMatches.filter { it.status == CanonicalMatchStatus.POSTPONED }
        val finishedMatches  = refreshedMatches.filter { it.status == CanonicalMatchStatus.FINISHED }
        val unknownMatches   = refreshedMatches.filter {
            it.status != CanonicalMatchStatus.FINISHED && it.status != CanonicalMatchStatus.POSTPONED
        }

        // Any match still live/scheduled means results aren't ready yet
        if (unknownMatches.isNotEmpty()) {
            logger.warn("Round $roundId has ${unknownMatches.size} non-terminal matches. Moving to REQUIRES_REVIEW")
            round.status = RoundStatus.REQUIRES_REVIEW
            roundRepository.save(round)
            return
        }

        // Score mismatch on finished matches → admin review
        val hasScoreMismatch = finishedMatches.any { refreshed ->
            val original = requireNotNull(matches.find { it.providerMatchId == refreshed.providerMatchId }) {
                "Original match not found for provider ID ${refreshed.providerMatchId}"
            }
            refreshed.regulationResultHome != original.regulationResultHome ||
            refreshed.regulationResultAway != original.regulationResultAway
        }
        if (hasScoreMismatch) {
            logger.warn("Sanity check failed for round $roundId: score corrections detected. Moving to REQUIRES_REVIEW")
            round.status = RoundStatus.REQUIRES_REVIEW
            roundRepository.save(round)
            return
        }

        // Postponement handling
        val postponedCount = postponedMatches.size
        if (postponedCount > 0) {
            val psm = PostponementStateMachine.evaluate(postponedCount)
            logger.info("Round $roundId has $postponedCount postponed match(es). Behaviour: ${psm.behaviour}")
            if (psm.behaviour == PostponementBehaviour.VOID_ROUND) {
                voidRound(round, roundId, lock)
                return
            }
            // SETTLE_ON_11 / SETTLE_ON_10: exclude postponed matches and use adjusted thresholds
            val postponedIds = postponedMatches.map { it.providerMatchId }.toSet()
            val resultsMap = finishedMatches.associate { it.providerMatchId to calculateOutcome(it.regulationResultHome, it.regulationResultAway) }
            val entries = entryRepository.findByRoundId(roundId)
            scoreAndPayout(round, roundId, entries, resultsMap, postponedIds, psm.jackpotThreshold, psm.runnerUpThreshold, psm.consolationThreshold, lock)
            return
        }

        val resultsMap = finishedMatches.associate { it.providerMatchId to calculateOutcome(it.regulationResultHome, it.regulationResultAway) }

        if (resultsMap.values.any { it == null }) {
            logger.warn("Round $roundId has matches without results. Moving to REQUIRES_REVIEW")
            round.status = RoundStatus.REQUIRES_REVIEW
            roundRepository.save(round)
            return
        }

        val entries = entryRepository.findByRoundId(roundId)
        scoreAndPayout(round, roundId, entries, resultsMap, emptySet(), 12, 11, 10, lock)
    }

    private fun scoreAndPayout(
        round: Round,
        roundId: UUID,
        entries: List<Entry>,
        resultsMap: Map<String, Outcome?>,
        excludedMatchIds: Set<String>,
        jackpotThreshold: Int,
        runnerUpThreshold: Int,
        consolationThreshold: Int,
        lock: RenewableRedisLock
    ) {
        val winners12 = mutableListOf<Entry>()
        val winners11 = mutableListOf<Entry>()
        val winners10 = mutableListOf<Entry>()

        entries.forEach { entry ->
            val entryId = requireNotNull(entry.id) { "Entry ID must not be null" }
            var correctCount = 0
            entry.picks.picks.forEach { pick ->
                if (pick.providerMatchId !in excludedMatchIds && resultsMap[pick.providerMatchId] == pick.outcome) {
                    correctCount++
                }
            }

            val result = EntryResult(
                entryId = entryId,
                entry = entry,
                tenantId = entry.tenantId,
                correctPicks = correctCount,
                isJackpotWinner = correctCount == jackpotThreshold
            )

            when (correctCount) {
                jackpotThreshold    -> { result.prizeTier = PrizeTier.JACKPOT_12_12;     winners12.add(entry) }
                runnerUpThreshold   -> { result.prizeTier = PrizeTier.RUNNER_UP_11_12;   winners11.add(entry) }
                consolationThreshold -> { result.prizeTier = PrizeTier.CONSOLATION_10_12; winners10.add(entry) }
            }
            entryResultRepository.save(result)
        }

        // Calculate prizes per winner
        val jackpotEach = if (winners12.isNotEmpty()) round.jackpotPoolPence / winners12.size else 0
        val elevenEach = if (winners11.isNotEmpty()) round.elevenPoolPence / winners11.size else 0
        val tenEach = if (winners10.isNotEmpty()) round.tenPoolPence / winners10.size else 0

        // Store remainders for next round rollover logic (omitted for POC simplicity, but tracked in schema)
        round.remainderPence = (round.jackpotPoolPence % (if(winners12.isNotEmpty()) winners12.size else 1)) +
                               (round.elevenPoolPence % (if(winners11.isNotEmpty()) winners11.size else 1)) +
                               (round.tenPoolPence % (if(winners10.isNotEmpty()) winners10.size else 1))

        // Issue credits
        val tenant = tenantRepository.findById(round.tenantId).orElseThrow()
        
        winners12.forEach { creditWinner(it, jackpotEach, tenant, lock) }
        winners11.forEach { creditWinner(it, elevenEach, tenant, lock) }
        winners10.forEach { creditWinner(it, tenEach, tenant, lock) }

        round.status = RoundStatus.SETTLED
        round.settledAt = Instant.now()
        roundRepository.save(round)

        if (winners12.isEmpty()) {
            outboxService.trackEvent("jackpot_rollover", mapOf(
                "round_id" to round.id.toString(),
                "jackpot_pool_pence" to round.jackpotPoolPence
            ))
        }

        outboxService.trackEvent("round_settled", mapOf(
            "round_id" to round.id.toString(),
            "jackpot_each" to jackpotEach,
            "eleven_each" to elevenEach,
            "ten_each" to tenEach,
            "winners_count" to (winners12.size + winners11.size + winners10.size)
        ))
        logger.info("Settlement completed for round ${round.id}")
    }

    private fun voidRound(round: Round, roundId: UUID, lock: RenewableRedisLock) {
        logger.warn("Voiding round $roundId due to excessive postponements")
        val tenant = tenantRepository.findById(round.tenantId).orElseThrow()
        val entries = entryRepository.findByRoundId(roundId)
        entries.forEach { entry ->
            if (entry.isFreeEntry || entry.stakePence <= 0) {
                entry.status = EntryStatus.VOID
                entryRepository.save(entry)
                return@forEach
            }
            if (lock.isLockLost()) throw RuntimeException("Lock lost during round void refunds!")
            val response = walletClient.credit(tenant, WalletRequest(
                playerId = entry.playerId,
                amountPence = entry.stakePence,
                currency = entry.currency,
                transactionId = "VOID-REFUND-${entry.id}",
                reference = "Quick Picks Round Voided"
            ))
            if (response.status == "SUCCESS") {
                entry.status = EntryStatus.VOID
                entryRepository.save(entry)
            } else {
                logger.error("Refund failed for entry ${entry.id} during void: ${response.errorMessage}")
            }
        }
        round.status = RoundStatus.VOIDED
        round.settledAt = Instant.now()
        roundRepository.save(round)
        outboxService.trackEvent("round_voided", mapOf("round_id" to roundId.toString()))
    }

    private fun creditWinner(entry: Entry, amount: Long, tenant: io.qplay.quickpicksgameservice.tenant.persistence.Tenant, lock: RenewableRedisLock) {
        if (lock.isLockLost()) {
            throw RuntimeException("Lock lost during settlement credit calls!")
        }
        if (amount <= 0) return

        val entryId = requireNotNull(entry.id) { "Entry ID must not be null" }
        val existing = entryResultRepository.findById(entryId).orElseThrow {
            IllegalStateException("Entry result not found for ID $entryId")
        }
        if (existing.creditIssued) {
            logger.info("Credit already issued for entry $entryId — skipping")
            return
        }

        val response = walletClient.credit(tenant, WalletRequest(
            playerId = entry.playerId,
            amountPence = amount,
            currency = entry.currency,
            transactionId = "CREDIT-${entry.id}",
            reference = "Quick Picks Prize"
        ))

        if (response.status == "SUCCESS") {
            existing.prizePence = amount
            existing.settledAt = Instant.now()
            existing.creditIssued = true
            entryResultRepository.save(existing)
            
            entry.status = EntryStatus.SETTLED
            entryRepository.save(entry)

            outboxService.trackEvent("player_won", mapOf(
                "player_id" to entry.playerId,
                "entry_id" to entryId.toString(),
                "prize_pence" to amount,
                "tier" to existing.prizeTier?.name,
                "round_id" to entry.round.id.toString()
            ), playerId = entry.playerId, batchEligible = false)
        } else {
            logger.error("Failed to credit winner for entry ${entry.id}: ${response.errorMessage}")
            // In a real system, this would trigger a retry or manual review
        }
    }

    private fun calculateOutcome(home: Int?, away: Int?): Outcome? {
        if (home == null || away == null) return null
        return when {
            home > away -> Outcome.HOME
            away > home -> Outcome.AWAY
            else -> Outcome.DRAW
        }
    }
}
