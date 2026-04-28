package io.qplay.quickpicksgameservice.tenant.persistence

import io.qplay.quickpicksgameservice.tenant.TenantContext
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "player_exclusions")
class PlayerExclusion(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String = TenantContext.getTenantId() ?: "SYSTEM",

    @Column(name = "player_id", nullable = false)
    val playerId: String,

    @Column(name = "reason")
    val reason: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Repository
interface PlayerExclusionRepository : JpaRepository<PlayerExclusion, UUID> {
    fun findByTenantIdAndPlayerId(tenantId: String, playerId: String): PlayerExclusion?
}
