package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.*
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.trending.RedisTrendingStore
import io.qplay.quickpicksgameservice.exception.PlayerNotAllowlistedException
import io.qplay.quickpicksgameservice.infra.wallet.*
import io.qplay.quickpicksgameservice.service.outbox.OutboxService
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class EntryService(
    private val entryRepository: EntryRepository,
    private val roundRepository: RoundRepository,
    private val tenantRepository: TenantRepository,
    private val walletClient: HmacWalletClient,
    private val walletTransactionRepository: WalletTransactionRepository,
    private val trendingStore: RedisTrendingStore,
    private val targetingService: TargetingService,
    private val outboxService: OutboxService,
    private val playerProfileService: PlayerProfileService,
    private val firstEntryFreeService: FirstEntryFreeService,
    private val exclusionEnforcer: ExclusionEnforcer,
    private val auditService: AuditService,
    private val draftService: DraftService
) {
    @Transactional
    fun createEntry(roundId: UUID, playerId: String, picks: PicksPayload, currency: String, idempotencyKey: String, playerExclusions: Map<String, Boolean> = emptyMap(), isPreview: Boolean = false, tiebreaker: Int? = null): Entry {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        
        if (isPreview) {
            throw IllegalArgumentException("Entry creation is disabled in preview mode")
        }
        
        entryRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
            ?.let { return it }

        val tenant = tenantRepository.findById(tenantId)
            .orElseThrow { IllegalStateException("Tenant not found: $tenantId") }

        // 1. Targeting & Exclusion Checks
        when (targetingService.checkAccess(tenantId, playerId, tenant.targetingMode)) {
            AccessResult.BLOCKED -> throw IllegalArgumentException("Player is not eligible to enter")
            AccessResult.COMING_SOON -> throw PlayerNotAllowlistedException()
            AccessResult.ALLOWED -> {}
        }
        try {
            exclusionEnforcer.checkExclusions(tenantId, playerId, playerExclusions)
        } catch (e: IllegalArgumentException) {
            auditService.log(tenantId, playerId, "ENTRY_BLOCKED_BY_EXCLUSION", roundId.toString(), mapOf("error" to e.message))
            throw e
        }

        val round = roundRepository.findById(roundId)
            .orElseThrow { IllegalArgumentException("Round not found") }

        require(round.status == RoundStatus.OPEN) { "Round is not OPEN (current status: ${round.status})" }
        
        validatePicks(picks, round.slate.matches.map { it.providerMatchId })

        val stakePence = 100L // Fixed 100 pence (1 GBP) stake for POC
        
        // 2. Promotion Check (FREE_ENTRY)
        val promotion = firstEntryFreeService.getEligiblePromotion(tenantId, playerId, roundId)
        val isFreeEntry = promotion != null

        val walletResponse = if (isFreeEntry) {
            val promoId = requireNotNull(promotion?.id) { "Promotion ID must not be null" }
            // Skip wallet debit if free entry
            WalletResponse(
                status = "SUCCESS",
                providerTransactionId = "FREE_PROMO_$promoId",
                errorCode = null,
                errorMessage = null
            )
        } else {
            // Debit Wallet
            val walletRequest = WalletRequest(
                playerId = playerId,
                amountPence = stakePence,
                currency = currency,
                transactionId = idempotencyKey,
                reference = "Quick Picks Entry - Round ${round.id}",
                metadata = mapOf("roundId" to round.id.toString())
            )

            val walletTx = walletTransactionRepository.save(WalletTransaction(
                tenantId = tenantId,
                playerId = playerId,
                round = round,
                entry = null,
                type = TransactionType.DEBIT,
                amountPence = stakePence,
                currency = currency,
                idempotencyKey = idempotencyKey,
                status = TransactionStatus.PENDING
            ))

            val response = walletClient.debit(tenant, walletRequest)
            
            walletTx.status = when (walletClient.classify(response)) {
                WalletResponseClassification.SUCCESS -> TransactionStatus.SUCCESS
                WalletResponseClassification.KNOWN_FAILURE -> TransactionStatus.FAILED
                WalletResponseClassification.AMBIGUOUS -> TransactionStatus.PENDING
            }
            walletTx.providerTxId = response.providerTransactionId
            walletTx.errorCode = response.errorCode
            walletTx.updatedAt = OffsetDateTime.now()
            walletTransactionRepository.save(walletTx)

            if (walletTx.status == TransactionStatus.FAILED) {
                throw IllegalArgumentException("Wallet debit failed: ${response.errorMessage ?: response.errorCode}")
            }
            
            if (walletTx.status != TransactionStatus.SUCCESS) {
                throw RuntimeException("Wallet debit status is ${walletTx.status}. Cannot proceed.")
            }
            response
        }

        // 3. Consume Promotion if used
        if (promotion != null) {
            firstEntryFreeService.consumePromotion(promotion)
        }

        val entry = Entry(
            round = round,
            tenantId = tenantId,
            playerId = playerId,
            picks = picks,
            tiebreaker = tiebreaker,
            stakePence = if (isFreeEntry) 0L else stakePence,
            currency = currency,
            status = EntryStatus.ACCEPTED,
            transactionId = walletResponse.providerTransactionId,
            idempotencyKey = idempotencyKey
        )
        
        val savedEntry = entryRepository.save(entry)

        // 4. Distribute paid stake to prize pools: 30% jackpot / 15% runner-up / 10% consolation
        //    45% (40% rollover reserve + 5% margin) is platform-retained and not tracked per-round.
        if (!isFreeEntry) {
            round.jackpotPoolPence += stakePence * 30 / 100
            round.elevenPoolPence  += stakePence * 15 / 100
            round.tenPoolPence     += stakePence * 10 / 100
            roundRepository.save(round)
        }

        // 5. Clean up any saved draft for this round
        draftService.deleteDraft(playerId, roundId)

        // 6. Update Player Profile
        playerProfileService.incrementEntriesPlaced(tenantId, playerId)
        playerProfileService.trackPicksAndUpdateStyle(tenantId, playerId, picks.picks, tenant.dominantStyleThresholdPct)

        // 7. Increment Trending (fire-and-forget)
        trendingStore.increment(roundId.toString(), round.id.toString(), "ENTRY_COUNT")
        picks.picks.forEach { pick ->
            trendingStore.increment(roundId.toString(), pick.providerMatchId, pick.outcome.name)
        }

        // 8. Track in Outbox
        outboxService.trackEvent("entry_placed", mapOf(
            "player_id" to playerId,
            "entry_id" to savedEntry.id.toString(),
            "round_id" to roundId.toString(),
            "stake_pence" to entry.stakePence,
            "is_free" to isFreeEntry
        ), playerId = playerId)
        
        return savedEntry
    }

    fun validatePicks(picks: PicksPayload, validProviderMatchIds: List<String>) {
        require(picks.picks.size == 12) { "Entry must have exactly 12 picks" }
        
        val uniqueMatchIds = picks.picks.map { it.providerMatchId }.toSet()
        require(uniqueMatchIds.size == 12) { "Duplicate match IDs in picks" }
        
        picks.picks.forEach { pick ->
            require(pick.providerMatchId in validProviderMatchIds) { 
                "Invalid providerMatchId: ${pick.providerMatchId}" 
            }
        }
    }

    fun getPlayerHistory(playerId: String): List<Entry> {
        return entryRepository.findByPlayerId(playerId)
    }

    fun getEntry(entryId: UUID): Entry {
        return entryRepository.findById(entryId)
            .orElseThrow { IllegalArgumentException("Entry not found") }
    }
}
