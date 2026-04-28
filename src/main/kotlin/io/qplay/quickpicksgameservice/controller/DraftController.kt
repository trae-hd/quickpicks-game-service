package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.domain.draft.PlayerDraft
import io.qplay.quickpicksgameservice.domain.entry.Pick
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.PlayerJwtClaims
import io.qplay.quickpicksgameservice.service.DraftService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/players/me/draft")
@Tag(name = "Drafts", description = "Player draft picks — auto-save and restore")
class DraftController(
    private val draftService: DraftService
) {
    @GetMapping
    @Operation(summary = "Get the player's current draft for a round, or 204 if none exists")
    fun getDraft(
        @RequestParam roundId: UUID,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ResponseEntity<ApiResponse<DraftResponse>> {
        val draft = draftService.getDraft(claims.playerId, roundId)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(ApiResponse(DraftResponse.fromDomain(draft)))
    }

    @PutMapping
    @Operation(summary = "Create or update a draft (upsert). Picks may be partial — fewer than 12 is valid for a draft.")
    fun upsertDraft(
        @RequestBody request: UpsertDraftRequest,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ApiResponse<DraftResponse> {
        val draft = draftService.upsertDraft(claims.playerId, request.roundId, request.picks, request.tiebreaker)
        return ApiResponse(DraftResponse.fromDomain(draft))
    }

    @DeleteMapping
    @Operation(summary = "Delete a draft. Called automatically on successful entry submission.")
    fun deleteDraft(
        @RequestParam roundId: UUID,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ResponseEntity<Void> {
        draftService.deleteDraft(claims.playerId, roundId)
        return ResponseEntity.noContent().build()
    }
}

data class UpsertDraftRequest @JsonCreator constructor(
    @JsonProperty("roundId") val roundId: UUID,
    @JsonProperty("picks") val picks: List<Pick>,
    @JsonProperty("tiebreaker") val tiebreaker: Int?
)

data class DraftResponse(
    val id: UUID,
    val roundId: UUID,
    val picks: List<Pick>,
    val tiebreaker: Int?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromDomain(draft: PlayerDraft) = DraftResponse(
            id = requireNotNull(draft.id) { "Draft ID must not be null for response mapping" },
            roundId = draft.roundId,
            picks = draft.picks.picks,
            tiebreaker = draft.tiebreaker,
            createdAt = requireNotNull(draft.createdAt) { "Draft createdAt must not be null for response mapping" },
            updatedAt = requireNotNull(draft.updatedAt) { "Draft updatedAt must not be null for response mapping" }
        )
    }
}
