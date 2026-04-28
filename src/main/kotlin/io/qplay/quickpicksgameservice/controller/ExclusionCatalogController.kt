package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.TenantExclusionCatalog
import io.qplay.quickpicksgameservice.tenant.persistence.TenantExclusionCatalogRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/exclusion-catalog")
@PreAuthorize("hasRole('TENANT_ADMIN')")
@Tag(name = "Admin - Exclusion Catalog", description = "Operator endpoints for managing exclusion flag definitions")
class ExclusionCatalogController(
    private val repository: TenantExclusionCatalogRepository
) {
    @GetMapping
    @Operation(summary = "List active exclusion flags for this tenant")
    fun getCatalog(): ApiResponse<List<TenantExclusionCatalog>> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        return ApiResponse(repository.findAllByTenantIdAndIsActive(tenantId, true))
    }

    @PostMapping
    @Operation(summary = "Add a new exclusion flag")
    fun addFlag(@RequestBody request: AddExclusionFlagRequest): ApiResponse<TenantExclusionCatalog> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val flag = TenantExclusionCatalog(
            tenantId = tenantId,
            flagName = request.flagName,
            description = request.description
        )
        return ApiResponse(repository.save(flag))
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update the description of an existing exclusion flag")
    fun updateFlag(
        @PathVariable id: UUID,
        @RequestBody request: UpdateExclusionFlagRequest
    ): ApiResponse<TenantExclusionCatalog> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val existing = repository.findById(id)
            .orElseThrow { IllegalArgumentException("Exclusion flag not found") }
        require(existing.tenantId == tenantId) { "Flag does not belong to this tenant" }
        val updated = TenantExclusionCatalog(
            id = existing.id,
            tenantId = existing.tenantId,
            flagName = existing.flagName,
            description = request.description,
            isActive = existing.isActive,
            createdAt = existing.createdAt
        )
        return ApiResponse(repository.save(updated))
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive (soft-delete) an exclusion flag — it will no longer appear in the catalog")
    fun archiveFlag(@PathVariable id: UUID): ResponseEntity<Void> {
        val tenantId = TenantContext.getTenantId() ?: throw IllegalStateException("No tenant context")
        val existing = repository.findById(id)
            .orElseThrow { IllegalArgumentException("Exclusion flag not found") }
        require(existing.tenantId == tenantId) { "Flag does not belong to this tenant" }
        val archived = TenantExclusionCatalog(
            id = existing.id,
            tenantId = existing.tenantId,
            flagName = existing.flagName,
            description = existing.description,
            isActive = false,
            createdAt = existing.createdAt
        )
        repository.save(archived)
        return ResponseEntity.noContent().build()
    }
}

data class AddExclusionFlagRequest(
    val flagName: String,
    val description: String? = null
)

data class UpdateExclusionFlagRequest(
    val description: String?
)