package io.qplay.quickpicksgameservice.domain.trending

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "trending_snapshots")
class TrendingSnapshot(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "round_id", nullable = false)
    val roundId: UUID,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "snapshot_data", nullable = false)
    val snapshotData: String, // JSON string

    @Column(name = "is_frozen", nullable = false)
    var isFrozen: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

interface TrendingSnapshotRepository : JpaRepository<TrendingSnapshot, UUID> {
    fun findFirstByRoundIdAndTenantIdOrderByCreatedAtDesc(roundId: UUID, tenantId: String): TrendingSnapshot?
}
