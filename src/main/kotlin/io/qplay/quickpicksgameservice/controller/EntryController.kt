package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.entry.CreateEntryRequest
import io.qplay.quickpicksgameservice.domain.entry.Entry
import io.qplay.quickpicksgameservice.domain.entry.PicksPayload
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.idempotency.Idempotent
import io.qplay.quickpicksgameservice.ratelimit.RateLimited
import io.qplay.quickpicksgameservice.security.PlayerJwtClaims
import io.qplay.quickpicksgameservice.service.EntryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/rounds/{roundId}/entries")
@Tag(name = "Entries", description = "Player endpoints for placing and viewing entries")
class EntryController(
    private val entryService: EntryService
) {
    @PostMapping
    @Idempotent
    @RateLimited
    @Operation(summary = "Place a new entry for a round")
    fun createEntry(
        @PathVariable roundId: UUID,
        @AuthenticationPrincipal claims: PlayerJwtClaims,
        @RequestBody request: CreateEntryRequest,
        @RequestParam(defaultValue = "GBP") currency: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ApiResponse<EntryResponse> {
        val entry = entryService.createEntry(
            roundId, 
            claims.playerId, 
            request.picks, 
            currency, 
            idempotencyKey, 
            request.playerExclusions ?: emptyMap(),
            claims.preview
        )
        return ApiResponse(EntryResponse.fromDomain(entry))
    }

    @GetMapping("/me")
    @Operation(summary = "Get player's entry history for a round")
    fun getMyHistory(@AuthenticationPrincipal claims: PlayerJwtClaims): ApiResponse<List<EntryResponse>> {
        val data = entryService.getPlayerHistory(claims.playerId).map { EntryResponse.fromDomain(it) }
        return ApiResponse(data)
    }
}

data class EntryResponse(
    val id: UUID,
    val roundId: UUID,
    val playerId: String,
    val picks: PicksPayload,
    val status: String,
    val stakePence: Long,
    val currency: String,
    val createdAt: Instant
) {
    companion object {
        fun fromDomain(entry: Entry) = EntryResponse(
            id = entry.id ?: throw IllegalStateException("Entry ID must not be null for response mapping"),
            roundId = entry.round.id ?: throw IllegalStateException("Round ID must not be null for response mapping"),
            playerId = entry.playerId,
            picks = entry.picks,
            status = entry.status.name,
            stakePence = entry.stakePence,
            currency = entry.currency,
            createdAt = entry.createdAt ?: throw IllegalStateException("Entry creation timestamp must not be null for response mapping")
        )
    }
}
