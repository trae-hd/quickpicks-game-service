package io.qplay.quickpicksgameservice.domain.player

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "player_exclusion_events")
class PlayerExclusionEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "host_player_id", nullable = false)
    val hostPlayerId: String,

    @Column(name = "exclusion_type", nullable = false)
    val exclusionType: String,

    @Column(name = "effective_at", nullable = false)
    val effectiveAt: OffsetDateTime,

    @Column(name = "reason")
    val reason: String? = null,

    @Column(name = "entries_flagged", nullable = false)
    val entriesFlagged: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Repository
interface PlayerExclusionEventRepository : JpaRepository<PlayerExclusionEvent, UUID> {
    fun findAllByTenantIdOrderByCreatedAtDesc(tenantId: String): List<PlayerExclusionEvent>
    fun findByTenantIdAndHostPlayerId(tenantId: String, hostPlayerId: String): List<PlayerExclusionEvent>
}