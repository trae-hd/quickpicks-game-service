package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.Entry
import io.qplay.quickpicksgameservice.domain.entry.PicksPayload
import io.qplay.quickpicksgameservice.domain.entry.Pick
import io.qplay.quickpicksgameservice.domain.round.Outcome
import io.qplay.quickpicksgameservice.domain.round.Round
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.slate.Slate
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GoldenGoalCalculatorTest {

    private fun makeEntry(tiebreaker: Int?): Entry {
        val slate = Slate(
            id = UUID.randomUUID(),
            tenantId = "t1",
            roundWindowStart = Instant.now(),
            roundWindowEnd = Instant.now(),
            createdBy = "admin"
        )
        val round = Round(id = UUID.randomUUID(), slate = slate, tenantId = "t1")
        return Entry(
            id = UUID.randomUUID(),
            round = round,
            tenantId = "t1",
            playerId = "p1",
            picks = PicksPayload((1..12).map { Pick("m-$it", Outcome.HOME) }),
            stakePence = 100,
            currency = "GBP",
            tiebreaker = tiebreaker
        )
    }

    @Test
    fun `no jackpot entries produces no bonus`() {
        val result = GoldenGoalCalculator.calculateGoldenGoalWinners(emptyList(), 18)
        assertTrue(result is GoldenGoalResult.NoBonusPaid)
        assertEquals("No 12/12 entries", (result as GoldenGoalResult.NoBonusPaid).reason)
    }

    @Test
    fun `exact tiebreaker match wins full bonus`() {
        val entry = makeEntry(18)
        val result = GoldenGoalCalculator.calculateGoldenGoalWinners(listOf(entry), 18, 500_000L)
        assertTrue(result is GoldenGoalResult.BonusPaid)
        val paid = result as GoldenGoalResult.BonusPaid
        assertEquals(listOf(entry), paid.winners)
        assertEquals(500_000L, paid.sharePence)
        assertEquals(0L, paid.remainderPence)
    }

    @Test
    fun `bonus split equally among multiple exact tiebreaker matches`() {
        val entries = listOf(makeEntry(18), makeEntry(18), makeEntry(18))
        val result = GoldenGoalCalculator.calculateGoldenGoalWinners(entries, 18, 500_000L)
        val paid = result as GoldenGoalResult.BonusPaid
        assertEquals(3, paid.winners.size)
        assertEquals(166_666L, paid.sharePence)
        assertEquals(2L, paid.remainderPence)
    }

    @Test
    fun `closest without going over wins when no exact match`() {
        val e1 = makeEntry(15)  // under
        val e2 = makeEntry(17)  // closest under
        val e3 = makeEntry(20)  // over
        val result = GoldenGoalCalculator.calculateGoldenGoalWinners(listOf(e1, e2, e3), 18, 500_000L)
        val paid = result as GoldenGoalResult.BonusPaid
        assertEquals(listOf(e2), paid.winners)
        assertEquals(500_000L, paid.sharePence)
    }

    @Test
    fun `all tiebreakers exceed actual total produces no bonus`() {
        val entries = listOf(makeEntry(25), makeEntry(30))
        val result = GoldenGoalCalculator.calculateGoldenGoalWinners(entries, 18)
        assertTrue(result is GoldenGoalResult.NoBonusPaid)
        assertTrue((result as GoldenGoalResult.NoBonusPaid).reason.contains("exceeded"))
    }

    @Test
    fun `null tiebreaker entries are ignored in closest-match calculation`() {
        val withNull = makeEntry(null)
        val withValue = makeEntry(15)
        val result = GoldenGoalCalculator.calculateGoldenGoalWinners(listOf(withNull, withValue), 18, 500_000L)
        val paid = result as GoldenGoalResult.BonusPaid
        assertEquals(listOf(withValue), paid.winners)
    }
}