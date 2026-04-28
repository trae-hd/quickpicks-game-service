package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.domain.entry.JobStatus
import io.qplay.quickpicksgameservice.domain.entry.PromotionAwardJob
import io.qplay.quickpicksgameservice.domain.entry.PromotionType
import io.qplay.quickpicksgameservice.domain.entry.PlayerPromotion
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.OperatorJwtClaims
import io.qplay.quickpicksgameservice.service.AwardRequest
import io.qplay.quickpicksgameservice.service.BulkAwardStats
import io.qplay.quickpicksgameservice.service.AwardResult
import io.qplay.quickpicksgameservice.service.PromotionAwardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/promotions")
@Tag(name = "Admin - Promotions", description = "Operator endpoints for free entry award management")
class PromotionAdminController(
    private val promotionAwardService: PromotionAwardService
) {
    @PostMapping("/award")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Award a single free entry to a player")
    fun awardSingle(
        @RequestBody request: SingleAwardRequest,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<AwardResult> {
        val result = promotionAwardService.awardToPlayer(
            AwardRequest(
                hostPlayerId = request.hostPlayerId,
                reason = request.reason,
                targetRound = request.targetRound,
                expiresAt = request.expiresAt,
                campaignId = request.campaignId,
                awardedBy = claims.operatorId
            )
        )
        return ApiResponse(result)
    }

    @PostMapping("/award/bulk", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Bulk award free entries via CSV upload (max 5,000 rows)")
    fun awardBulkCsv(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) campaignId: String?,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<BulkAwardStats> {
        val stats = promotionAwardService.processCsvBulkAward(file, campaignId, claims.operatorId)
        return ApiResponse(stats)
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "List promotions for a specific player")
    fun listByPlayer(
        @RequestParam hostPlayerId: String
    ): ApiResponse<List<PromotionSummary>> {
        val promotions = promotionAwardService.listPlayerPromotions(hostPlayerId)
            .map { PromotionSummary.fromDomain(it) }
        return ApiResponse(promotions)
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "List all award jobs for the tenant")
    fun listJobs(): ApiResponse<List<JobSummary>> {
        val jobs = promotionAwardService.listJobs().map { JobSummary.fromDomain(it) }
        return ApiResponse(jobs)
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Get progress for a specific award job")
    fun getJob(@PathVariable jobId: UUID): ApiResponse<JobSummary> {
        val job = promotionAwardService.getJob(jobId)
        return ApiResponse(JobSummary.fromDomain(job))
    }
}

data class SingleAwardRequest @JsonCreator constructor(
    @JsonProperty("hostPlayerId") val hostPlayerId: String,
    @JsonProperty("reason") val reason: String,
    @JsonProperty("targetRound") val targetRound: String? = null,
    @JsonProperty("expiresAt") val expiresAt: OffsetDateTime? = null,
    @JsonProperty("campaignId") val campaignId: String? = null
)

data class PromotionSummary(
    val id: UUID,
    val promotionType: PromotionType,
    val status: String,
    val roundId: UUID?,
    val awardedBy: String?,
    val awardReason: String?,
    val campaignId: String?,
    val grantedAt: OffsetDateTime,
    val consumedAt: OffsetDateTime?,
    val expiresAt: OffsetDateTime?
) {
    companion object {
        fun fromDomain(p: PlayerPromotion) = PromotionSummary(
            id = p.id,
            promotionType = p.promotionType,
            status = p.status,
            roundId = p.roundId,
            awardedBy = p.awardedBy,
            awardReason = p.awardReason,
            campaignId = p.campaignId,
            grantedAt = p.grantedAt,
            consumedAt = p.consumedAt,
            expiresAt = p.expiresAt
        )
    }
}

data class JobSummary(
    val id: UUID,
    val campaignId: String?,
    val status: JobStatus,
    val totalRequested: Int,
    val totalProcessed: Int,
    val totalAwarded: Int,
    val totalSkipped: Int,
    val totalFailed: Int,
    val createdBy: String,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?
) {
    companion object {
        fun fromDomain(j: PromotionAwardJob) = JobSummary(
            id = j.id,
            campaignId = j.campaignId,
            status = j.status,
            totalRequested = j.totalRequested,
            totalProcessed = j.totalProcessed,
            totalAwarded = j.totalAwarded,
            totalSkipped = j.totalSkipped,
            totalFailed = j.totalFailed,
            createdBy = j.createdBy,
            createdAt = j.createdAt,
            completedAt = j.completedAt
        )
    }
}
