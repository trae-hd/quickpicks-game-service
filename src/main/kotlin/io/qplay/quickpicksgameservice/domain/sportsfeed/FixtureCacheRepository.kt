package io.qplay.quickpicksgameservice.domain.sportsfeed

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class FixtureCacheRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun findDistinctSeasons(): List<String> {
        val sql = """
            SELECT DISTINCT season
            FROM fixture_cache
            WHERE season != ''
            ORDER BY season DESC
        """.trimIndent()
        return jdbcTemplate.queryForList(sql, emptyMap<String, Any>(), String::class.java)
    }

    fun findDistinctLeagues(season: String?): List<LeagueDto> {
        val sql = buildString {
            append("SELECT DISTINCT league_id, league_name FROM fixture_cache")
            if (season != null) append(" WHERE season = :season")
            append(" ORDER BY league_name ASC")
        }
        val params = if (season != null) mapOf("season" to season) else emptyMap()
        return jdbcTemplate.query(sql, params) { rs, _ ->
            LeagueDto(
                leagueId = rs.getString("league_id"),
                leagueName = rs.getString("league_name")
            )
        }
    }

    fun deleteExpired(): Int {
        val sql = "DELETE FROM fixture_cache WHERE expires_at < now()"
        return jdbcTemplate.update(sql, emptyMap<String, Any>())
    }

    fun upsertFixtures(fixtures: List<FixtureCacheRow>): Int {
        if (fixtures.isEmpty()) return 0
        val sql = """
            INSERT INTO fixture_cache (
                provider_match_id, provider_id, league_id, league_name,
                home_team, away_team, kick_off, raw_payload, expires_at, season, created_at, updated_at
            ) VALUES (
                :providerMatchId, :providerId, :leagueId, :leagueName,
                :homeTeam, :awayTeam, :kickOff, CAST(:rawPayload AS jsonb), :expiresAt, :season, now(), now()
            ) ON CONFLICT (provider_match_id) DO UPDATE SET
                league_name = EXCLUDED.league_name,
                home_team = EXCLUDED.home_team,
                away_team = EXCLUDED.away_team,
                kick_off = EXCLUDED.kick_off,
                raw_payload = EXCLUDED.raw_payload,
                expires_at = EXCLUDED.expires_at,
                season = EXCLUDED.season,
                updated_at = now()
        """.trimIndent()

        val batchParams = fixtures.map { f ->
            MapSqlParameterSource()
                .addValue("providerMatchId", f.providerMatchId)
                .addValue("providerId", f.providerId)
                .addValue("leagueId", f.leagueId)
                .addValue("leagueName", f.leagueName)
                .addValue("homeTeam", f.homeTeam)
                .addValue("awayTeam", f.awayTeam)
                .addValue("kickOff", Timestamp.from(f.kickOff))
                .addValue("rawPayload", f.rawPayload)
                .addValue("expiresAt", Timestamp.from(f.expiresAt))
                .addValue("season", f.season)
        }.toTypedArray()

        return jdbcTemplate.batchUpdate(sql, batchParams).sum()
    }
}