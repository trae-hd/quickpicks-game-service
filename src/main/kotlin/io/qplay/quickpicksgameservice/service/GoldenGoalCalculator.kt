package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.Entry
import java.util.UUID

sealed class GoldenGoalResult {
    data class BonusPaid(
        val winners: List<Entry>,
        val sharePence: Long,
        val remainderPence: Long
    ) : GoldenGoalResult()

    data class NoBonusPaid(val reason: String) : GoldenGoalResult()
}

object GoldenGoalCalculator {
    fun calculateGoldenGoalWinners(
        jackpotEntries: List<Entry>,  // only 12/12 entries
        actualTotalGoals: Int,
        bonusAmountPence: Long = 500_000L, // £5,000 default
    ): GoldenGoalResult {
        if (jackpotEntries.isEmpty()) {
            return GoldenGoalResult.NoBonusPaid(reason = "No 12/12 entries")
        }

        // Exact matches first
        val exactMatches = jackpotEntries.filter { (it.tiebreaker ?: -1) == actualTotalGoals }
        if (exactMatches.isNotEmpty()) {
            val share = bonusAmountPence / exactMatches.size
            val remainder = bonusAmountPence % exactMatches.size
            return GoldenGoalResult.BonusPaid(winners = exactMatches, sharePence = share, remainderPence = remainder)
        }

        // Closest-without-going-over
        val underOrEqual = jackpotEntries.filter { 
            val tb = it.tiebreaker
            tb != null && tb <= actualTotalGoals 
        }
        if (underOrEqual.isEmpty()) {
            // Every 12/12 entry went over — no bonus paid, rolls to reserve
            return GoldenGoalResult.NoBonusPaid(reason = "All tiebreakers exceeded actual total")
        }

        val closestValue = underOrEqual.maxOf { it.tiebreaker ?: 0 }
        val closestEntries = underOrEqual.filter { it.tiebreaker == closestValue }
        val share = bonusAmountPence / closestEntries.size
        val remainder = bonusAmountPence % closestEntries.size
        return GoldenGoalResult.BonusPaid(winners = closestEntries, sharePence = share, remainderPence = remainder)
    }
}
