package io.qplay.quickpicksgameservice.domain.round

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "round_change_alerts")
class RoundChangeAlert(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "round_id", nullable = false)
    val roundId: UUID,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "alert_type", nullable = false)
    val alertType: String,

    @Column(nullable = false)
    val message: String,

    @Column(nullable = false)
    val severity: String = "MEDIUM",

    @Column(name = "is_resolved", nullable = false)
    var isResolved: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Repository
interface RoundChangeAlertRepository : JpaRepository<RoundChangeAlert, UUID> {
    fun findAllByTenantIdAndIsResolvedFalse(tenantId: String): List<RoundChangeAlert>
}
