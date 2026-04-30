package io.qplay.quickpicksgameservice.domain.sportsfeed

import java.time.Instant

data class FixtureCacheRow(
    val providerMatchId: String,
    val providerId: String,
    val leagueId: String,
    val leagueName: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickOff: Instant,
    val rawPayload: String,
    val expiresAt: Instant,
    val season: String
)