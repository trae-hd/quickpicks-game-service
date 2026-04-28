package io.qplay.quickpicksgameservice.domain.sportsfeed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class FeedMapperTest {

    private val feedMapper = FeedMapper()

    @Test
    fun `should map real api-football fixture response to match`() {
        val rawJson = """
        {
          "fixture": {
            "id": 12345,
            "date": "2026-04-19T15:00:00+00:00",
            "status": { "short": "FT" }
          },
          "teams": {
            "home": { "name": "Arsenal" },
            "away": { "name": "Chelsea" }
          },
          "score": {
            "fulltime": { "home": 2, "away": 1 }
          }
        }
        """.trimIndent()

        val fieldMappings = mapOf(
            "match_id" to "$.fixture.id",
            "home_team" to "$.teams.home.name",
            "away_team" to "$.teams.away.name",
            "kick_off" to "$.fixture.date",
            "status" to "$.fixture.status.short",
            "home_score" to "$.score.fulltime.home",
            "away_score" to "$.score.fulltime.away"
        )

        val statusTranslations = mapOf(
            "FT" to CanonicalMatchStatus.FINISHED
        )

        val match = feedMapper.mapToMatch(rawJson, fieldMappings, statusTranslations)

        assertEquals("12345", match.providerMatchId)
        assertEquals("Arsenal", match.homeTeam)
        assertEquals("Chelsea", match.awayTeam)
        assertEquals(OffsetDateTime.parse("2026-04-19T15:00:00Z"), match.kickOff)
        assertEquals(CanonicalMatchStatus.FINISHED, match.status)
        assertEquals(2, match.regulationResultHome)
        assertEquals(1, match.regulationResultAway)
    }
}
