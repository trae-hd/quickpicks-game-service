package io.qplay.quickpicksgameservice.service.outbox

import io.qplay.quickpicksgameservice.domain.outbox.OutboxEvent
import io.qplay.quickpicksgameservice.domain.outbox.OutboxRepository
import io.qplay.quickpicksgameservice.service.PlayerProfileService
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxService(
    private val outboxRepository: OutboxRepository,
    private val playerProfileService: PlayerProfileService
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun trackEvent(
        eventType: String,
        payload: Map<String, Any?>,
        playerId: String? = null,
        batchEligible: Boolean = true
    ) {
        val tenantId = TenantContext.getTenantId() ?: "SYSTEM"
        val profile = if (playerId != null) {
            playerProfileService.getCounters(tenantId, playerId)
        } else {
            emptyMap()
        }

        val fullPayload = payload.toMutableMap()
        fullPayload["player_profile"] = profile

        outboxRepository.save(
            OutboxEvent(
                eventType = eventType,
                payload = fullPayload,
                playerId = playerId,
                batchEligible = batchEligible,
                tenantId = tenantId
            )
        )
    }
}
