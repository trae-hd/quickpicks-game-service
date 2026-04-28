package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.security.OperatorJwtClaims
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/tenants")
@Tag(name = "Admin - Tenants", description = "Tenant configuration management")
class TenantAdminController(
    private val tenantRepository: TenantRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "List all tenants (PLATFORM_ADMIN only)")
    fun listTenants(): ApiResponse<List<TenantConfigResponse>> {
        return ApiResponse(tenantRepository.findAll().map { it.toResponse() })
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Get configuration for a tenant. TENANT_ADMIN can only access their own.")
    fun getTenant(
        @PathVariable id: String,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<TenantConfigResponse> {
        if (claims.role == "TENANT_ADMIN" && claims.tenantId != id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
        val tenant = tenantRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: $id") }
        return ApiResponse(tenant.toResponse())
    }

    /**
     * Update tenant configuration.
     * - walletBaseUrl: single base URL for the wallet service (e.g. "https://wallet.mrq.com")
     * - allowedHostOrigins: array of allowed origins (e.g. ["https://mrq.com"])
     * TENANT_ADMIN can only update their own tenant.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Operation(summary = "Update wallet URL and allowed origins. TENANT_ADMIN can only update their own.")
    fun updateTenant(
        @PathVariable id: String,
        @RequestBody request: UpdateTenantConfigRequest,
        @AuthenticationPrincipal claims: OperatorJwtClaims
    ): ApiResponse<TenantConfigResponse> {
        if (claims.role == "TENANT_ADMIN" && claims.tenantId != id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }
        if (!tenantRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: $id")
        }
        val originsJson = request.allowedHostOrigins?.let { objectMapper.writeValueAsString(it) }
        jdbcTemplate.update(
            """
            UPDATE tenants SET
                wallet_base_url       = COALESCE(?, wallet_base_url),
                allowed_host_origins  = CASE WHEN ?::text IS NOT NULL THEN ?::jsonb ELSE allowed_host_origins END,
                updated_at            = NOW()
            WHERE id = ?
            """,
            request.walletBaseUrl,
            originsJson, originsJson,
            id
        )
        val tenant = tenantRepository.findById(id).orElseThrow()
        return ApiResponse(tenant.toResponse())
    }

    private fun Tenant.toResponse(): TenantConfigResponse {
        val origins: List<String> = try {
            objectMapper.readValue(this.allowedHostOrigins)
        } catch (_: Exception) {
            emptyList()
        }
        return TenantConfigResponse(
            id = this.id,
            name = this.name,
            walletBaseUrl = this.walletBaseUrl,
            allowedHostOrigins = origins,
            currency = this.currency,
            featureFlags = this.featureFlags,
            targetingMode = this.targetingMode,
            freeEntryEnabled = this.freeEntryEnabled,
            crossSellComplianceMode = this.crossSellComplianceMode,
            playerFeedEnabled = this.playerFeedEnabled,
            primaryTimezone = this.primaryTimezone,
            slateWindowHours = this.slateWindowHours,
            optimoveStreamId = this.optimoveStreamId,
            hasOptimoveApiKey = this.optimoveApiKeyEncrypted != null
        )
    }
}

data class TenantConfigResponse(
    val id: String,
    val name: String,
    val walletBaseUrl: String,
    val allowedHostOrigins: List<String>,      // native array, not a JSON string
    val currency: String,
    val featureFlags: String,
    val targetingMode: String,
    val freeEntryEnabled: Boolean,
    val crossSellComplianceMode: String,
    val playerFeedEnabled: Boolean,
    val primaryTimezone: String,
    val slateWindowHours: Int,
    val optimoveStreamId: String?,
    val hasOptimoveApiKey: Boolean
)

data class UpdateTenantConfigRequest(
    val walletBaseUrl: String? = null,
    val allowedHostOrigins: List<String>? = null   // send as array, not a JSON-encoded string
)