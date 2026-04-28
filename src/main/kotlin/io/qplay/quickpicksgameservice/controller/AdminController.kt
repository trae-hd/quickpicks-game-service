package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.slate.MatchRepository
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.OperatorJwtClaims
import io.qplay.quickpicksgameservice.service.AuditService
import io.qplay.quickpicksgameservice.service.SettlementService
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import java.time.OffsetDateTime
import java.util.*

// ---------------------------------------------------------------------------
// Reconciliation
// ---------------------------------------------------------------------------

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/reconciliation")
@Tag(name = "Admin - Reconciliation", description = "Operator endpoints for resolving ledger exceptions")
class ReconciliationController(
    private val jdbcTemplate: JdbcTemplate
) {
    @GetMapping("/exceptions")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "List open reconciliation exceptions")
    fun listExceptions(@AuthenticationPrincipal claims: OperatorJwtClaims): ApiResponse<List<Map<String, Any>>> {
        val exceptions = jdbcTemplate.queryForList(
            "SELECT * FROM reconciliation_exceptions WHERE tenant_id = ? AND status = 'OPEN'",
            claims.tenantId
        )
        return ApiResponse(exceptions)
    }

    @PostMapping("/exceptions/{id}/resolve")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Mark a reconciliation exception as resolved")
    fun resolveException(
        @PathVariable id: String,
        @RequestBody request: ResolveRequest,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<Map<String, Any>> {
        jdbcTemplate.update(
            "UPDATE reconciliation_exceptions SET status = 'RESOLVED', resolved_at = ?, resolved_by = ?, notes = ? WHERE id = ? AND tenant_id = ?",
            OffsetDateTime.now(), claims.operatorId, request.notes, id, claims.tenantId
        )
        return ApiResponse(mapOf("success" to true))
    }
}

data class ResolveRequest(val notes: String)

// ---------------------------------------------------------------------------
// Audit
// ---------------------------------------------------------------------------

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Admin - Audit", description = "Operator endpoints for viewing audit trails")
class AuditController(
    private val jdbcTemplate: JdbcTemplate
) {
    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "List audit log entries")
    fun listAuditEntries(
        @AuthenticationPrincipal claims: OperatorJwtClaims,
        @RequestParam(required = false) action: String?
    ): ApiResponse<List<Map<String, Any>>> {
        val sql = if (action != null) {
            "SELECT * FROM audit_log WHERE tenant_id = ? AND action = ? ORDER BY created_at DESC LIMIT 100"
        } else {
            "SELECT * FROM audit_log WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 100"
        }
        val params: Array<Any> = if (action != null) arrayOf(claims.tenantId, action) else arrayOf(claims.tenantId)
        return ApiResponse(jdbcTemplate.queryForList(sql, *params))
    }
}

// ---------------------------------------------------------------------------
// Rounds (admin-scoped — accepts operator JWT, all statuses)
// ---------------------------------------------------------------------------

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/rounds")
@Tag(name = "Admin - Rounds", description = "Operator endpoints for round management, dashboards, and settlement")
class RoundAdminController(
    private val jdbcTemplate: JdbcTemplate,
    private val settlementService: SettlementService,
    private val matchRepository: MatchRepository,
    private val auditService: AuditService
) {
    /**
     * List rounds for the tenant across all statuses. Optional ?status= filter accepts
     * the canonical enum values: OPEN, LOCKED, SETTLED, VOIDED, REQUIRES_REVIEW, CANCELLED.
     * These are the only valid round statuses — AWAITING_SETTLEMENT and SETTLING do not exist.
     */
    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "List rounds (all statuses). Filter with ?status=OPEN|LOCKED|SETTLED|VOIDED|REQUIRES_REVIEW|CANCELLED")
    fun listRounds(
        @RequestParam(required = false) status: String?,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<List<RoundAdminListItem>> {
        if (status != null) {
            runCatching { RoundStatus.valueOf(status.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $status. Valid values: ${RoundStatus.values().joinToString()}") }
        }
        val sql = buildString {
            append("""
                SELECT
                    r.id,
                    r.status,
                    r.jackpot_pool_pence,
                    r.eleven_pool_pence,
                    r.ten_pool_pence,
                    r.locked_at,
                    r.settle_after,
                    r.settled_at,
                    r.created_at as round_created_at,
                    s.round_window_start,
                    s.round_window_end,
                    (SELECT COUNT(*) FROM entries e WHERE e.round_id = r.id AND e.tenant_id = r.tenant_id) AS entry_count
                FROM rounds r
                JOIN slates s ON s.id = r.slate_id
                WHERE r.tenant_id = ?
            """)
            if (status != null) append(" AND r.status = ?")
            append(" ORDER BY s.round_window_start DESC LIMIT 50")
        }
        val params = if (status != null) arrayOf<Any>(claims.tenantId, status.uppercase()) else arrayOf<Any>(claims.tenantId)
        val rows = jdbcTemplate.queryForList(sql, *params)
        return ApiResponse(rows.map { it.toRoundAdminListItem() })
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get a single round with entry stats and match list")
    fun getRound(
        @PathVariable id: UUID,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<RoundAdminDetail> {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT
                r.id,
                r.status,
                r.jackpot_pool_pence,
                r.eleven_pool_pence,
                r.ten_pool_pence,
                r.rollover_in_pence,
                r.locked_at,
                r.settle_after,
                r.settled_at,
                s.id            AS slate_id,
                s.status        AS slate_status,
                s.round_window_start,
                s.round_window_end,
                s.created_by,
                s.approved_by,
                (SELECT COUNT(*)            FROM entries e WHERE e.round_id = r.id AND e.tenant_id = r.tenant_id) AS entry_count,
                (SELECT COUNT(DISTINCT e.player_id) FROM entries e WHERE e.round_id = r.id AND e.tenant_id = r.tenant_id) AS unique_players,
                (SELECT COALESCE(SUM(e.stake_pence), 0) FROM entries e WHERE e.round_id = r.id AND e.tenant_id = r.tenant_id AND NOT e.is_free_entry) AS gross_revenue_pence
            FROM rounds r
            JOIN slates s ON s.id = r.slate_id
            WHERE r.id = ? AND r.tenant_id = ?
            """,
            id, claims.tenantId
        )
        val matches = jdbcTemplate.queryForList(
            "SELECT id, provider_match_id, home_team, away_team, kick_off, league, status FROM matches WHERE slate_id = ? ORDER BY kick_off",
            row["slate_id"]
        )
        return ApiResponse(row.toRoundAdminDetail(matches))
    }

    @GetMapping("/{id}/dashboard")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Live entry stats dashboard for a round")
    fun getRoundDashboard(
        @PathVariable id: UUID,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<RoundDashboardResponse> {
        val entryStats = jdbcTemplate.queryForMap(
            """
            SELECT
                COUNT(*) AS entry_count,
                COALESCE(SUM(stake_pence), 0) AS gross_revenue_pence,
                COALESCE(SUM(CASE WHEN NOT is_free_entry THEN stake_pence ELSE 0 END), 0) AS paid_revenue_pence,
                COUNT(DISTINCT player_id) AS unique_players,
                SUM(CASE WHEN is_free_entry THEN 1 ELSE 0 END) AS free_entries
            FROM entries
            WHERE round_id = ? AND tenant_id = ?
            """,
            id, claims.tenantId
        )

        val roundMeta = jdbcTemplate.queryForMap(
            """
            SELECT
                r.status          AS settlement_status,
                r.jackpot_pool_pence,
                MIN(m.kick_off)   AS first_kick_off
            FROM rounds r
            JOIN slates s ON s.id = r.slate_id
            JOIN matches m ON m.slate_id = s.id
            WHERE r.id = ? AND r.tenant_id = ?
            GROUP BY r.status, r.jackpot_pool_pence
            """,
            id, claims.tenantId
        )

        val topOutcomes: List<Map<String, Any>> = try {
            jdbcTemplate.queryForList(
                """
                SELECT
                    pick->>'providerMatchId' AS match_id,
                    pick->>'outcome'         AS outcome,
                    COUNT(*)                 AS count
                FROM entries e,
                     jsonb_array_elements(e.picks->'picks') AS pick
                WHERE e.round_id = ? AND e.tenant_id = ?
                GROUP BY pick->>'providerMatchId', pick->>'outcome'
                ORDER BY count DESC
                LIMIT 5
                """,
                id, claims.tenantId
            )
        } catch (_: Exception) {
            emptyList()
        }

        val grossRevenue = (entryStats["gross_revenue_pence"] as Number).toLong()
        return ApiResponse(
            RoundDashboardResponse(
                entryCount = (entryStats["entry_count"] as Number).toLong(),
                grossRevenuePence = grossRevenue,
                jackpotPoolPence = (roundMeta["jackpot_pool_pence"] as Number).toLong(),
                projectedCostPence = 0L,
                projectedMarginPence = 0L,
                uniquePlayers = (entryStats["unique_players"] as Number).toLong(),
                freeEntries = (entryStats["free_entries"] as Number).toLong(),
                settlementStatus = roundMeta["settlement_status"]?.toString() ?: "UNKNOWN",
                firstKickOff = roundMeta["first_kick_off"]?.toString(),
                topOutcomes = topOutcomes
            )
        )
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Manually trigger settlement for a LOCKED round")
    fun settleRound(
        @PathVariable id: UUID,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<Map<String, Any>> {
        settlementService.settleRoundNow(id)
        auditService.log(claims.tenantId, claims.operatorId, "MANUAL_SETTLE", id.toString())
        return ApiResponse(mapOf("success" to true))
    }

    /**
     * Override a match result. Canonical result values: HOME, AWAY, DRAW.
     * Aliases HOME_WIN and AWAY_WIN are also accepted for compatibility.
     * The override stores representative scores (e.g. HOME → 1:0) so the next
     * settlement cycle picks them up correctly.
     */
    @PostMapping("/{id}/override")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Override a match result. Canonical result values: HOME | AWAY | DRAW (aliases HOME_WIN | AWAY_WIN also accepted)")
    fun overrideResult(
        @PathVariable id: UUID,
        @RequestBody request: OverrideRequest,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<Map<String, Any>> {
        val match = matchRepository.findById(request.matchId)
            .orElseThrow { IllegalArgumentException("Match not found: ${request.matchId}") }
        val (home, away) = when (request.result.uppercase()) {
            "HOME", "HOME_WIN" -> Pair(1, 0)
            "AWAY", "AWAY_WIN" -> Pair(0, 1)
            "DRAW"             -> Pair(0, 0)
            else -> throw IllegalArgumentException("Invalid result '${request.result}'. Valid values: HOME, AWAY, DRAW")
        }
        match.regulationResultHome = home
        match.regulationResultAway = away
        matchRepository.save(match)
        auditService.log(
            tenantId = claims.tenantId,
            actorId = claims.operatorId,
            action = "RESULT_OVERRIDE",
            targetId = request.matchId.toString(),
            payload = mapOf("result" to request.result, "reason" to request.reason, "roundId" to id.toString())
        )
        return ApiResponse(mapOf("success" to true, "matchId" to request.matchId.toString(), "result" to request.result))
    }
}

// --- DTOs ---

data class RoundAdminListItem(
    val id: UUID,
    val status: String,
    val roundWindowStart: Any?,   // Instant — surfaced as ISO string via Jackson
    val roundWindowEnd: Any?,
    val jackpotPoolPence: Long,
    val entryCount: Long,
    val lockedAt: Any?,
    val settleAfter: Any?,
    val settledAt: Any?
)

data class RoundAdminDetail(
    val id: UUID,
    val status: String,
    val slateId: UUID,
    val slateStatus: String,
    val roundWindowStart: Any?,
    val roundWindowEnd: Any?,
    val jackpotPoolPence: Long,
    val elevenPoolPence: Long,
    val tenPoolPence: Long,
    val rolloverInPence: Long,
    val entryCount: Long,
    val uniquePlayers: Long,
    val grossRevenuePence: Long,
    val lockedAt: Any?,
    val settleAfter: Any?,
    val settledAt: Any?,
    val createdBy: String?,
    val approvedBy: String?,
    val matches: List<Map<String, Any>>
)

data class RoundDashboardResponse(
    val entryCount: Long,
    val grossRevenuePence: Long,
    val jackpotPoolPence: Long,
    val projectedCostPence: Long,
    val projectedMarginPence: Long,
    val uniquePlayers: Long,
    val freeEntries: Long,
    val settlementStatus: String,
    val firstKickOff: String?,
    val topOutcomes: List<Map<String, Any>>
)

data class OverrideRequest(
    val matchId: UUID,
    val result: String,
    val reason: String
)

private fun Map<String, Any?>.toRoundAdminListItem() = RoundAdminListItem(
    id = UUID.fromString(this["id"].toString()),
    status = this["status"].toString(),
    roundWindowStart = this["round_window_start"],
    roundWindowEnd = this["round_window_end"],
    jackpotPoolPence = (this["jackpot_pool_pence"] as Number).toLong(),
    entryCount = (this["entry_count"] as Number).toLong(),
    lockedAt = this["locked_at"],
    settleAfter = this["settle_after"],
    settledAt = this["settled_at"]
)

private fun Map<String, Any?>.toRoundAdminDetail(matches: List<Map<String, Any>>) = RoundAdminDetail(
    id = UUID.fromString(this["id"].toString()),
    status = this["status"].toString(),
    slateId = UUID.fromString(this["slate_id"].toString()),
    slateStatus = this["slate_status"].toString(),
    roundWindowStart = this["round_window_start"],
    roundWindowEnd = this["round_window_end"],
    jackpotPoolPence = (this["jackpot_pool_pence"] as Number).toLong(),
    elevenPoolPence = (this["eleven_pool_pence"] as Number).toLong(),
    tenPoolPence = (this["ten_pool_pence"] as Number).toLong(),
    rolloverInPence = (this["rollover_in_pence"] as Number).toLong(),
    entryCount = (this["entry_count"] as Number).toLong(),
    uniquePlayers = (this["unique_players"] as Number).toLong(),
    grossRevenuePence = (this["gross_revenue_pence"] as Number).toLong(),
    lockedAt = this["locked_at"],
    settleAfter = this["settle_after"],
    settledAt = this["settled_at"],
    createdBy = this["created_by"]?.toString(),
    approvedBy = this["approved_by"]?.toString(),
    matches = matches
)

// ---------------------------------------------------------------------------
// Optimove
// ---------------------------------------------------------------------------

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/optimove")
@Tag(name = "Admin - Optimove", description = "Operator endpoints for Optimove configuration and health")
class OptimoveAdminController(
    private val tenantRepository: TenantRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val restClientBuilder: RestClient.Builder
) {
    @GetMapping("/config")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get tenant-specific Optimove configuration")
    fun getConfig(@AuthenticationPrincipal claims: OperatorJwtClaims): ApiResponse<OptimoveConfigResponse> {
        val tenant = tenantRepository.findById(claims.tenantId).orElseThrow()
        return ApiResponse(OptimoveConfigResponse(streamId = tenant.optimoveStreamId, hasApiKey = tenant.optimoveApiKeyEncrypted != null))
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update Optimove stream ID and API key for the tenant")
    fun updateConfig(
        @RequestBody request: OptimoveConfigRequest,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<OptimoveConfigResponse> {
        jdbcTemplate.update(
            "UPDATE tenants SET optimove_stream_id = ?, optimove_api_key_encrypted = ?, updated_at = NOW() WHERE id = ?",
            request.streamId, request.apiKey, claims.tenantId
        )
        return ApiResponse(OptimoveConfigResponse(streamId = request.streamId, hasApiKey = request.apiKey != null))
    }

    @GetMapping("/health")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Check Optimove delivery health for this tenant")
    fun getHealth(@AuthenticationPrincipal claims: OperatorJwtClaims): ApiResponse<OptimoveHealthResponse> {
        val tenant = tenantRepository.findById(claims.tenantId).orElseThrow()
        if (tenant.optimoveApiKeyEncrypted == null || tenant.optimoveStreamId == null) {
            return ApiResponse(OptimoveHealthResponse(status = "NOT_CONFIGURED", latencyMs = null))
        }
        return try {
            val start = System.currentTimeMillis()
            restClientBuilder.clone()
                .baseUrl("https://streams.optimove.net")
                .defaultHeader("Authorization-Token", tenant.optimoveApiKeyEncrypted)
                .build()
                .get()
                .uri("/v1/streams/${tenant.optimoveStreamId}/events")
                .retrieve()
                .toBodilessEntity()
            ApiResponse(OptimoveHealthResponse(status = "UP", latencyMs = System.currentTimeMillis() - start))
        } catch (_: Exception) {
            ApiResponse(OptimoveHealthResponse(status = "DOWN", latencyMs = null))
        }
    }
}

data class OptimoveConfigRequest(val streamId: String?, val apiKey: String?)
data class OptimoveConfigResponse(val streamId: String?, val hasApiKey: Boolean)
data class OptimoveHealthResponse(val status: String, val latencyMs: Long?)