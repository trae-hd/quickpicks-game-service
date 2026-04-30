package io.qplay.quickpicksgameservice.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("worker-feed")
class FixtureCacheSyncRunner(
    private val fixtureCacheSyncService: FixtureCacheSyncService
) {
    @Scheduled(cron = "0 0 6 * * *")
    @SchedulerLock(name = "fixture-cache-sync", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    fun sync() {
        fixtureCacheSyncService.sync()
    }
}