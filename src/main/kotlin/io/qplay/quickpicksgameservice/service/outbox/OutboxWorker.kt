package io.qplay.quickpicksgameservice.service.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.qplay.quickpicksgameservice.domain.outbox.OutboxEvent
import io.qplay.quickpicksgameservice.domain.outbox.OutboxRepository
import io.qplay.quickpicksgameservice.domain.sportsfeed.FeedProviderRepository
import io.qplay.quickpicksgameservice.infra.optimove.OptimoveStreamsClient
import io.qplay.quickpicksgameservice.infra.optimove.OptimoveEvent
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Service
@Profile("worker-outbox")
class OutboxWorker(
    private val outboxRepository: OutboxRepository,
    private val optimoveStreamsClient: OptimoveStreamsClient,
    private val transactionTemplate: TransactionTemplate,
    private val feedProviderRepository: FeedProviderRepository,
    private val objectMapper: ObjectMapper,
    private val lockExecutor: LockingTaskExecutor
) {
    private val logger = LoggerFactory.getLogger(OutboxWorker::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    @PostConstruct
    fun start() {
        // Section 17: Load polling cadence from database
        val provider = feedProviderRepository.findById("api-football").orElse(null)
        val intervalMs = if (provider != null) {
            try {
                val json = objectMapper.readTree(provider.pollingIntervalsJson)
                json.get("outbox_worker_ms")?.asLong() ?: 2000L
            } catch (e: Exception) {
                2000L
            }
        } else {
            2000L
        }

        scheduler.scheduleWithFixedDelay({
            // ShedLock manually executed because we are using a custom scheduler
            val lockConfig = LockConfiguration(Instant.now(), "outbox_processor", Duration.ofMinutes(1), Duration.ofSeconds(1))
            lockExecutor.executeWithLock(Runnable { processOutbox() }, lockConfig)
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
        
        logger.info("OutboxWorker started with interval ${intervalMs}ms")
    }

    @PreDestroy
    fun stop() {
        scheduler.shutdown()
    }

    fun processOutbox() {
        // Use transactionTemplate to ensure the SELECT FOR UPDATE SKIP LOCKED is in a transaction
        transactionTemplate.execute {
            val events = outboxRepository.findUnsent(100)
            if (events.isEmpty()) return@execute

            val eventsByTenant = events.groupBy { it.tenantId }

            eventsByTenant.forEach { (tenantId, tenantEvents) ->
                try {
                    val optimoveBatch = tenantEvents.map { it.toOptimoveEvent() }
                    optimoveStreamsClient.publishBatch(tenantId, optimoveBatch)
                    tenantEvents.forEach { it.sentAt = OffsetDateTime.now() }
                } catch (e: Exception) {
                    logger.error("Failed to publish batch for tenant $tenantId: ${e.message}")
                    tenantEvents.forEach { it.errorLog = e.message }
                }
            }
            outboxRepository.saveAll(events)
        }
    }

    private fun OutboxEvent.toOptimoveEvent() = OptimoveEvent(
        eventId = id.toString(),
        eventName = eventType,
        eventTimestamp = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        playerId = playerId ?: "SYSTEM",
        tenantId = tenantId,
        eventProperties = payload
    )
}
