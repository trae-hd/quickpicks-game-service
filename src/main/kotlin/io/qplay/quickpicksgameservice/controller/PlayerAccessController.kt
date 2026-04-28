package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.OperatorJwtClaims
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.TenantPlayerAccess
import io.qplay.quickpicksgameservice.tenant.persistence.TenantPlayerAccessRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/player-access")
@PreAuthorize("hasRole('TENANT_ADMIN')")
@Tag(name = "Admin - Player Access", description = "Operator endpoints for managing per-player access levels")
class PlayerAccessController(
    private val repository: TenantPlayerAccessRepository
) {
    @GetMapping
    @Operation(summary = "List all player access records, optionally filtered by accessLevel (ALLOW or BLOCK)")
    fun listAccess(
        @RequestParam(required = false) accessLevel: String?,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<List<TenantPlayerAccess>> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val normalisedLevel = accessLevel?.uppercase()?.let {
            when (it) { "ALLOW" -> "ALLOWED"; "BLOCK" -> "BLOCKED"; else -> it }
        }
        val data = if (normalisedLevel != null) {
            repository.findAllByTenantIdAndAccessLevel(tenantId, normalisedLevel)
        } else {
            repository.findAllByTenantId(tenantId)
        }
        return ApiResponse(data)
    }

    @GetMapping("/{playerId}")
    @Operation(summary = "Get the access record for a single player")
    fun getAccess(@PathVariable playerId: String): ApiResponse<TenantPlayerAccess?> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        return ApiResponse(repository.findByTenantIdAndPlayerId(tenantId, playerId))
    }

    /**
     * Create or update a player's access level.
     * Canonical values: ALLOWED, BLOCKED.
     * Aliases ALLOW and BLOCK are also accepted and normalised on write.
     */
    @PostMapping
    @Operation(summary = "Create or update a player's access level. Values: ALLOWED | BLOCKED")
    fun updateAccess(@RequestBody request: UpdateAccessRequest): ApiResponse<TenantPlayerAccess> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val normalised = when (request.accessLevel.uppercase()) {
            "ALLOW", "ALLOWED"   -> "ALLOWED"
            "BLOCK", "BLOCKED"   -> "BLOCKED"
            else -> throw IllegalArgumentException("Invalid accessLevel '${request.accessLevel}'. Valid values: ALLOWED, BLOCKED")
        }
        val existing = repository.findByTenantIdAndPlayerId(tenantId, request.playerId)
        val access = existing?.let {
            TenantPlayerAccess(id = it.id, tenantId = tenantId, playerId = request.playerId, accessLevel = normalised)
        } ?: TenantPlayerAccess(tenantId = tenantId, playerId = request.playerId, accessLevel = normalised)
        return ApiResponse(repository.save(access))
    }

    @DeleteMapping("/{playerId}")
    @Transactional
    @Operation(summary = "Remove a player from the access list")
    fun removeAccess(
        @PathVariable playerId: String,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ResponseEntity<Void> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        repository.deleteByTenantIdAndPlayerId(tenantId, playerId)
        return ResponseEntity.noContent().build()
    }
}

data class UpdateAccessRequest(
    val playerId: String,
    val accessLevel: String
)