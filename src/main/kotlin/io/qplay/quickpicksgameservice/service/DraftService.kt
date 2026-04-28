package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.draft.DraftPicksPayload
import io.qplay.quickpicksgameservice.domain.draft.PlayerDraft
import io.qplay.quickpicksgameservice.domain.draft.PlayerDraftRepository
import io.qplay.quickpicksgameservice.domain.entry.Pick
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.tenant.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class DraftService(
    private val draftRepository: PlayerDraftRepository,
    private val roundRepository: RoundRepository
) {
    private val logger = LoggerFactory.getLogger(DraftService::class.java)

    fun getDraft(playerId: String, roundId: UUID): PlayerDraft? {
        val tenantId = TenantContext.getRequiredTenantId()
        return draftRepository.findByTenantIdAndRoundIdAndHostPlayerId(tenantId, roundId, playerId)
    }

    @Transactional
    fun upsertDraft(playerId: String, roundId: UUID, picks: List<Pick>, tiebreaker: Int?): PlayerDraft {
        val tenantId = TenantContext.getRequiredTenantId()

        val round = roundRepository.findById(roundId)
            .orElseThrow { IllegalArgumentException("Round not found") }
        require(round.status == RoundStatus.OPEN) { "Cannot save a draft — round is not OPEN" }

        val existing = draftRepository.findByTenantIdAndRoundIdAndHostPlayerId(tenantId, roundId, playerId)
        return if (existing != null) {
            existing.picks = DraftPicksPayload(picks)
            existing.tiebreaker = tiebreaker
            draftRepository.save(existing)
        } else {
            draftRepository.save(
                PlayerDraft(
                    tenantId = tenantId,
                    roundId = roundId,
                    hostPlayerId = playerId,
                    picks = DraftPicksPayload(picks),
                    tiebreaker = tiebreaker
                )
            )
        }
    }

    @Transactional
    fun deleteDraft(playerId: String, roundId: UUID) {
        val tenantId = TenantContext.getRequiredTenantId()
        draftRepository.deleteByTenantIdAndRoundIdAndHostPlayerId(tenantId, roundId, playerId)
    }

    @Transactional
    fun deleteAllByRoundId(roundId: UUID): Int {
        val count = draftRepository.deleteAllByRoundId(roundId)
        logger.info("Deleted $count draft(s) for locked round $roundId")
        return count
    }
}
