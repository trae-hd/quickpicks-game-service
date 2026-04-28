package io.qplay.quickpicksgameservice.service

import io.mockk.*
import io.qplay.quickpicksgameservice.domain.entry.*
import io.qplay.quickpicksgameservice.domain.round.*
import io.qplay.quickpicksgameservice.domain.slate.Match
import io.qplay.quickpicksgameservice.domain.slate.Slate
import io.qplay.quickpicksgameservice.domain.sportsfeed.*
import io.qplay.quickpicksgameservice.infra.feed.ApiFootballClient
import io.qplay.quickpicksgameservice.infra.wallet.HmacWalletClient
import io.qplay.quickpicksgameservice.infra.wallet.WalletResponse
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class SettlementServiceTest {
    private val roundRepository = mockk<RoundRepository>()
    private val entryRepository = mockk<EntryRepository>()
    private val entryResultRepository = mockk<EntryResultRepository>()
    private val tenantRepository = mockk<TenantRepository>()
    private val walletClient = mockk<HmacWalletClient>()
    private val redisTemplate = mockk<StringRedisTemplate>()
    private val valueOps = mockk<ValueOperations<String, String>>()
    private val outboxService = mockk<io.qplay.quickpicksgameservice.service.outbox.OutboxService>(relaxed = true)
    private val apiFootballClient = mockk<ApiFootballClient>()
    private val feedMapper = mockk<FeedMapper>()
    private val fieldMappingRepository = mockk<FeedFieldMappingRepository>()
    private val statusTranslationRepository = mockk<FeedStatusTranslationRepository>()

    private val settlementService = SettlementService(
        roundRepository,
        entryRepository,
        entryResultRepository,
        tenantRepository,
        walletClient,
        redisTemplate,
        outboxService,
        apiFootballClient,
        feedMapper,
        fieldMappingRepository,
        statusTranslationRepository
    )

    @Test
    fun `should calculate correct picks and issue credits`() {
        val tenantId = "tenant-1"
        val slateId = UUID.randomUUID()
        val roundId = UUID.randomUUID()
        
        val tenant = Tenant(
            id = tenantId, 
            name = "Tenant 1", 
            jwtSecret = "secret", 
            walletBaseUrl = "http://wallet", 
            walletHmacSecret = "secret"
        )
        val slate = Slate(id = slateId, tenantId = tenantId, roundWindowStart = Instant.now(), roundWindowEnd = Instant.now(), createdBy = "admin")
        
        val matches = (1..12).map { i ->
            Match(
                id = UUID.randomUUID(),
                slate = slate,
                tenantId = tenantId,
                providerMatchId = "$i",
                homeTeam = "Home $i",
                awayTeam = "Away $i",
                kickOff = Instant.now(),
                league = "PL",
                regulationResultHome = 1,
                regulationResultAway = 0, // All Home Wins
                status = "FINISHED"
            )
        }
        slate.matches = matches.toMutableList()

        val round = Round(
            id = roundId,
            slate = slate,
            tenantId = tenantId,
            status = RoundStatus.LOCKED,
            settleAfter = Instant.now().minusSeconds(1),
            jackpotPoolPence = 1200,
            elevenPoolPence = 1100,
            tenPoolPence = 1000
        )

        val entry = Entry(
            id = UUID.randomUUID(),
            round = round,
            tenantId = tenantId,
            playerId = "player-1",
            picks = PicksPayload(matches.map { Pick(it.providerMatchId, Outcome.HOME) }), // 12/12 correct
            stakePence = 100,
            currency = "GBP"
        )

        every { roundRepository.findByStatus(RoundStatus.LOCKED) } returns listOf(round)
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.setIfAbsent(any(), any(), any()) } returns true
        every { redisTemplate.execute(any<org.springframework.data.redis.core.script.RedisScript<Long>>(), any(), *anyVararg()) } returns 1L
        
        // Sanity check mocks
        every { fieldMappingRepository.findByProviderId("api-football") } returns emptyList()
        every { statusTranslationRepository.findByProviderId("api-football") } returns emptyList()
        every { apiFootballClient.getFixtureById(any()) } returns "{}"
        every { feedMapper.mapToMatch(any(), any(), any()) } returnsMany (1..12).map { i ->
            CanonicalMatch("$i", "Home $i", "Away $i", OffsetDateTime.now(), CanonicalMatchStatus.FINISHED, 1, 0)
        }

        every { entryRepository.findByRoundId(roundId) } returns listOf(entry)
        every { entryResultRepository.save(any()) } returns mockk()
        every { tenantRepository.findById(tenantId) } returns Optional.of(tenant)
        every { walletClient.credit(any(), any()) } returns WalletResponse("SUCCESS", "tx-1", null, null)
        every { entryResultRepository.findById(entry.id!!) } returns Optional.of(EntryResult(entry.id!!, entry, tenantId))
        every { entryRepository.save(any()) } returns entry
        every { roundRepository.save(any()) } returns round

        settlementService.processSettlement()

        verify { walletClient.credit(tenant, match { it.amountPence == 1200L }) }
        assertEquals(RoundStatus.SETTLED, round.status)
    }
}
