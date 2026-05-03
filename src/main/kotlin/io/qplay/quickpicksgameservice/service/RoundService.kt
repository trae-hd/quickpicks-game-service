package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.round.Round
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.slate.Slate
import io.qplay.quickpicksgameservice.service.outbox.OutboxService
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(RoundService::class.java)
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

    @Transactional(readOnly = true)
    fun getRound(roundId: UUID): Round {
        val round = roundRepository.findById(roundId)
            .orElseThrow { IllegalArgumentException("Round not found") }
        round.slate.matches.size // force lazy collection init before session closes
        return round
    }

    @Transactional(readOnly = true)
    fun findOpenRounds(): List<Round> {
        val rounds = roundRepository.findByStatusIn(listOf(RoundStatus.OPEN, RoundStatus.LOCKED))
        rounds.forEach { it.slate.matches.size } // force lazy collection init before session closes
        return rounds
    }

    @Transactional
    fun autoLockExpiredRounds() {
        val expired = roundRepository.findOpenRoundsPassedWindow(Instant.now())
        expired.forEach { round ->
            round.status = RoundStatus.LOCKED
            round.lockedAt = Instant.now()
            roundRepository.save(round)
            draftService.deleteAllByRoundId(round.id!!)
            outboxService.trackEvent("round_locked", mapOf("round_id" to round.id.toString()))
            logger.info("Auto-locked round ${round.id} (round window has ended)")
        }
    }
}
