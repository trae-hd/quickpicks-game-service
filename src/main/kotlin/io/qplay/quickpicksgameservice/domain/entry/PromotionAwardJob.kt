package io.qplay.quickpicksgameservice.domain.entry

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

enum class JobStatus { PROCESSING, COMPLETED, FAILED }

@Entity
@Table(name = "promotion_award_jobs")
class PromotionAwardJob(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "campaign_id")
    val campaignId: String? = null,

    @Column(name = "total_requested", nullable = false)
    val totalRequested: Int,

    @Column(name = "total_processed", nullable = false)
    var totalProcessed: Int = 0,

    @Column(name = "total_awarded", nullable = false)
    var totalAwarded: Int = 0,

    @Column(name = "total_skipped", nullable = false)
    var totalSkipped: Int = 0,

    @Column(name = "total_failed", nullable = false)
    var totalFailed: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobStatus = JobStatus.PROCESSING,

    @Column(name = "created_by", nullable = false)
    val createdBy: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null
)

@Repository
interface PromotionAwardJobRepository : JpaRepository<PromotionAwardJob, UUID> {
    fun findByTenantIdOrderByCreatedAtDesc(tenantId: String): List<PromotionAwardJob>
}
