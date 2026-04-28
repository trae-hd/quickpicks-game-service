package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.PlayerJwtClaims
import io.qplay.quickpicksgameservice.service.AnalyticsService
import io.qplay.quickpicksgameservice.service.RoundAnalytics
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Profile("api")
@Tag(name = "Analytics", description = "Round analytics endpoints")
class AnalyticsController(
    private val analyticsService: AnalyticsService
) {
    @PostMapping("/api/v1/analytics/impression")
    @Operation(summary = "Record a player impression on the post-entry receipt screen. Fire-and-forget — errors are swallowed.")
    fun recordImpression(
        @RequestBody request: ImpressionRequest,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ResponseEntity<Void> {
        try {
            analyticsService.recordImpression(
                roundId = request.roundId,
                hostPlayerId = claims.playerId,
                impressionType = request.impressionType ?: "POST_ENTRY_RECEIPT"
            )
        } catch (_: Exception) {
            // Impression tracking must never block the player flow
        }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/v1/admin/rounds/{roundId}/analytics")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Get full analytics for a round: entries, revenue, pools, settlement, cross-sell CTR, funnel, and jackpot rollover streak")
    fun getRoundAnalytics(@PathVariable roundId: UUID): ApiResponse<RoundAnalytics> {
        return ApiResponse(analyticsService.getRoundAnalytics(roundId))
    }
}

data class ImpressionRequest @JsonCreator constructor(
    @JsonProperty("roundId") val roundId: UUID,
    @JsonProperty("impressionType") val impressionType: String? = null
)
