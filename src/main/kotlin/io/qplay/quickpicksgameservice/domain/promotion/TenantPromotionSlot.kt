package io.qplay.quickpicksgameservice.domain.promotion

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

enum class SlotPosition { POST_ENTRY_RECEIPT, HUB_BANNER }
enum class ProductCategory { SPORTS, CASINO, GAMING, OTHER }

@Entity
@Table(name = "tenant_promotion_slots")
class TenantPromotionSlot(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_position", nullable = false)
    val slotPosition: SlotPosition,

    @Enumerated(EnumType.STRING)
    @Column(name = "product_category", nullable = false)
    var productCategory: ProductCategory = ProductCategory.OTHER,

    @Column(nullable = false)
    var title: String,

    @Column
    var subtitle: String? = null,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column(name = "cta_text", nullable = false)
    var ctaText: String,

    @Column(name = "cta_action", nullable = false)
    var ctaAction: String = "NAVIGATE",

    @Column(name = "cta_url", nullable = false)
    var ctaUrl: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(nullable = false)
    var priority: Int = 0,

    @Column(name = "start_at")
    var startAt: OffsetDateTime? = null,

    @Column(name = "end_at")
    var endAt: OffsetDateTime? = null,

    @Column(name = "click_count", nullable = false)
    var clickCount: Long = 0,

    @Column(name = "impression_count", nullable = false)
    var impressionCount: Long = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

@Repository
interface TenantPromotionSlotRepository : JpaRepository<TenantPromotionSlot, UUID> {

    fun findByTenantIdOrderByPriorityDesc(tenantId: String): List<TenantPromotionSlot>

    @Query(
        "SELECT s FROM TenantPromotionSlot s WHERE s.tenantId = :tenantId " +
        "AND s.slotPosition = :position AND s.isActive = true " +
        "AND (s.startAt IS NULL OR s.startAt <= CURRENT_TIMESTAMP) " +
        "AND (s.endAt IS NULL OR s.endAt >= CURRENT_TIMESTAMP) " +
        "ORDER BY s.priority DESC"
    )
    fun findActiveSlots(tenantId: String, position: SlotPosition): List<TenantPromotionSlot>

    @Transactional
    @Modifying
    @Query("UPDATE TenantPromotionSlot s SET s.clickCount = s.clickCount + 1, s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    fun incrementClickCount(id: UUID): Int

    @Transactional
    @Modifying
    @Query("UPDATE TenantPromotionSlot s SET s.impressionCount = s.impressionCount + 1, s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    fun incrementImpressionCount(id: UUID): Int
}
