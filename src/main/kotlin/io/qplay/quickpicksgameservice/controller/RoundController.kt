package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.round.Round
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.PlayerJwtClaims
import io.qplay.quickpicksgameservice.service.FirstEntryFreeService
import io.qplay.quickpicksgameservice.service.RoundService
import io.qplay.quickpicksgameservice.tenant.TenantContext
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/rounds")
class RoundController(
    private val roundService: RoundService,
    private val firstEntryFreeService: FirstEntryFreeService
) {
    @GetMapping("/{id}")
    fun getRound(@PathVariable id: UUID, @AuthenticationPrincipal claims: PlayerJwtClaims?): ApiResponse<RoundResponse> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        if (claims != null) {
            firstEntryFreeService.resolvePromotionsForPlayer(tenantId, claims.playerId, claims.registeredAt)
        }
        val round = roundService.getRound(id)
        return ApiResponse(RoundResponse.fromDomain(round))
    }

    @GetMapping("/open")
    fun getOpenRounds(@AuthenticationPrincipal claims: PlayerJwtClaims?): ApiResponse<List<RoundResponse>> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        if (claims != null) {
            firstEntryFreeService.resolvePromotionsForPlayer(tenantId, claims.playerId, claims.registeredAt)
        }
        val data = roundService.findOpenRounds().map { RoundResponse.fromDomain(it) }
        return ApiResponse(data)
    }
}

data class RoundResponse(
    val id: UUID,
    val slateId: UUID,
    val status: String,
    val jackpotPoolPence: Long,
    val matches: List<MatchResponse>
) {
    companion object {
        fun fromDomain(round: Round) = RoundResponse(
            id = round.id ?: throw IllegalStateException("Round ID must not be null for response mapping"),
            slateId = round.slate.id ?: throw IllegalStateException("Slate ID must not be null for response mapping"),
            status = round.status.name,
            jackpotPoolPence = round.jackpotPoolPence,
            matches = round.slate.matches.map { 
                MatchResponse(
                    id = it.id ?: throw IllegalStateException("Fixture ID must not be null for response mapping"),
                    providerMatchId = it.providerMatchId,
                    homeTeam = it.homeTeam,
                    awayTeam = it.awayTeam,
                    kickOff = it.kickOff,
                    league = it.league
                )
            }
        )
    }
}
