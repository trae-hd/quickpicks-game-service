package io.qplay.quickpicksgameservice.domain.round

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.*

interface RoundRepository : JpaRepository<Round, UUID> {
    fun findBySlateId(slateId: UUID): Optional<Round>
    fun findByStatus(status: RoundStatus): List<Round>
    fun findByStatusIn(statuses: List<RoundStatus>): List<Round>
    fun findByTenantIdAndStatusOrderBySettledAtDesc(tenantId: String, status: RoundStatus): List<Round>

    @Query("SELECT r FROM Round r JOIN r.slate s WHERE r.status = 'OPEN' AND s.roundWindowEnd < :now")
    fun findOpenRoundsPassedWindow(now: Instant): List<Round>
}
