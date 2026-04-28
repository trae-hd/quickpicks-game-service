package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.entry.EntryRepository
import io.qplay.quickpicksgameservice.domain.entry.EntryResultRepository
import io.qplay.quickpicksgameservice.domain.entry.EntryStatus
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.tenant.TenantContext
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/players/me")
class PlayerHistoryController(
    private val entryRepository: EntryRepository,
    private val entryResultRepository: EntryResultRepository
) {

    @GetMapping("/history")
    fun getHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<List<Map<String, Any?>>> {
        val playerId = TenantContext.getRequiredPlayerId()
        val pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending())
        
        val entriesPage = entryRepository.findByPlayerIdAndStatusIn(
            playerId, 
            listOf(EntryStatus.SETTLED, EntryStatus.VOID), 
            pageRequest
        )

        val entryIds = entriesPage.content.mapNotNull { it.id }
        val results = if (entryIds.isNotEmpty()) {
            entryResultRepository.findByEntryIdIn(entryIds).associateBy { it.entryId }
        } else {
            emptyMap()
        }

        val data = entriesPage.content.map { entry ->
            mapOf(
                "id" to entry.id,
                "roundId" to entry.round.id,
                "status" to entry.status,
                "stakePence" to entry.stakePence,
                "currency" to entry.currency,
                "createdAt" to entry.createdAt,
                "picks" to entry.picks.picks,
                "result" to results[entry.id]?.let {
                    mapOf(
                        "correctPicks" to it.correctPicks,
                        "prizePence" to it.prizePence,
                        "prizeTier" to it.prizeTier,
                        "settledAt" to it.settledAt
                    )
                }
            )
        }

        return ApiResponse(
            data = data,
            meta = mapOf(
                "totalElements" to entriesPage.totalElements,
                "totalPages" to entriesPage.totalPages,
                "page" to entriesPage.number,
                "size" to entriesPage.size
            )
        )
    }

    @GetMapping("/active")
    fun getActive(): ApiResponse<List<Map<String, Any?>>> {
        val playerId = TenantContext.getRequiredPlayerId()
        val entries = entryRepository.findByPlayerIdAndStatusIn(
            playerId,
            listOf(EntryStatus.PENDING),
            PageRequest.of(0, 100, Sort.by("createdAt").descending())
        )

        val data = entries.content.map { entry ->
            mapOf(
                "id" to entry.id,
                "roundId" to entry.round.id,
                "status" to entry.status,
                "stakePence" to entry.stakePence,
                "currency" to entry.currency,
                "createdAt" to entry.createdAt,
                "picks" to entry.picks.picks
            )
        }

        return ApiResponse(data = data)
    }
}
