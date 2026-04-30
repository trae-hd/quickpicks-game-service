package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.sportsfeed.FixtureCacheRepository
import io.qplay.quickpicksgameservice.domain.sportsfeed.LeagueDto
import org.springframework.stereotype.Service

@Service
class FixtureCacheService(
    private val fixtureCacheRepository: FixtureCacheRepository
) {
    fun getAvailableSeasons(): List<String> = fixtureCacheRepository.findDistinctSeasons()

    fun getAvailableLeagues(season: String?): List<LeagueDto> = fixtureCacheRepository.findDistinctLeagues(season)
}