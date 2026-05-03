package io.qplay.quickpicksgameservice.domain.slate

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SlateRepository : JpaRepository<Slate, UUID> {
    fun findByTenantIdAndStatusNot(tenantId: String, status: SlateStatus): List<Slate>
    fun findByTenantIdAndStatus(tenantId: String, status: SlateStatus): List<Slate>
}

interface MatchRepository : JpaRepository<Match, UUID> {
    fun findBySlateId(slateId: UUID): List<Match>
    fun findByProviderMatchId(providerMatchId: String): Match?
}
