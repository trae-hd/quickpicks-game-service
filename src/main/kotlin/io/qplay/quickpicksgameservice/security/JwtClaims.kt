package io.qplay.quickpicksgameservice.security

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

data class PlayerJwtClaims(
    @JsonProperty("sub") val playerId: String,
    @JsonProperty("tenant") val tenantId: String,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("locale") val locale: String,
    @JsonProperty("kycLevel") val kycLevel: String,
    @JsonProperty("rgFlags") val rgFlags: List<String> = emptyList(),
    @JsonProperty("exclusionFlags") val exclusionFlags: List<String> = emptyList(),
    @JsonProperty("preview") val preview: Boolean = false,
    @JsonProperty("registeredAt") val registeredAt: OffsetDateTime? = null
)

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
data class OperatorJwtClaims(
    @JsonProperty("sub") val operatorId: String,
    @JsonProperty("tenantScope") val tenantId: String,
    @JsonProperty("role") val role: String
)
