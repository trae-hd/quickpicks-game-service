package io.qplay.quickpicksgameservice.domain.round

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface RoundRepository : JpaRepository<Round, UUID> {
    fun findBySlateId(slateId: UUID): Optional<Round>
    fun findByStatus(status: RoundStatus): List<Round>
    fun findByTenantIdAndStatusOrderBySettledAtDesc(tenantId: String, status: RoundStatus): List<Round>
}
