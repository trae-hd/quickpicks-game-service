package io.qplay.quickpicksgameservice.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.qplay.quickpicksgameservice.domain.entry.*
import io.qplay.quickpicksgameservice.domain.round.Outcome
import io.qplay.quickpicksgameservice.domain.round.Round
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.domain.slate.Match
import io.qplay.quickpicksgameservice.domain.slate.Slate
import io.qplay.quickpicksgameservice.domain.trending.RedisTrendingStore
import io.qplay.quickpicksgameservice.infra.wallet.*
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class EntryServiceWalletTest {

    private val entryRepository = mockk<EntryRepository>()
    private val roundRepository = mockk<RoundRepository>()
    private val tenantRepository = mockk<TenantRepository>()
    private val walletClient = mockk<HmacWalletClient>()
    private val walletTransactionRepository = mockk<WalletTransactionRepository>(relaxed = true)
    private val trendingStore = mockk<RedisTrendingStore>(relaxed = true)
    private val targetingService = mockk<TargetingService>(relaxed = true)
    private val outboxService = mockk<io.qplay.quickpicksgameservice.service.outbox.OutboxService>(relaxed = true)
    private val playerProfileService = mockk<PlayerProfileService>(relaxed = true)
    private val firstEntryFreeService = mockk<FirstEntryFreeService>(relaxed = true)
    private val exclusionEnforcer = mockk<ExclusionEnforcer>(relaxed = true)
    private val auditService = mockk<AuditService>(relaxed = true)
    private val draftService = mockk<DraftService>(relaxed = true)

    private val entryService = EntryService(
        entryRepository,
        roundRepository,
        tenantRepository,
        walletClient,
        walletTransactionRepository,
        trendingStore,
        targetingService,
        outboxService,
        playerProfileService,
        firstEntryFreeService,
        exclusionEnforcer,
        auditService,
        draftService
    )

    private val tenant = Tenant(id = "t1", name = "T1", jwtSecret = "s", walletBaseUrl = "b", walletHmacSecret = "h")
    private val roundId = UUID.randomUUID()
    private val round = mockk<Round>(relaxed = true)
    private val slate = mockk<Slate>()

    @BeforeEach
    fun setup() {
        TenantContext.setTenantId("t1")
        every { tenantRepository.findById("t1") } returns Optional.of(tenant)
        every { roundRepository.findById(roundId) } returns Optional.of(round)
        every { roundRepository.save(any()) } returns round
        every { round.status } returns RoundStatus.OPEN
        every { round.id } returns roundId
        every { round.tenantId } returns "t1"
        every { round.slate } returns slate
        
        val matches = (1..12).map { 
            val m = mockk<Match>()
            every { m.providerMatchId } returns "m-$it"
            m
        }
        every { slate.matches } returns matches.toMutableList()
    }

    @Test
    fun `should debit wallet before creating entry`() {
        val picks = PicksPayload((1..12).map { Pick("m-$it", Outcome.HOME) })

        every { entryRepository.findByTenantIdAndIdempotencyKey(any(), any()) } returns null
        every { targetingService.checkAccess(any(), any(), any()) } returns AccessResult.ALLOWED
        every { firstEntryFreeService.getEligiblePromotion(any(), any(), any()) } returns null
        every { walletTransactionRepository.save(any<WalletTransaction>()) } answers { it.invocation.args[0] as WalletTransaction }
        every { walletClient.debit(any(), any()) } returns WalletResponse("SUCCESS", "tx-123", null, null)
        every { walletClient.classify(any()) } returns WalletResponseClassification.SUCCESS
        every { entryRepository.save(any()) } answers { it.invocation.args[0] as Entry }

        val entry = entryService.createEntry(roundId, "p1", picks, "GBP", "idemp-1")

        assertEquals("tx-123", entry.transactionId)
        verify { walletClient.debit(tenant, any()) }
        verify { trendingStore.increment(any(), any(), any()) }
    }
}
