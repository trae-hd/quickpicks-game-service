package io.qplay.quickpicksgameservice.controller

import io.qplay.quickpicksgameservice.domain.sportsfeed.LeagueDto
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.service.FixtureCacheService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
@Tag(name = "Admin - Fixture Cache", description = "Fixture cache discovery endpoints for the Slate Builder UI")
class FixtureCacheController(
    private val fixtureCacheService: FixtureCacheService
) {
    @GetMapping("/seasons")
    @Operation(
        summary = "List available seasons",
        description = "Returns the distinct list of seasons present in the fixture cache, ordered most recent first. Use the returned values as the season parameter for the leagues and fixture search endpoints."
    )
    fun getAvailableSeasons(): ApiResponse<List<String>> {
        val seasons = fixtureCacheService.getAvailableSeasons()
        return ApiResponse(data = seasons, meta = mapOf("count" to seasons.size))
    }

    @GetMapping("/leagues")
    @Operation(
        summary = "List available leagues",
        description = "Returns the distinct list of leagues present in the fixture cache, ordered alphabetically. Pass the season parameter to restrict the list to leagues available for a specific season. Each entry includes both the provider league ID (for API calls) and the display name (for UI rendering)."
    )
    fun getAvailableLeagues(
        @Parameter(description = "Season year, e.g. '2025'. If omitted, leagues from all seasons are returned.")
        @RequestParam season: String? = null
    ): ApiResponse<List<LeagueDto>> {
        val leagues = fixtureCacheService.getAvailableLeagues(season)
        return ApiResponse(data = leagues, meta = mapOf("count" to leagues.size))
    }
}