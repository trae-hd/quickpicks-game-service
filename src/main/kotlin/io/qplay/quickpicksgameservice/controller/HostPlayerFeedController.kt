package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.HostFeedPrincipal
import io.qplay.quickpicksgameservice.service.*
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@Profile("api")
@RequestMapping("/api/v1/host/players")
@Tag(name = "Host Feed - Players", description = "Host casino endpoints for player access and exclusion management (HMAC-signed)")
class HostPlayerFeedController(
    private val hostPlayerFeedService: HostPlayerFeedService,
    private val tenantRepository: TenantRepository
) {
    @PostMapping("/access/sync")
    @Operation(summary = "Bulk sync allowlist/blocklist from host casino")
    fun syncAccess(
        @RequestBody request: BulkSyncRequest,
        @AuthenticationPrincipal principal: HostFeedPrincipal
    ): ApiResponse<SyncStats> {
        val tenant = tenantRepository.findById(principal.tenantId).orElseThrow()
        val stats = hostPlayerFeedService.syncPlayers(
            tenant,
            SyncMode.valueOf(request.syncMode),
            request.accessMode,
            request.players.map { PlayerSyncEntry(it.hostPlayerId, it.action) }
        )
        return ApiResponse(stats)
    }

    @PostMapping("/access/update")
    @Operation(summary = "Single player access update from host casino")
    fun updateAccess(
        @RequestBody request: SingleUpdateRequest,
        @AuthenticationPrincipal principal: HostFeedPrincipal
    ): ApiResponse<SingleUpdateResult> {
        val tenant = tenantRepository.findById(principal.tenantId).orElseThrow()
        val result = hostPlayerFeedService.updateSinglePlayer(
            tenant,
            request.hostPlayerId,
            request.action,
            request.accessMode,
            request.reason
        )
        return ApiResponse(result)
    }

    @PostMapping("/exclusions/notify")
    @Operation(summary = "Exclusion event notification from host casino")
    fun notifyExclusion(
        @RequestBody request: ExclusionNotifyRequest,
        @AuthenticationPrincipal principal: HostFeedPrincipal
    ): ApiResponse<ExclusionNotifyResult> {
        val tenant = tenantRepository.findById(principal.tenantId).orElseThrow()
        val result = hostPlayerFeedService.notifyExclusion(
            tenant,
            request.hostPlayerId,
            request.exclusionType,
            request.effectiveAt,
            request.reason
        )
        return ApiResponse(result)
    }

    @GetMapping("/access/status")
    @Operation(summary = "Check a player's current access status")
    fun getStatus(
        @RequestParam hostPlayerId: String,
        @AuthenticationPrincipal principal: HostFeedPrincipal
    ): ApiResponse<PlayerAccessStatus> {
        val tenant = tenantRepository.findById(principal.tenantId).orElseThrow()
        val status = hostPlayerFeedService.getPlayerStatus(tenant, hostPlayerId)
        return ApiResponse(status)
    }
}

data class BulkSyncRequest @JsonCreator constructor(
    @JsonProperty("syncMode") val syncMode: String,
    @JsonProperty("accessMode") val accessMode: String,
    @JsonProperty("players") val players: List<BulkSyncPlayer>
)

data class BulkSyncPlayer @JsonCreator constructor(
    @JsonProperty("hostPlayerId") val hostPlayerId: String,
    @JsonProperty("action") val action: String
)

data class SingleUpdateRequest @JsonCreator constructor(
    @JsonProperty("hostPlayerId") val hostPlayerId: String,
    @JsonProperty("action") val action: String,
    @JsonProperty("accessMode") val accessMode: String,
    @JsonProperty("reason") val reason: String? = null
)

data class ExclusionNotifyRequest @JsonCreator constructor(
    @JsonProperty("hostPlayerId") val hostPlayerId: String,
    @JsonProperty("exclusionType") val exclusionType: String,
    @JsonProperty("effectiveAt") val effectiveAt: OffsetDateTime,
    @JsonProperty("reason") val reason: String? = null
)