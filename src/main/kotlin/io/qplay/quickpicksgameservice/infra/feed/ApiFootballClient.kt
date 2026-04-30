package io.qplay.quickpicksgameservice.infra.feed

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ApiFootballClient(
    @Value("\${app.feed.api-football.api-key}") private val apiKey: String,
    @Value("\${app.feed.api-football.base-url:https://v3.football.api-sports.io}") private val baseUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("x-apisports-key", apiKey)
        .build()

    fun getFixturesByLeagueAndDate(leagueId: Int, season: Int, date: String): String {
        return restClient.get()
            .uri("/fixtures?league={leagueId}&season={season}&date={date}", leagueId, season, date)
            .retrieve()
            .body(String::class.java) ?: throw RuntimeException("Empty response from api-football")
    }

    fun getFixtureById(fixtureId: Int): String {
        return restClient.get()
            .uri("/fixtures?id={fixtureId}", fixtureId)
            .retrieve()
            .body(String::class.java) ?: throw RuntimeException("Empty response from api-football")
    }

    fun getFixtures(league: String, season: String, from: String, to: String): String {
        return restClient.get()
            .uri("/fixtures?league={league}&season={season}&from={from}&to={to}", league, season, from, to)
            .retrieve()
            .body(String::class.java) ?: throw RuntimeException("Empty response from api-football")
    }

    fun getCurrentSeasons(): String {
        return restClient.get()
            .uri("/leagues?current=true")
            .retrieve()
            .body(String::class.java) ?: throw RuntimeException("Empty response from api-football")
    }
}
