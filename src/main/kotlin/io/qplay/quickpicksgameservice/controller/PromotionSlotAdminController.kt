package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.domain.promotion.ProductCategory
import io.qplay.quickpicksgameservice.domain.promotion.SlotPosition
import io.qplay.quickpicksgameservice.domain.promotion.TenantPromotionSlot
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.service.CreateSlotRequest
import io.qplay.quickpicksgameservice.service.PromotionSlotService
import io.qplay.quickpicksgameservice.service.SlotAnalytics
import io.qplay.quickpicksgameservice.service.UpdateSlotRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/promotions/slots")
@Tag(name = "Admin - Promotion Slots", description = "Operator endpoints for cross-sell slot configuration")
class PromotionSlotAdminController(
    private val promotionSlotService: PromotionSlotService
) {
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "List all promotion slots for the tenant")
    fun listSlots(): ApiResponse<List<AdminSlotResponse>> {
        val slots = promotionSlotService.listSlots().map { AdminSlotResponse.fromDomain(it) }
        return ApiResponse(slots)
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Create a new promotion slot. UK_STRICT tenants cannot use CASINO or GAMING categories.")
    fun createSlot(@RequestBody request: CreateSlotHttpRequest): ApiResponse<AdminSlotResponse> {
        val slot = promotionSlotService.createSlot(
            CreateSlotRequest(
                slotPosition = request.slotPosition,
                productCategory = request.productCategory,
                title = request.title,
                subtitle = request.subtitle,
                imageUrl = request.imageUrl,
                ctaText = request.ctaText,
                ctaUrl = request.ctaUrl,
                priority = request.priority,
                startAt = request.startAt,
                endAt = request.endAt
            )
        )
        return ApiResponse(AdminSlotResponse.fromDomain(slot))
    }

    @PutMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Update an existing promotion slot")
    fun updateSlot(
        @PathVariable slotId: UUID,
        @RequestBody request: UpdateSlotHttpRequest
    ): ApiResponse<AdminSlotResponse> {
        val slot = promotionSlotService.updateSlot(
            slotId,
            UpdateSlotRequest(
                productCategory = request.productCategory,
                title = request.title,
                subtitle = request.subtitle,
                imageUrl = request.imageUrl,
                ctaText = request.ctaText,
                ctaUrl = request.ctaUrl,
                isActive = request.isActive,
                priority = request.priority,
                startAt = request.startAt,
                endAt = request.endAt
            )
        )
        return ApiResponse(AdminSlotResponse.fromDomain(slot))
    }

    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Deactivate a promotion slot (soft delete)")
    fun deactivateSlot(@PathVariable slotId: UUID): ResponseEntity<Void> {
        promotionSlotService.deactivateSlot(slotId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{slotId}/analytics")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Get click and impression analytics for a slot")
    fun getSlotAnalytics(@PathVariable slotId: UUID): ApiResponse<SlotAnalytics> {
        return ApiResponse(promotionSlotService.getSlotAnalytics(slotId))
    }
}

data class CreateSlotHttpRequest @JsonCreator constructor(
    @JsonProperty("slotPosition") val slotPosition: SlotPosition,
    @JsonProperty("productCategory") val productCategory: ProductCategory,
    @JsonProperty("title") val title: String,
    @JsonProperty("subtitle") val subtitle: String? = null,
    @JsonProperty("imageUrl") val imageUrl: String? = null,
    @JsonProperty("ctaText") val ctaText: String,
    @JsonProperty("ctaUrl") val ctaUrl: String,
    @JsonProperty("priority") val priority: Int = 0,
    @JsonProperty("startAt") val startAt: OffsetDateTime? = null,
    @JsonProperty("endAt") val endAt: OffsetDateTime? = null
)

data class UpdateSlotHttpRequest @JsonCreator constructor(
    @JsonProperty("productCategory") val productCategory: ProductCategory? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("subtitle") val subtitle: String? = null,
    @JsonProperty("imageUrl") val imageUrl: String? = null,
    @JsonProperty("ctaText") val ctaText: String? = null,
    @JsonProperty("ctaUrl") val ctaUrl: String? = null,
    @JsonProperty("isActive") val isActive: Boolean? = null,
    @JsonProperty("priority") val priority: Int? = null,
    @JsonProperty("startAt") val startAt: OffsetDateTime? = null,
    @JsonProperty("endAt") val endAt: OffsetDateTime? = null
)

data class AdminSlotResponse(
    val id: UUID,
    val slotPosition: SlotPosition,
    val productCategory: ProductCategory,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val ctaText: String,
    val ctaAction: String,
    val ctaUrl: String,
    val isActive: Boolean,
    val priority: Int,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val clickCount: Long,
    val impressionCount: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
) {
    companion object {
        fun fromDomain(s: TenantPromotionSlot) = AdminSlotResponse(
            id = s.id,
            slotPosition = s.slotPosition,
            productCategory = s.productCategory,
            title = s.title,
            subtitle = s.subtitle,
            imageUrl = s.imageUrl,
            ctaText = s.ctaText,
            ctaAction = s.ctaAction,
            ctaUrl = s.ctaUrl,
            isActive = s.isActive,
            priority = s.priority,
            startAt = s.startAt,
            endAt = s.endAt,
            clickCount = s.clickCount,
            impressionCount = s.impressionCount,
            createdAt = s.createdAt,
            updatedAt = s.updatedAt
        )
    }
}
