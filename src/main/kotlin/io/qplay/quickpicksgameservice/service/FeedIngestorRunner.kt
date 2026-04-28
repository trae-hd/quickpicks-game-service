package io.qplay.quickpicksgameservice.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("worker-feed")
class FeedIngestorRunner(
    private val resultingService: ResultingService
) {
    @Scheduled(fixedDelayString = "\${app.scheduling.feed-poll-delay-ms:30000}")
    @SchedulerLock(name = "feed-ingestor-poll", lockAtMostFor = "55s", lockAtLeastFor = "5s")
    fun poll() {
        resultingService.pollResultsForActiveRounds()
    }
}