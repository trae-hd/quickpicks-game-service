package io.qplay.quickpicksgameservice.domain.sportsfeed

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

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
}