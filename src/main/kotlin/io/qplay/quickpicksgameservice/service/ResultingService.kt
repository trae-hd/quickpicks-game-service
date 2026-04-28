package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.slate.MatchRepository
import io.qplay.quickpicksgameservice.domain.sportsfeed.*
import io.qplay.quickpicksgameservice.infra.feed.ApiFootballClient
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class ResultingService(
    private val roundRepository: RoundRepository,
    private val matchRepository: MatchRepository,
    private val apiFootballClient: ApiFootballClient,
    private val feedMapper: FeedMapper,
    private val fieldMappingRepository: FeedFieldMappingRepository,
    private val statusTranslationRepository: FeedStatusTranslationRepository,
    private val redisTemplate: StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(ResultingService::class.java)

    @Transactional
    fun pollResultsForActiveRounds() {
        val activeRounds = roundRepository.findByStatus(RoundStatus.LOCKED)
        activeRounds.forEach { round ->
            val roundSlateId = round.slate.id ?: throw IllegalStateException("Round slate ID must not be null for resulting")
            val matches = matchRepository.findBySlateId(roundSlateId)
            var allFinished = true

            matches.forEach { match ->
                if (match.status != "FINISHED" && match.status != "CANCELLED" && match.status != "POSTPONED") {
                    val rawJson = apiFootballClient.getFixtureById(match.providerMatchId.toInt())
                    val mappings = fieldMappingRepository.findByProviderId("api-football")
                        .associate { it.canonicalField to it.providerJsonPath }
                    val translations = statusTranslationRepository.findByProviderId("api-football")
                        .associate { it.providerStatus to it.canonicalStatus }

                    val canonicalMatch = feedMapper.mapToMatch(rawJson, mappings, translations)

                    if (canonicalMatch.status == CanonicalMatchStatus.FINISHED) {
                        val confirmKey = "result-confirmed:${match.providerMatchId}"
                        val alreadyConfirmed = redisTemplate.opsForValue().get(confirmKey) != null
                        if (!alreadyConfirmed) {
                            // First poll showing FINISHED — require a second poll to confirm
                            redisTemplate.opsForValue().set(confirmKey, "1", Duration.ofHours(24))
                            allFinished = false
                            return@forEach
                        }
                        redisTemplate.delete(confirmKey)
                    }

                    match.status = canonicalMatch.status.name
                    match.regulationResultHome = canonicalMatch.regulationResultHome
                    match.regulationResultAway = canonicalMatch.regulationResultAway
                    matchRepository.save(match)

                    if (canonicalMatch.status != CanonicalMatchStatus.FINISHED &&
                        canonicalMatch.status != CanonicalMatchStatus.CANCELLED &&
                        canonicalMatch.status != CanonicalMatchStatus.POSTPONED) {
                        allFinished = false
                    }
                }
            }

            if (allFinished) {
                val now = Instant.now()
                round.fullTimeAt = now
                round.settleAfter = now.plusSeconds(1800) // 30 minutes
                roundRepository.save(round)
                logger.info("Round ${round.id} reached full-time. Settlement available after ${round.settleAfter}")
            }
        }
    }
}
