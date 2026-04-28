package io.qplay.quickpicksgameservice.domain.entry

import io.mockk.mockk
import io.qplay.quickpicksgameservice.domain.round.Outcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.qplay.quickpicksgameservice.service.EntryService
import org.junit.jupiter.api.assertDoesNotThrow

class PicksPayloadTest {
    
    private val entryService = EntryService(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(relaxed = true), mockk(relaxed = true), mockk(), mockk(), mockk(), mockk(), mockk(relaxed = true))
    private val validMatches = (1..12).map { "match_$it" }

    @Test
    fun `should accept valid 12 picks`() {
        val picks = PicksPayload(
            picks = (1..12).map { 
                Pick(providerMatchId = "match_$it", outcome = Outcome.HOME) 
            }
        )
        
        assertDoesNotThrow {
            entryService.validatePicks(picks, validMatches)
        }
    }

    @Test
    fun `should reject if picks count is not 12`() {
        val picks = PicksPayload(
            picks = (1..11).map { 
                Pick(providerMatchId = "match_$it", outcome = Outcome.HOME) 
            }
        )
        
        val exception = assertThrows<IllegalArgumentException> {
            entryService.validatePicks(picks, validMatches)
        }
        assert(exception.message!!.contains("exactly 12 picks"))
    }

    @Test
    fun `should reject duplicate match IDs`() {
        val picksList = (1..11).map { 
            Pick(providerMatchId = "match_$it", outcome = Outcome.HOME) 
        }.toMutableList()
        picksList.add(Pick(providerMatchId = "match_1", outcome = Outcome.AWAY))
        
        val picks = PicksPayload(picks = picksList)
        
        val exception = assertThrows<IllegalArgumentException> {
            entryService.validatePicks(picks, validMatches)
        }
        assert(exception.message!!.contains("Duplicate match IDs"))
    }

    @Test
    fun `should reject invalid providerMatchId`() {
        val picks = PicksPayload(
            picks = (1..11).map { 
                Pick(providerMatchId = "match_$it", outcome = Outcome.HOME) 
            } + Pick(providerMatchId = "invalid_match", outcome = Outcome.HOME)
        )
        
        val exception = assertThrows<IllegalArgumentException> {
            entryService.validatePicks(picks, validMatches)
        }
        assert(exception.message!!.contains("Invalid providerMatchId"))
    }
}
