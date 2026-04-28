package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.promotion.ProductCategory
import io.qplay.quickpicksgameservice.domain.promotion.SlotPosition
import io.qplay.quickpicksgameservice.domain.promotion.TenantPromotionSlot
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.PlayerJwtClaims
import io.qplay.quickpicksgameservice.service.PromotionSlotService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/promotions/slots")
@Tag(name = "Promotion Slots", description = "Player endpoints for cross-sell promotion slots")
class PromotionSlotController(
    private val promotionSlotService: PromotionSlotService
) {
    @GetMapping
    @Operation(summary = "Get the active promotion slot for a given position. Returns 204 if none is configured or tenant has slots disabled.")
    fun getActiveSlot(
        @RequestParam position: SlotPosition,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ResponseEntity<ApiResponse<SlotResponse>> {
        // Self-excluded players must not see any promotion slot
        if (claims.rgFlags.contains("self_excluded")) {
            return ResponseEntity.noContent().build()
        }

        val slot = promotionSlotService.getActiveSlotForPosition(position)
            ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(ApiResponse(SlotResponse.fromDomain(slot)))
    }

    @PostMapping("/{slotId}/impression")
    @Operation(summary = "Record a slot impression. Fire-and-forget — errors are swallowed.")
    fun recordImpression(
        @PathVariable slotId: UUID,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ResponseEntity<Void> {
        try {
            promotionSlotService.recordImpression(slotId)
        } catch (_: Exception) {
            // Impression tracking must never block the player flow
        }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{slotId}/click")
    @Operation(summary = "Record a CTA click on a promotion slot")
    fun recordClick(
        @PathVariable slotId: UUID,
        @RequestParam(required = false) roundId: UUID?,
        @AuthenticationPrincipal claims: PlayerJwtClaims
    ): ResponseEntity<Void> {
        promotionSlotService.recordClick(slotId, claims.playerId, roundId)
        return ResponseEntity.noContent().build()
    }
}

data class SlotResponse(
    val id: UUID,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val ctaText: String,
    val ctaAction: String,
    val ctaUrl: String,
    val productCategory: ProductCategory
) {
    companion object {
        fun fromDomain(slot: TenantPromotionSlot) = SlotResponse(
            id = slot.id,
            title = slot.title,
            subtitle = slot.subtitle,
            imageUrl = slot.imageUrl,
            ctaText = slot.ctaText,
            ctaAction = slot.ctaAction,
            ctaUrl = slot.ctaUrl,
            productCategory = slot.productCategory
        )
    }
}
