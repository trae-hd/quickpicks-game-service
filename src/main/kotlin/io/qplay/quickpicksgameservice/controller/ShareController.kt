package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.entry.EntryRepository
import io.qplay.quickpicksgameservice.domain.share.ShareLink
import io.qplay.quickpicksgameservice.domain.share.ShareLinkRepository
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.idempotency.Idempotent
import io.qplay.quickpicksgameservice.ratelimit.RateLimited
import io.qplay.quickpicksgameservice.security.PlayerJwtClaims
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/share")
@Tag(name = "Share Links", description = "Endpoints for generating and resolving entry share links")
class ShareController(
    private val shareLinkRepository: ShareLinkRepository,
    private val entryRepository: EntryRepository
) {
    private val secureRandom = SecureRandom()
    private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // Avoid ambiguous chars

    @PostMapping("/{entryId}")
    @Idempotent
    @RateLimited
    @Operation(summary = "Generate a short-token share link for an entry")
    fun generateShareLink(
        @PathVariable entryId: UUID,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ApiResponse<ShareResponse> {
        if (claims.preview) {
            throw IllegalArgumentException("Share link generation is disabled in preview mode")
        }
        val entry = entryRepository.findById(entryId)
            .orElseThrow { IllegalArgumentException("Entry not found") }

        if (entry.playerId != TenantContext.getPlayerId()) {
            throw IllegalArgumentException("Not authorized to share this entry")
        }

        val token = (1..8).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("")
        
        val shareLink = ShareLink(
            tenantId = TenantContext.getRequiredTenantId(),
            playerId = TenantContext.getRequiredPlayerId(),
            entryId = entry.id ?: throw IllegalStateException("Entry ID must not be null for sharing"),
            token = token,
            picksJson = entry.picks,
            expiresAt = OffsetDateTime.now().plusDays(30)
        )

        shareLinkRepository.save(shareLink)

        return ApiResponse(ShareResponse(token, shareLink.expiresAt))
    }

    @GetMapping("/resolve/{token}")
    @Operation(summary = "Resolve a share token into entry picks")
    fun resolveShareLink(@PathVariable token: String): ApiResponse<ResolvedShareResponse> {
        val shareLink = shareLinkRepository.findByToken(token)
            ?: throw IllegalArgumentException("Invalid or expired share token")

        if (shareLink.expiresAt.isBefore(OffsetDateTime.now())) {
            throw IllegalArgumentException("Share link has expired")
        }

        return ApiResponse(ResolvedShareResponse(
            picks = shareLink.picksJson.picks,
            originalPlayerId = shareLink.playerId
        ))
    }
}

data class ShareResponse(val token: String, val expiresAt: OffsetDateTime)
data class ResolvedShareResponse(val picks: Any, val originalPlayerId: String)
