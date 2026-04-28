package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.round.Round
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.slate.Slate
import io.qplay.quickpicksgameservice.service.outbox.OutboxService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class RoundService(
    private val roundRepository: RoundRepository,
    private val outboxService: OutboxService,
    private val draftService: DraftService
) {
    @Transactional
    fun createRound(slate: Slate): Round {
        val round = Round(
            slate = slate,
            tenantId = slate.tenantId,
            status = RoundStatus.OPEN
        )
        val savedRound = roundRepository.save(round)
        outboxService.trackEvent("round_created", mapOf("round_id" to savedRound.id.toString()))
        return savedRound
    }

    @Transactional
    fun lockRound(roundId: UUID): Round {
        val round = roundRepository.findById(roundId)
            .orElseThrow { IllegalArgumentException("Round not found") }
        
        require(round.status == RoundStatus.OPEN) { "Only OPEN rounds can be locked" }
        
        round.status = RoundStatus.LOCKED
        round.lockedAt = Instant.now()
        val savedRound = roundRepository.save(round)
        draftService.deleteAllByRoundId(roundId)
        outboxService.trackEvent("round_locked", mapOf("round_id" to savedRound.id.toString()))
        return savedRound
    }

    fun getRound(roundId: UUID): Round {
        return roundRepository.findById(roundId)
            .orElseThrow { IllegalArgumentException("Round not found") }
    }

    fun findOpenRounds(): List<Round> {
        return roundRepository.findByStatus(RoundStatus.OPEN)
    }
}
