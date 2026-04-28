package io.qplay.quickpicksgameservice.service.trending

import com.fasterxml.jackson.databind.ObjectMapper
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.trending.RedisTrendingStore
import io.qplay.quickpicksgameservice.domain.trending.TrendingSnapshot
import io.qplay.quickpicksgameservice.domain.trending.TrendingSnapshotRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class TrendingApplicationService(
    private val roundRepository: RoundRepository,
    private val trendingStore: RedisTrendingStore,
    private val snapshotRepository: TrendingSnapshotRepository,
    private val objectMapper: ObjectMapper
) {
    @Scheduled(fixedRateString = "\${app.trending.flush-interval:PT5M}")
    @SchedulerLock(name = "trendingFlush", lockAtLeastFor = "PT1M", lockAtMostFor = "PT4M")
    @Transactional
    fun flushTrendingToDb() {
        val activeRounds = roundRepository.findAll().filter { it.status == RoundStatus.OPEN || it.status == RoundStatus.LOCKED }
        
        activeRounds.forEach { round ->
            val data = trendingStore.getTrending(round.id.toString())
            if (data.isNotEmpty()) {
                val snapshot = TrendingSnapshot(
                    roundId = round.id ?: throw IllegalStateException("Round ID must not be null for trending flush"),
                    tenantId = round.tenantId,
                    snapshotData = objectMapper.writeValueAsString(data),
                    isFrozen = round.status == RoundStatus.LOCKED // T-60 freeze logic: if LOCKED, it's frozen
                )
                snapshotRepository.save(snapshot)
            }
        }
    }

    fun getTrending(roundId: java.util.UUID, tenantId: String): Map<String, Map<String, Int>> {
        val round = roundRepository.findById(roundId).orElse(null) ?: return emptyMap()
        
        // If round is LOCKED, always serve from the latest frozen snapshot
        if (round.status == RoundStatus.LOCKED || round.status == RoundStatus.SETTLED) {
            val latestSnapshot = snapshotRepository.findFirstByRoundIdAndTenantIdOrderByCreatedAtDesc(roundId, tenantId)
            return if (latestSnapshot != null) {
                objectMapper.readValue(latestSnapshot.snapshotData, Map::class.java) as Map<String, Map<String, Int>>
            } else {
                emptyMap()
            }
        }
        
        // Otherwise serve live from Redis
        return trendingStore.getTrending(roundId.toString())
    }
}
