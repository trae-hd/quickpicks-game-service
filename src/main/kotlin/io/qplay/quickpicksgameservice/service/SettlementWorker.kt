package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.service.ResultingService
import io.qplay.quickpicksgameservice.service.SettlementService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("worker-settlement")
class SettlementWorker(
    private val resultingService: ResultingService,
    private val settlementService: SettlementService,
    private val firstEntryFreeService: FirstEntryFreeService,
    private val jdbcTemplate: JdbcTemplate
) {
    @Scheduled(fixedDelayString = "\${app.scheduling.resulting-delay-ms:60000}")
    @SchedulerLock(name = "resulting-poll", lockAtMostFor = "55s", lockAtLeastFor = "10s")
    fun pollResults() {
        resultingService.pollResultsForActiveRounds()
    }

    @Scheduled(fixedDelayString = "\${app.scheduling.settlement-delay-ms:60000}")
    @SchedulerLock(name = "settlement-process", lockAtMostFor = "55s", lockAtLeastFor = "10s")
    fun processSettlement() {
        settlementService.processSettlement()
    }

    @Scheduled(fixedDelayString = "\${app.scheduling.promo-expiry-delay-ms:3600000}") // Every hour
    @SchedulerLock(name = "promo-expiry", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    fun expirePromotions() {
        firstEntryFreeService.expirePromotions()
    }

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    @SchedulerLock(name = "raw-responses-cleanup", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    fun cleanupRawResponses() {
        jdbcTemplate.execute("DELETE FROM feed_raw_responses WHERE fetched_at < now() - INTERVAL '30 days'")
    }
}
