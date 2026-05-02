package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.slate.Match
import io.qplay.quickpicksgameservice.domain.slate.Slate
import io.qplay.quickpicksgameservice.domain.slate.SlateRepository
import io.qplay.quickpicksgameservice.domain.slate.SlateStatus
import io.qplay.quickpicksgameservice.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class SlateService(
    private val slateRepository: SlateRepository,
    private val roundService: RoundService,
    private val firstEntryFreeService: FirstEntryFreeService
) {
    @Transactional
    fun createDraft(roundWindowStart: Instant, roundWindowEnd: Instant, createdBy: String): Slate {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val slate = Slate(
            tenantId = tenantId,
            roundWindowStart = roundWindowStart,
            roundWindowEnd = roundWindowEnd,
            createdBy = createdBy,
            status = SlateStatus.DRAFT
        )
        return slateRepository.save(slate)
    }

    @Transactional
    fun addMatch(slateId: UUID, matchData: MatchData): Match {
        val slate = slateRepository.findById(slateId)
            .orElseThrow { IllegalArgumentException("Slate not found") }
        
        require(slate.status == SlateStatus.DRAFT) { "Cannot add match to slate in ${slate.status} status" }

        val match = Match(
            slate = slate,
            tenantId = slate.tenantId,
            providerMatchId = matchData.providerMatchId,
            homeTeam = matchData.homeTeam,
            awayTeam = matchData.awayTeam,
            kickOff = matchData.kickOff,
            league = matchData.league
        )
        slate.matches.add(match)
        slateRepository.save(slate)
        return match
    }

    @Transactional
    fun submitForApproval(slateId: UUID): Slate {
        val slate = slateRepository.findById(slateId)
            .orElseThrow { IllegalArgumentException("Slate not found") }
        
        require(slate.status == SlateStatus.DRAFT) { "Only DRAFT slates can be submitted" }
        require(slate.matches.size == 12) { "Slate must have exactly 12 matches (currently ${slate.matches.size})" }
        
        slate.status = SlateStatus.SUBMITTED
        return slateRepository.save(slate)
    }

    @Transactional
    fun approveAndPublish(slateId: UUID, approvedBy: String): Slate {
        val slate = slateRepository.findById(slateId)
            .orElseThrow { IllegalArgumentException("Slate not found") }
        
        require(slate.status == SlateStatus.SUBMITTED) { "Only SUBMITTED slates can be approved" }
        require(slate.createdBy != approvedBy) { "Creator cannot approve their own slate (two-eyes principle)" }

        slate.status = SlateStatus.PUBLISHED
        slate.approvedBy = approvedBy
        val savedSlate = slateRepository.save(slate)
        savedSlate.matches.size // force lazy collection init before session closes

        val round = roundService.createRound(savedSlate)
        
        // Grant promotions for the new round if needed
        val roundId = round.id ?: throw IllegalStateException("Round ID must not be null after save")
        firstEntryFreeService.grantPromotionsToEligiblePlayers(slate.tenantId, roundId)
        
        return savedSlate
    }

    @Transactional(readOnly = true)
    fun getSlate(slateId: UUID): Slate {
        val slate = slateRepository.findById(slateId)
            .orElseThrow { IllegalArgumentException("Slate not found") }
        slate.matches.size // force lazy collection init before session closes
        return slate
    }

    @Transactional(readOnly = true)
    fun listByStatus(status: SlateStatus?): List<Slate> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val slates = if (status != null) {
            slateRepository.findByTenantIdAndStatus(tenantId, status)
        } else {
            slateRepository.findByTenantId(tenantId)
        }
        slates.forEach { it.matches.size } // force lazy collection init before session closes
        return slates
    }

    @Transactional
    fun updateWindow(slateId: UUID, start: Instant, end: Instant): Slate {
        val slate = slateRepository.findById(slateId)
            .orElseThrow { IllegalArgumentException("Slate not found") }
        require(slate.status == SlateStatus.DRAFT) { "Window can only be updated on DRAFT slates" }
        require(start.isBefore(end)) { "roundWindowStart must be before roundWindowEnd" }
        slate.roundWindowStart = start
        slate.roundWindowEnd = end
        return slateRepository.save(slate)
    }

    @Transactional
    fun clearMatches(slateId: UUID) {
        val slate = slateRepository.findById(slateId)
            .orElseThrow { IllegalArgumentException("Slate not found") }
        require(slate.status == SlateStatus.DRAFT) { "Matches can only be cleared on DRAFT slates" }
        slate.matches.clear()
        slateRepository.save(slate)
    }
}

data class MatchData(
    val providerMatchId: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickOff: Instant,
    val league: String
)
