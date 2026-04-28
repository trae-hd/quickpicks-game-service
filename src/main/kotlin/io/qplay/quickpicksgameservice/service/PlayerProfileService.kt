package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.Pick
import io.qplay.quickpicksgameservice.domain.player.PlayerProfileCounters
import io.qplay.quickpicksgameservice.domain.player.PlayerProfileCountersRepository
import io.qplay.quickpicksgameservice.domain.player.PlayerProfileId
import io.qplay.quickpicksgameservice.domain.round.Outcome
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PlayerProfileService(
    private val repository: PlayerProfileCountersRepository
) {
    @Transactional
    fun incrementEntriesPlaced(tenantId: String, playerId: String) {
        val id = PlayerProfileId(tenantId, playerId)
        val profile = repository.findById(id).orElseGet { PlayerProfileCounters(id = id) }
        val counters = profile.counters.toMutableMap()
        counters["entries_placed"] = ((counters["entries_placed"] as? Number)?.toLong() ?: 0L) + 1
        profile.counters = counters
        profile.updatedAt = OffsetDateTime.now()
        repository.save(profile)
    }

    @Transactional
    fun trackPicksAndUpdateStyle(tenantId: String, playerId: String, picks: List<Pick>, thresholdPct: Int) {
        val id = PlayerProfileId(tenantId, playerId)
        val profile = repository.findById(id).orElseGet { PlayerProfileCounters(id = id) }
        val counters = profile.counters.toMutableMap()

        val home = ((counters["home_picks"] as? Number)?.toLong() ?: 0L) + picks.count { it.outcome == Outcome.HOME }
        val away = ((counters["away_picks"] as? Number)?.toLong() ?: 0L) + picks.count { it.outcome == Outcome.AWAY }
        val draw = ((counters["draw_picks"] as? Number)?.toLong() ?: 0L) + picks.count { it.outcome == Outcome.DRAW }
        val total = home + away + draw

        counters["home_picks"] = home
        counters["away_picks"] = away
        counters["draw_picks"] = draw

        val dominant = computeDominantPlayStyle(home, away, draw, total, thresholdPct)
        if (dominant != null) counters["dominant_play_style"] = dominant
        else counters.remove("dominant_play_style")

        profile.counters = counters
        profile.updatedAt = OffsetDateTime.now()
        repository.save(profile)
    }

    private fun computeDominantPlayStyle(home: Long, away: Long, draw: Long, total: Long, thresholdPct: Int): String? {
        if (total == 0L) return null
        return when {
            home * 100 / total >= thresholdPct -> "HOME"
            away * 100 / total >= thresholdPct -> "AWAY"
            draw * 100 / total >= thresholdPct -> "DRAW"
            else -> null
        }
    }

    @Transactional(readOnly = true)
    fun getCounters(tenantId: String, playerId: String): Map<String, Any> {
        val id = PlayerProfileId(tenantId, playerId)
        return repository.findById(id).map { it.counters }.orElse(emptyMap())
    }
}
