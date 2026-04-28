package io.qplay.quickpicksgameservice.domain.sportsfeed

import com.jayway.jsonpath.JsonPath
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class FeedMapper {

    fun mapToMatch(
        rawJson: String,
        fieldMappings: Map<String, String>,
        statusTranslations: Map<String, CanonicalMatchStatus>
    ): CanonicalMatch {
        val document = JsonPath.parse(rawJson)

        val providerMatchId = document.read<Any>(fieldMappings["match_id"]).toString()
        val homeTeam = document.read<String>(fieldMappings["home_team"])
        val awayTeam = document.read<String>(fieldMappings["away_team"])
        val kickOffStr = document.read<String>(fieldMappings["kick_off"])
        val kickOff = OffsetDateTime.parse(kickOffStr)
        
        val providerStatus = document.read<String>(fieldMappings["status"])
        val canonicalStatus = statusTranslations[providerStatus] ?: CanonicalMatchStatus.SCHEDULED

        val homeScore = readNullableInt(document, fieldMappings["home_score"])
        val awayScore = readNullableInt(document, fieldMappings["away_score"])

        return CanonicalMatch(
            providerMatchId = providerMatchId,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            kickOff = kickOff,
            status = canonicalStatus,
            regulationResultHome = homeScore,
            regulationResultAway = awayScore
        )
    }

    private fun readNullableInt(document: com.jayway.jsonpath.DocumentContext, path: String?): Int? {
        if (path == null) return null
        return try {
            val value = document.read<Any>(path)
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class CanonicalMatch(
    val providerMatchId: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickOff: OffsetDateTime,
    val status: CanonicalMatchStatus,
    val regulationResultHome: Int?,
    val regulationResultAway: Int?
)
