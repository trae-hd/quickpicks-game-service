package io.qplay.quickpicksgameservice.infra.optimove

import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.slf4j.LoggerFactory

@Component
class OptimoveStreamsClient(
    private val restClientBuilder: RestClient.Builder,
    private val tenantRepository: TenantRepository
) {
    private val logger = LoggerFactory.getLogger(OptimoveStreamsClient::class.java)

    fun publishBatch(tenantId: String, batch: List<OptimoveEvent>) {
        val tenant = tenantRepository.findById(tenantId).orElse(null) ?: return
        val apiKey = tenant.optimoveApiKeyEncrypted ?: return // Assume decrypted for now or already in clear
        val streamId = tenant.optimoveStreamId ?: return

        val client = restClientBuilder
            .baseUrl("https://streams.optimove.net/v1/")
            .defaultHeader("Authorization-Token", apiKey)
            .build()

        try {
            client.post()
                .uri("/streams/$streamId/events")
                .body(batch)
                .retrieve()
                .toBodilessEntity()
            logger.info("Published batch of ${batch.size} events for tenant $tenantId")
        } catch (e: Exception) {
            logger.error("Failed to publish to Optimove for $tenantId: ${e.message}")
            throw e
        }
    }
}

data class OptimoveEvent(
    @get:JsonProperty("event_id") val eventId: String,
    @get:JsonProperty("event_name") val eventName: String,
    @get:JsonProperty("event_timestamp") val eventTimestamp: String,
    @get:JsonProperty("player_id") val playerId: String,
    @get:JsonProperty("tenant_id") val tenantId: String,
    @get:JsonProperty("event_properties") val eventProperties: Map<String, Any?>
)
