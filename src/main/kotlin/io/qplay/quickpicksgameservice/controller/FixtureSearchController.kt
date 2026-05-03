package io.qplay.quickpicksgameservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.qplay.quickpicksgameservice.exception.ApiResponse
import io.qplay.quickpicksgameservice.infra.feed.ApiFootballClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Profile("api")
@RequestMapping("/api/v1/admin/fixtures")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'REVIEWER')")
@Tag(name = "Admin - Fixtures", description = "Operator fixture search for the Slate Builder UI")
class FixtureSearchController(
    private val apiFootballClient: ApiFootballClient,
    private val objectMapper: ObjectMapper
) {
    @GetMapping("/search")
    @Operation(summary = "Search fixtures from the provider feed for a given league, season and date range")
    fun searchFixtures(
        @RequestParam league: String,
        @RequestParam season: String,
        @RequestParam from: String,
        @RequestParam to: String
    ): ApiResponse<List<FixtureSearchResult>> {
        val rawJson = apiFootballClient.getFixtures(league, season, from, to)
        val root = objectMapper.readTree(rawJson)
        val fixtures = root.path("response").map { node ->
            FixtureSearchResult(
                providerMatchId = node.path("fixture").path("id").asText(),
                homeTeam = node.path("teams").path("home").path("name").asText(),
                awayTeam = node.path("teams").path("away").path("name").asText(),
                kickOff = node.path("fixture").path("date").asText(),
                league = node.path("league").path("name").asText(),
                status = node.path("fixture").path("status").path("short").asText()
            )
        }
        return ApiResponse(fixtures)
    }
}

data class FixtureSearchResult(
    val providerMatchId: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickOff: String,
    val league: String,
    val status: String
)