package io.qplay.quickpicksgameservice.tenant.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TenantRepository : JpaRepository<Tenant, String>
