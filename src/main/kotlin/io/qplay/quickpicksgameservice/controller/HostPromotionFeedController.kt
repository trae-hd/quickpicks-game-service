package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.HostFeedPrincipal
import io.qplay.quickpicksgameservice.service.AwardRequest
import io.qplay.quickpicksgameservice.service.AwardResult
import io.qplay.quickpicksgameservice.service.BulkAwardStats
import io.qplay.quickpicksgameservice.service.PromotionAwardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@Profile("api")
@RequestMapping("/api/v1/host/promotions")
@Tag(name = "Host Feed - Promotions", description = "Host casino endpoints for programmatic free entry awards (HMAC-signed)")
class HostPromotionFeedController(
    private val promotionAwardService: PromotionAwardService
) {
    @PostMapping("/award")
    @Operation(summary = "Award a single free entry from the host casino's CRM system")
    fun awardSingle(
        @RequestBody request: HostSingleAwardRequest,
        @AuthenticationPrincipal principal: HostFeedPrincipal
    ): ApiResponse<AwardResult> {
        val result = promotionAwardService.awardToPlayer(
            AwardRequest(
                hostPlayerId = request.hostPlayerId,
                reason = request.reason,
                targetRound = request.targetRound,
                expiresAt = request.expiresAt,
                campaignId = request.campaignId,
                awardedBy = "host-feed"
            )
        )
        return ApiResponse(result)
    }

    @PostMapping("/award/bulk")
    @Operation(summary = "Award free entries to a batch of players (max 1,000 per request). Returns a jobId immediately.")
    fun awardBulk(
        @RequestBody request: HostBulkAwardRequest,
        @AuthenticationPrincipal principal: HostFeedPrincipal
    ): ApiResponse<BulkAwardStats> {
        val awardRequests = request.players.map { player ->
            AwardRequest(
                hostPlayerId = player.hostPlayerId,
                reason = player.reason,
                targetRound = player.targetRound,
                expiresAt = player.expiresAt,
                campaignId = request.campaignId,
                awardedBy = "host-feed"
            )
        }
        val stats = promotionAwardService.awardBulkFromFeed(awardRequests, request.campaignId, "host-feed")
        return ApiResponse(stats)
    }
}

data class HostSingleAwardRequest @JsonCreator constructor(
    @JsonProperty("hostPlayerId") val hostPlayerId: String,
    @JsonProperty("reason") val reason: String,
    @JsonProperty("targetRound") val targetRound: String? = null,
    @JsonProperty("expiresAt") val expiresAt: OffsetDateTime? = null,
    @JsonProperty("campaignId") val campaignId: String? = null
)

data class HostBulkAwardRequest @JsonCreator constructor(
    @JsonProperty("players") val players: List<HostBulkAwardPlayer>,
    @JsonProperty("campaignId") val campaignId: String? = null
)

data class HostBulkAwardPlayer @JsonCreator constructor(
    @JsonProperty("hostPlayerId") val hostPlayerId: String,
    @JsonProperty("reason") val reason: String,
    @JsonProperty("targetRound") val targetRound: String? = null,
    @JsonProperty("expiresAt") val expiresAt: OffsetDateTime? = null
)
