package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.slate.Slate
import io.qplay.quickpicksgameservice.domain.slate.SlateStatus
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.service.MatchData
import io.qplay.quickpicksgameservice.service.SlateService
import io.qplay.quickpicksgameservice.security.OperatorJwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/slates")
@PreAuthorize("hasRole('TENANT_ADMIN')")
@Tag(name = "Slate Management", description = "Operator endpoints for building and publishing slates")
class SlateController(
    private val slateService: SlateService
) {
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'REVIEWER')")
    @Operation(
        summary = "List slates",
        description = "Returns all slates for the current tenant ordered by creation date descending. Both TENANT_ADMIN and REVIEWER roles receive all tenant slates regardless of creator. Optionally filter by status to retrieve only DRAFT, SUBMITTED, or PUBLISHED slates."
    )
    fun listSlates(
        @Parameter(description = "Filter by slate status. One of: DRAFT, SUBMITTED, PUBLISHED. If omitted, all statuses are returned.")
        @RequestParam(required = false) status: String?
    ): ApiResponse<List<SlateResponse>> {
        val statusEnum = status?.let {
            runCatching { SlateStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $it. Valid values: ${SlateStatus.values().joinToString()}") }
        }
        return ApiResponse(slateService.listByStatus(statusEnum).map { SlateResponse.fromDomain(it) })
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'REVIEWER')")
    @Operation(
        summary = "Get slate by ID",
        description = "Returns a single slate by its UUID. Accessible to TENANT_ADMIN and REVIEWER roles."
    )
    fun getSlate(@PathVariable id: UUID): ApiResponse<SlateResponse> {
        return ApiResponse(SlateResponse.fromDomain(slateService.getSlate(id)))
    }

    @PostMapping
    @Operation(summary = "Create a new draft slate")
    fun createDraft(@RequestBody request: CreateSlateRequest, @AuthenticationPrincipal claims: OperatorJwtClaims): ApiResponse<SlateResponse> {
        val slate = slateService.createDraft(request.roundWindowStart, request.roundWindowEnd, claims.operatorId)
        return ApiResponse(SlateResponse.fromDomain(slate))
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update round window times on a DRAFT slate")
    fun updateWindow(
        @PathVariable id: UUID,
        @RequestBody request: UpdateSlateWindowRequest
    ): ApiResponse<SlateResponse> {
        val slate = slateService.updateWindow(id, request.roundWindowStart, request.roundWindowEnd)
        return ApiResponse(SlateResponse.fromDomain(slate))
    }

    @PostMapping("/{id}/matches")
    @Operation(summary = "Add a match fixture to a draft slate")
    fun addMatch(@PathVariable id: UUID, @RequestBody request: MatchData): ApiResponse<SlateResponse> {
        slateService.addMatch(id, request)
        val slate = slateService.getSlate(id)
        return ApiResponse(SlateResponse.fromDomain(slate))
    }

    @DeleteMapping("/{id}/matches")
    @Operation(summary = "Remove all matches from a DRAFT slate")
    fun clearMatches(@PathVariable id: UUID): ApiResponse<SlateResponse> {
        slateService.clearMatches(id)
        val slate = slateService.getSlate(id)
        return ApiResponse(SlateResponse.fromDomain(slate))
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a slate for approval")
    fun submitForApproval(@PathVariable id: UUID): ApiResponse<SlateResponse> {
        val slate = slateService.submitForApproval(id)
        return ApiResponse(SlateResponse.fromDomain(slate))
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'REVIEWER')")
    @Operation(
        summary = "Approve and publish a slate",
        description = "Approves a SUBMITTED slate and publishes it, creating a live round. The approving operator's identity (JWT sub) must differ from the operator who submitted the slate — the two-eyes rule is enforced and returns 409 Conflict if violated. Accessible to TENANT_ADMIN and REVIEWER roles."
    )
    fun approveAndPublish(@PathVariable id: UUID, @AuthenticationPrincipal claims: OperatorJwtClaims): ApiResponse<SlateResponse> {
        val slate = slateService.approveAndPublish(id, claims.operatorId)
        return ApiResponse(SlateResponse.fromDomain(slate))
    }
}

data class CreateSlateRequest(
    val roundWindowStart: Instant,
    val roundWindowEnd: Instant
)

data class UpdateSlateWindowRequest(
    val roundWindowStart: Instant,
    val roundWindowEnd: Instant
)

data class ChangedFixture(
    val providerMatchId: String,
    val homeTeam: String,
    val awayTeam: String,
    val oldKickOff: Instant,
    val newKickOff: Instant
)

data class SlateResponse(
    val id: UUID,
    val status: String,
    val roundWindowStart: Instant,
    val roundWindowEnd: Instant,
    val matches: List<MatchResponse>,
    val changedFixtures: List<ChangedFixture>
) {
    companion object {
        fun fromDomain(slate: Slate) = SlateResponse(
            id = slate.id ?: throw IllegalStateException("Slate ID must not be null for response mapping"),
            status = slate.status.name,
            roundWindowStart = slate.roundWindowStart,
            roundWindowEnd = slate.roundWindowEnd,
            matches = slate.matches.map {
                MatchResponse(
                    id = it.id ?: throw IllegalStateException("Fixture ID must not be null for response mapping"),
                    providerMatchId = it.providerMatchId,
                    homeTeam = it.homeTeam,
                    awayTeam = it.awayTeam,
                    kickOff = it.kickOff,
                    league = it.league
                )
            },
            // Populated by feed ingestor reconciliation when kick-off times change after a match is added
            changedFixtures = emptyList()
        )
    }
}

data class MatchResponse(
    val id: UUID,
    val providerMatchId: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickOff: Instant,
    val league: String
)