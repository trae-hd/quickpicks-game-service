package io.qplay.quickpicksgameservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.qplay.quickpicksgameservice.domain.sportsfeed.FeedLeagueMappingRepository
import io.qplay.quickpicksgameservice.domain.sportsfeed.FixtureCacheRepository
import io.qplay.quickpicksgameservice.domain.sportsfeed.FixtureCacheRow
import io.qplay.quickpicksgameservice.infra.feed.ApiFootballClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class FixtureCacheSyncService(
    private val apiFootballClient: ApiFootballClient,
    private val feedLeagueMappingRepository: FeedLeagueMappingRepository,
    private val fixtureCacheRepository: FixtureCacheRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(FixtureCacheSyncService::class.java)

    fun sync() {
        val deleted = fixtureCacheRepository.deleteExpired()
        logger.info("Deleted $deleted expired fixture cache entries")

        val currentSeasons = fetchCurrentSeasons()
        logger.info("Discovered ${currentSeasons.size} leagues with active seasons")

        val leagues = feedLeagueMappingRepository.findByProviderId("api-football")
        logger.info("Syncing fixtures for ${leagues.size} configured leagues")

        val today = LocalDate.now(ZoneOffset.UTC)
        val toDate = today.plusDays(14)

        leagues.forEach { league ->
            val leagueIdInt = league.providerLeagueId.toIntOrNull() ?: run {
                logger.warn("League ${league.providerLeagueId} has non-integer ID, skipping")
                return@forEach
            }
            val season = currentSeasons[leagueIdInt] ?: run {
                logger.warn("No active season found for league ${league.providerLeagueId} (${league.leagueName}), skipping")
                return@forEach
            }

            try {
                val rawResponse = apiFootballClient.getFixtures(
                    league = league.providerLeagueId,
                    season = season.toString(),
                    from = today.toString(),
                    to = toDate.toString()
                )
                val fixtures = parseFixtures(rawResponse, league.leagueName)
                val upserted = fixtureCacheRepository.upsertFixtures(fixtures)
                logger.info("Upserted $upserted fixtures for ${league.leagueName} (season $season)")
            } catch (e: Exception) {
                logger.error("Failed to sync fixtures for league ${league.providerLeagueId} (${league.leagueName}): ${e.message}", e)
            }
        }
    }

    private fun fetchCurrentSeasons(): Map<Int, Int> {
        val rawResponse = apiFootballClient.getCurrentSeasons()
        val root = objectMapper.readTree(rawResponse)
        return root["response"]
            ?.associate { item ->
                val leagueId = item["league"]["id"].asInt()
                val season = item["seasons"]
                    ?.firstOrNull { it["current"]?.asBoolean() == true }
                    ?.get("year")?.asInt() ?: -1
                leagueId to season
            }
            ?.filterValues { it != -1 }
            ?: emptyMap()
    }

    private fun parseFixtures(rawResponse: String, leagueName: String): List<FixtureCacheRow> {
        val root = objectMapper.readTree(rawResponse)
        return root["response"]?.mapNotNull { item ->
            try {
                val fixture = item["fixture"]
                val league = item["league"]
                val teams = item["teams"]
                val kickOff = OffsetDateTime.parse(fixture["date"].asText()).toInstant()

                FixtureCacheRow(
                    providerMatchId = fixture["id"].asText(),
                    providerId = "api-football",
                    leagueId = league["id"].asText(),
                    leagueName = leagueName,
                    homeTeam = teams["home"]["name"].asText(),
                    awayTeam = teams["away"]["name"].asText(),
                    kickOff = kickOff,
                    rawPayload = item.toString(),
                    expiresAt = kickOff.plus(Duration.ofHours(3)),
                    season = league["season"].asText()
                )
            } catch (e: Exception) {
                logger.warn("Skipping unparseable fixture in $leagueName: ${e.message}")
                null
            }
        } ?: emptyList()
    }
}