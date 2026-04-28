package io.qplay.quickpicksgameservice.tenant.persistence

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "tenant_player_access")
class TenantPlayerAccess(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "player_id", nullable = false)
    val playerId: String,

    @Column(name = "access_level", nullable = false)
    val accessLevel: String, // ALLOW, BLOCK

    @Column(name = "source", nullable = false)
    val source: String = "MANUAL", // MANUAL, HOST_FEED

    @Column(name = "updated_by")
    val updatedBy: String? = null,

    @Column(name = "reason")
    val reason: String? = null,

    @Column(name = "synced_at")
    val syncedAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Repository
interface TenantPlayerAccessRepository : JpaRepository<TenantPlayerAccess, UUID> {
    fun findByTenantIdAndPlayerId(tenantId: String, playerId: String): TenantPlayerAccess?
    fun findAllByTenantId(tenantId: String): List<TenantPlayerAccess>
    fun findAllByTenantIdAndSource(tenantId: String, source: String): List<TenantPlayerAccess>
    fun findAllByTenantIdAndAccessLevel(tenantId: String, accessLevel: String): List<TenantPlayerAccess>

    @Modifying
    @Query("DELETE FROM TenantPlayerAccess t WHERE t.tenantId = :tenantId AND t.source = :source")
    fun deleteAllByTenantIdAndSource(tenantId: String, source: String)

    @Modifying
    @Query("DELETE FROM TenantPlayerAccess t WHERE t.tenantId = :tenantId AND t.playerId = :playerId")
    fun deleteByTenantIdAndPlayerId(tenantId: String, playerId: String)
}
