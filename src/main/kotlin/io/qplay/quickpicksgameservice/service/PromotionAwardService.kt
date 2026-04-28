package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.*
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.service.outbox.OutboxService
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.*

data class AwardRequest(
    val hostPlayerId: String,
    val reason: String,
    val targetRound: String? = null,      // CURRENT_ROUND | NEXT_ROUND | UUID
    val expiresAt: OffsetDateTime? = null,
    val campaignId: String? = null,
    val awardedBy: String
)

data class AwardResult(
    val promotionId: UUID,
    val hostPlayerId: String,
    val roundId: UUID?,
    val awardedAt: OffsetDateTime
)

data class BulkAwardStats(
    val jobId: UUID,
    val totalRequested: Int,
    val totalAwarded: Int,
    val totalSkipped: Int,
    val totalFailed: Int,
    val campaignId: String?,
    val completedAt: OffsetDateTime
)

@Service
class PromotionAwardService(
    private val promotionRepository: PlayerPromotionRepository,
    private val jobRepository: PromotionAwardJobRepository,
    private val tenantRepository: TenantRepository,
    private val roundRepository: RoundRepository,
    private val outboxService: OutboxService
) {
    private val logger = LoggerFactory.getLogger(PromotionAwardService::class.java)

    @Transactional
    fun awardToPlayer(request: AwardRequest): AwardResult {
        val tenantId = TenantContext.getRequiredTenantId()
        val tenant = requireTenant(tenantId)

        validateAwardCap(tenantId, request.hostPlayerId, tenant)

        val roundId = resolveTargetRound(tenantId, request.targetRound)
        val expiresAt = request.expiresAt ?: roundId?.let { resolveRoundExpiry(it) }

        val promo = promotionRepository.save(
            PlayerPromotion(
                tenantId = tenantId,
                playerId = request.hostPlayerId,
                type = "FREE_ENTRY",
                promotionType = PromotionType.AWARDED_FREE_ENTRY,
                roundId = roundId,
                awardedBy = request.awardedBy,
                awardReason = request.reason,
                campaignId = request.campaignId,
                expiresAt = expiresAt
            )
        )

        outboxService.trackEvent(
            "free_entry_awarded",
            mapOf(
                "promotion_id" to promo.id.toString(),
                "promotion_type" to PromotionType.AWARDED_FREE_ENTRY.name,
                "awarded_by" to request.awardedBy,
                "reason" to request.reason,
                "campaign_id" to (request.campaignId ?: ""),
                "round_id" to (roundId?.toString() ?: ""),
                "player_id" to request.hostPlayerId
            ),
            playerId = request.hostPlayerId
        )

        return AwardResult(
            promotionId = requireNotNull(promo.id) { "Promotion ID must not be null" },
            hostPlayerId = promo.playerId,
            roundId = promo.roundId,
            awardedAt = promo.grantedAt
        )
    }

    @Transactional
    fun processCsvBulkAward(file: MultipartFile, campaignId: String?, awardedBy: String): BulkAwardStats {
        val tenantId = TenantContext.getRequiredTenantId()
        val tenant = requireTenant(tenantId)

        val lines = file.inputStream.bufferedReader().readLines()
        require(lines.size > 1) { "CSV file is empty or contains only a header row" }

        val dataLines = lines.drop(1).filter { it.isNotBlank() }
        require(dataLines.size <= 5000) { "CSV exceeds the 5,000 row limit (found ${dataLines.size} rows)" }

        val job = jobRepository.save(
            PromotionAwardJob(
                tenantId = tenantId,
                campaignId = campaignId,
                totalRequested = dataLines.size,
                createdBy = awardedBy
            )
        )

        var awarded = 0
        var skipped = 0
        var failed = 0

        dataLines.forEachIndexed { index, line ->
            val columns = line.split(",").map { it.trim() }
            if (columns.size < 2 || columns[0].isBlank() || columns[1].isBlank()) {
                logger.warn("CSV row ${index + 2}: skipped — missing host_player_id or reason")
                failed++
                return@forEachIndexed
            }

            val hostPlayerId = columns[0]
            val reason = columns[1]
            val targetRoundRaw = columns.getOrNull(2)?.takeIf { it.isNotBlank() }
            val expiresAtRaw = columns.getOrNull(3)?.takeIf { it.isNotBlank() }

            val activeCount = promotionRepository.countNonExpiredActiveByTenantIdAndPlayerId(tenantId, hostPlayerId)
            if (activeCount >= tenant.maxActiveFreeEntriesPerPlayer) {
                skipped++
                return@forEachIndexed
            }

            try {
                val roundId = resolveTargetRound(tenantId, targetRoundRaw)
                val expiresAt = expiresAtRaw?.let {
                    try { OffsetDateTime.parse(it) } catch (_: DateTimeParseException) { null }
                } ?: roundId?.let { resolveRoundExpiry(it) }

                promotionRepository.save(
                    PlayerPromotion(
                        tenantId = tenantId,
                        playerId = hostPlayerId,
                        type = "FREE_ENTRY",
                        promotionType = PromotionType.AWARDED_FREE_ENTRY,
                        roundId = roundId,
                        awardedBy = awardedBy,
                        awardReason = reason,
                        campaignId = campaignId,
                        expiresAt = expiresAt
                    )
                )
                awarded++
            } catch (e: Exception) {
                logger.warn("CSV row ${index + 2}: failed — ${e.message}")
                failed++
            }
        }

        job.totalProcessed = dataLines.size
        job.totalAwarded = awarded
        job.totalSkipped = skipped
        job.totalFailed = failed
        job.status = JobStatus.COMPLETED
        job.completedAt = OffsetDateTime.now()
        jobRepository.save(job)

        outboxService.trackEvent(
            "free_entry_awarded_bulk",
            mapOf(
                "job_id" to job.id.toString(),
                "campaign_id" to (campaignId ?: ""),
                "total_awarded" to awarded,
                "total_skipped" to skipped,
                "awarded_by" to awardedBy
            )
        )

        return BulkAwardStats(
            jobId = job.id,
            totalRequested = dataLines.size,
            totalAwarded = awarded,
            totalSkipped = skipped,
            totalFailed = failed,
            campaignId = campaignId,
            completedAt = requireNotNull(job.completedAt) { "Job completedAt must not be null" }
        )
    }

    @Transactional
    fun awardBulkFromFeed(players: List<AwardRequest>, campaignId: String?, awardedBy: String): BulkAwardStats {
        require(players.size <= 1000) { "Batch size exceeds the 1,000 player limit per request" }
        val tenantId = TenantContext.getRequiredTenantId()
        val tenant = requireTenant(tenantId)

        val job = jobRepository.save(
            PromotionAwardJob(
                tenantId = tenantId,
                campaignId = campaignId,
                totalRequested = players.size,
                createdBy = awardedBy
            )
        )

        var awarded = 0
        var skipped = 0
        var failed = 0

        players.forEach { req ->
            val activeCount = promotionRepository.countNonExpiredActiveByTenantIdAndPlayerId(tenantId, req.hostPlayerId)
            if (activeCount >= tenant.maxActiveFreeEntriesPerPlayer) {
                skipped++
                return@forEach
            }
            try {
                val roundId = resolveTargetRound(tenantId, req.targetRound)
                val expiresAt = req.expiresAt ?: roundId?.let { resolveRoundExpiry(it) }
                promotionRepository.save(
                    PlayerPromotion(
                        tenantId = tenantId,
                        playerId = req.hostPlayerId,
                        type = "FREE_ENTRY",
                        promotionType = PromotionType.AWARDED_FREE_ENTRY,
                        roundId = roundId,
                        awardedBy = awardedBy,
                        awardReason = req.reason,
                        campaignId = campaignId ?: req.campaignId,
                        expiresAt = expiresAt
                    )
                )
                awarded++
            } catch (e: Exception) {
                logger.warn("Bulk feed award failed for player ${req.hostPlayerId}: ${e.message}")
                failed++
            }
        }

        job.totalProcessed = players.size
        job.totalAwarded = awarded
        job.totalSkipped = skipped
        job.totalFailed = failed
        job.status = JobStatus.COMPLETED
        job.completedAt = OffsetDateTime.now()
        jobRepository.save(job)

        outboxService.trackEvent(
            "free_entry_awarded_bulk",
            mapOf(
                "job_id" to job.id.toString(),
                "campaign_id" to (campaignId ?: ""),
                "total_awarded" to awarded,
                "total_skipped" to skipped,
                "awarded_by" to awardedBy
            )
        )

        return BulkAwardStats(
            jobId = job.id,
            totalRequested = players.size,
            totalAwarded = awarded,
            totalSkipped = skipped,
            totalFailed = failed,
            campaignId = campaignId,
            completedAt = requireNotNull(job.completedAt) { "Job completedAt must not be null" }
        )
    }

    fun getJob(jobId: UUID): PromotionAwardJob {
        val tenantId = TenantContext.getRequiredTenantId()
        val job = jobRepository.findById(jobId)
            .orElseThrow { IllegalArgumentException("Job not found") }
        require(job.tenantId == tenantId) { "Job not found" }
        return job
    }

    fun listJobs(): List<PromotionAwardJob> {
        val tenantId = TenantContext.getRequiredTenantId()
        return jobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
    }

    fun listPlayerPromotions(hostPlayerId: String): List<PlayerPromotion> {
        val tenantId = TenantContext.getRequiredTenantId()
        return promotionRepository.findByTenantIdAndPlayerId(tenantId, hostPlayerId)
    }

    private fun validateAwardCap(tenantId: String, playerId: String, tenant: Tenant) {
        val activeCount = promotionRepository.countNonExpiredActiveByTenantIdAndPlayerId(tenantId, playerId)
        if (activeCount >= tenant.maxActiveFreeEntriesPerPlayer) {
            throw IllegalArgumentException(
                "Player already has $activeCount unused free ${if (activeCount == 1L) "entry" else "entries"} " +
                "(tenant max: ${tenant.maxActiveFreeEntriesPerPlayer})"
            )
        }
    }

    private fun requireTenant(tenantId: String): Tenant =
        tenantRepository.findById(tenantId).orElseThrow { IllegalStateException("Tenant not found: $tenantId") }

    private fun resolveTargetRound(tenantId: String, targetRound: String?): UUID? {
        return when {
            targetRound.isNullOrBlank() || targetRound.equals("CURRENT_ROUND", ignoreCase = true) -> {
                roundRepository.findByStatus(RoundStatus.OPEN)
                    .firstOrNull { it.tenantId == tenantId }?.id
            }
            targetRound.equals("NEXT_ROUND", ignoreCase = true) -> null
            else -> runCatching { UUID.fromString(targetRound) }.getOrNull()
        }
    }

    private fun resolveRoundExpiry(roundId: UUID): OffsetDateTime? {
        val round = roundRepository.findById(roundId).orElse(null) ?: return null
        val lockedAt = round.lockedAt ?: return null
        return lockedAt.atOffset(java.time.ZoneOffset.UTC)
    }
}
