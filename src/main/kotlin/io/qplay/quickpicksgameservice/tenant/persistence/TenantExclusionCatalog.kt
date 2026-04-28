package io.qplay.quickpicksgameservice.tenant.persistence

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "tenant_exclusion_catalog")
class TenantExclusionCatalog(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "flag_name", nullable = false)
    val flagName: String,

    @Column
    val description: String? = null,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

@Repository
interface TenantExclusionCatalogRepository : JpaRepository<TenantExclusionCatalog, UUID> {
    fun findAllByTenantIdAndIsActive(tenantId: String, isActive: Boolean): List<TenantExclusionCatalog>
}
