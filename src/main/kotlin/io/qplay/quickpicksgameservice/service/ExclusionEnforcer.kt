package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.tenant.persistence.TenantExclusionCatalogRepository
import org.springframework.stereotype.Service

@Service
class ExclusionEnforcer(
    private val exclusionCatalogRepository: TenantExclusionCatalogRepository
) {
    fun checkExclusions(tenantId: String, playerId: String, playerExclusions: Map<String, Boolean>) {
        val catalog = exclusionCatalogRepository.findAllByTenantIdAndIsActive(tenantId, true)
        
        catalog.forEach { flag ->
            if (playerExclusions[flag.flagName] == true) {
                throw IllegalArgumentException("Player is not eligible to enter")
            }
        }
    }
}
