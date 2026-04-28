package io.qplay.quickpicksgameservice.integration

import io.qplay.quickpicksgameservice.domain.entry.Pick
import io.qplay.quickpicksgameservice.domain.entry.PicksPayload
import io.qplay.quickpicksgameservice.domain.round.Outcome
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.infra.wallet.*
import io.qplay.quickpicksgameservice.service.EntryService
import io.qplay.quickpicksgameservice.service.MatchData
import io.qplay.quickpicksgameservice.service.SlateService
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class WalletIntegrationTest {

    @Autowired
    private lateinit var tenantRepository: TenantRepository

    @Autowired
    private lateinit var slateService: SlateService

    @Autowired
    private lateinit var roundRepository: RoundRepository

    @Autowired
    private lateinit var entryService: EntryService

    @Autowired
    private lateinit var walletTransactionRepository: WalletTransactionRepository
    
    @MockitoBean
    private lateinit var walletClient: HmacWalletClient

    @BeforeEach
    fun setup() {
        TenantContext.setTenantId("tenant-test")
        tenantRepository.save(Tenant(id = "tenant-test", name = "Test Tenant", jwtSecret = "secret", walletBaseUrl = "http://localhost", walletHmacSecret = "hmac"))
    }

    @Test
    fun `entry creation should debit wallet and record transaction`() {
        // 1. Create slate and round
        val slate = slateService.createDraft(Instant.now(), Instant.now().plusSeconds(3600), "operator")
        val matches = (1..12).map {
            slateService.addMatch(slate.id!!, MatchData("m-$it", "H", "A", Instant.now().plusSeconds(1800), "L"))
        }
        slateService.submitForApproval(slate.id!!)
        val publishedSlate = slateService.approveAndPublish(slate.id!!, "approver")
        val round = roundRepository.findBySlateId(publishedSlate.id!!).get()

        // 2. Mock successful wallet response
        whenever(walletClient.debit(any(), any())).thenReturn(WalletResponse("SUCCESS", "tx-123", null, null))
        whenever(walletClient.classify(any())).thenReturn(WalletResponseClassification.SUCCESS)

        // 3. Place entry
        val picks = PicksPayload(matches.map { Pick(it.providerMatchId, Outcome.HOME) })
        val entry = entryService.createEntry(round.id!!, "player-1", picks, "GBP", "idemp-key-1")

        // 4. Verify wallet transaction record
        val walletTxs = walletTransactionRepository.findAll()
        assertEquals(1, walletTxs.size)
        assertEquals("tx-123", walletTxs[0].providerTxId)
        assertEquals("SUCCESS", walletTxs[0].status.name)
        assertNotNull(entry.transactionId)
        assertEquals("tx-123", entry.transactionId)
    }
}
