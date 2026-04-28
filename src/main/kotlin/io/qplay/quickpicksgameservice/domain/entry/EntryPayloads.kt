package io.qplay.quickpicksgameservice.domain.entry

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.qplay.quickpicksgameservice.domain.round.Outcome

data class PicksPayload @JsonCreator constructor(
    @JsonProperty("picks")
    val picks: List<Pick>,
    @JsonProperty("version")
    val version: Int = 1
) {
    init {
        require(picks.isNotEmpty()) { "Picks must not be empty" }
        require(picks.size <= 12) { "Picks must not exceed 12 entries" }
    }
}

data class CreateEntryRequest @JsonCreator constructor(
    @JsonProperty("picks")
    val picks: PicksPayload,
    @JsonProperty("tiebreaker")
    val tiebreaker: Int? = null,
    @JsonProperty("playerExclusions")
    val playerExclusions: Map<String, Boolean>? = null
)

data class Pick @JsonCreator constructor(
    @JsonProperty("providerMatchId")
    val providerMatchId: String,
    @JsonProperty("outcome")
    val outcome: Outcome
)

enum class EntryStatus {
    PENDING,
    ACCEPTED,
    SETTLED,
    CANCELLED,
    VOID
}
