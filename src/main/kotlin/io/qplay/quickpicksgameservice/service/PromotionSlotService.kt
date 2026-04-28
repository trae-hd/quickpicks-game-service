package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.promotion.*
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

data class CreateSlotRequest(
    val slotPosition: SlotPosition,
    val productCategory: ProductCategory,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val ctaText: String,
    val ctaUrl: String,
    val priority: Int = 0,
    val startAt: OffsetDateTime? = null,
    val endAt: OffsetDateTime? = null
)

data class UpdateSlotRequest(
    val productCategory: ProductCategory? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val ctaText: String? = null,
    val ctaUrl: String? = null,
    val isActive: Boolean? = null,
    val priority: Int? = null,
    val startAt: OffsetDateTime? = null,
    val endAt: OffsetDateTime? = null
)

data class SlotAnalytics(
    val slotId: UUID,
    val title: String,
    val totalImpressions: Long,
    val totalClicks: Long,
    val ctr: Double,
    val clicksByRound: List<ClicksByRound>,
    val topClickingPlayers: List<TopClicker>
)

@Service
class PromotionSlotService(
    private val slotRepository: TenantPromotionSlotRepository,
    private val clickRepository: PromotionSlotClickRepository,
    private val tenantRepository: TenantRepository
) {
    fun getActiveSlotForPosition(position: SlotPosition): TenantPromotionSlot? {
        val tenantId = TenantContext.getRequiredTenantId()
        val tenant = requireTenant(tenantId)

        if (tenant.crossSellComplianceMode == "DISABLED") return null

        return slotRepository.findActiveSlots(tenantId, position).firstOrNull()
    }

    @Transactional
    fun recordImpression(slotId: UUID) {
        slotRepository.incrementImpressionCount(slotId)
    }

    @Transactional
    fun recordClick(slotId: UUID, hostPlayerId: String, roundId: UUID?) {
        val tenantId = TenantContext.getRequiredTenantId()
        val slot = slotRepository.findById(slotId)
            .orElseThrow { IllegalArgumentException("Promotion slot not found") }
        require(slot.tenantId == tenantId) { "Promotion slot not found" }

        clickRepository.save(
            PromotionSlotClick(
                slotId = slotId,
                tenantId = tenantId,
                roundId = roundId,
                hostPlayerId = hostPlayerId
            )
        )
        slotRepository.incrementClickCount(slotId)
    }

    @Transactional
    fun createSlot(request: CreateSlotRequest): TenantPromotionSlot {
        val tenantId = TenantContext.getRequiredTenantId()
        val tenant = requireTenant(tenantId)
        validateCompliance(tenant, request.productCategory)

        return slotRepository.save(
            TenantPromotionSlot(
                tenantId = tenantId,
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
    }

    @Transactional
    fun updateSlot(slotId: UUID, request: UpdateSlotRequest): TenantPromotionSlot {
        val tenantId = TenantContext.getRequiredTenantId()
        val tenant = requireTenant(tenantId)
        val slot = requireSlot(slotId, tenantId)

        request.productCategory?.let {
            validateCompliance(tenant, it)
            slot.productCategory = it
        }
        request.title?.let { slot.title = it }
        request.subtitle?.let { slot.subtitle = it }
        request.imageUrl?.let { slot.imageUrl = it }
        request.ctaText?.let { slot.ctaText = it }
        request.ctaUrl?.let { slot.ctaUrl = it }
        request.isActive?.let { slot.isActive = it }
        request.priority?.let { slot.priority = it }
        request.startAt?.let { slot.startAt = it }
        request.endAt?.let { slot.endAt = it }
        slot.updatedAt = OffsetDateTime.now()

        return slotRepository.save(slot)
    }

    @Transactional
    fun deactivateSlot(slotId: UUID) {
        val tenantId = TenantContext.getRequiredTenantId()
        val slot = requireSlot(slotId, tenantId)
        slot.isActive = false
        slot.updatedAt = OffsetDateTime.now()
        slotRepository.save(slot)
    }

    fun listSlots(): List<TenantPromotionSlot> {
        val tenantId = TenantContext.getRequiredTenantId()
        return slotRepository.findByTenantIdOrderByPriorityDesc(tenantId)
    }

    fun getSlotAnalytics(slotId: UUID): SlotAnalytics {
        val tenantId = TenantContext.getRequiredTenantId()
        val slot = requireSlot(slotId, tenantId)

        val ctr = if (slot.impressionCount > 0) {
            (slot.clickCount.toDouble() / slot.impressionCount.toDouble()) * 100.0
        } else 0.0

        return SlotAnalytics(
            slotId = slot.id,
            title = slot.title,
            totalImpressions = slot.impressionCount,
            totalClicks = slot.clickCount,
            ctr = Math.round(ctr * 10.0) / 10.0,
            clicksByRound = clickRepository.clicksByRound(slotId),
            topClickingPlayers = clickRepository.topClickingPlayers(slotId).take(20)
        )
    }

    private fun validateCompliance(tenant: Tenant, category: ProductCategory) {
        if (tenant.crossSellComplianceMode == "DISABLED") {
            throw IllegalArgumentException("Promotion slots are disabled for this tenant.")
        }
        if (tenant.crossSellComplianceMode == "UK_STRICT" && category in listOf(ProductCategory.CASINO, ProductCategory.GAMING)) {
            throw IllegalArgumentException(
                "UK regulations restrict cross-selling ${category.name} products from within a gaming product. " +
                "Use SPORTS or OTHER category."
            )
        }
    }

    private fun requireTenant(tenantId: String): Tenant =
        tenantRepository.findById(tenantId).orElseThrow { IllegalStateException("Tenant not found: $tenantId") }

    private fun requireSlot(slotId: UUID, tenantId: String): TenantPromotionSlot {
        val slot = slotRepository.findById(slotId).orElseThrow { IllegalArgumentException("Promotion slot not found") }
        require(slot.tenantId == tenantId) { "Promotion slot not found" }
        return slot
    }
}
