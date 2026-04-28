package io.qplay.quickpicksgameservice.integration

import io.qplay.quickpicksgameservice.domain.entry.EntryRepository
import io.qplay.quickpicksgameservice.domain.round.RoundRepository
import io.qplay.quickpicksgameservice.domain.slate.SlateRepository
import io.qplay.quickpicksgameservice.service.MatchData
import io.qplay.quickpicksgameservice.service.SlateService
import io.qplay.quickpicksgameservice.tenant.TenantContext
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest
@ActiveProfiles("dev")
class TenantIsolationTest {

    @Autowired private lateinit var tenantRepository: TenantRepository
    @Autowired private lateinit var slateService: SlateService
    @Autowired private lateinit var roundRepository: RoundRepository
    @Autowired private lateinit var slateRepository: SlateRepository
    @Autowired private lateinit var entryRepository: EntryRepository

    @BeforeEach
    fun setup() {
        tenantRepository.save(Tenant(id = "tenant-a", name = "Tenant A", jwtSecret = "secret", walletBaseUrl = "http://localhost", walletHmacSecret = "hmac"))
        tenantRepository.save(Tenant(id = "tenant-b", name = "Tenant B", jwtSecret = "secret", walletBaseUrl = "http://localhost", walletHmacSecret = "hmac"))
    }

    @AfterEach
    fun cleanup() {
        // Each deleteAll() uses its own connection; TenantAwareDataSource sets SET LOCAL per connection,
        // so RLS applies correctly for each call. Delete in FK-safe order: entries → rounds → slates.
        for (tenantId in listOf("tenant-a", "tenant-b")) {
            TenantContext.setTenantId(tenantId)
            entryRepository.deleteAll()
            roundRepository.deleteAll()
            slateRepository.deleteAll()
        }
        TenantContext.clear()
        tenantRepository.deleteById("tenant-a")
        tenantRepository.deleteById("tenant-b")
    }

    @Test
    fun `tenant A should not see tenant B rounds`() {
        // Create slate and round for Tenant B
        TenantContext.setTenantId("tenant-b")
        val slateB = slateService.createDraft(Instant.now(), Instant.now().plusSeconds(3600), "operator-b")
        (1..12).forEach {
            slateService.addMatch(slateB.id!!, MatchData("match-$it", "Home", "Away", Instant.now().plusSeconds(1800), "League"))
        }
        slateService.submitForApproval(slateB.id!!)
        val publishedSlateB = slateService.approveAndPublish(slateB.id!!, "approver-b")
        val roundB = roundRepository.findBySlateId(publishedSlateB.id!!).get()

        // Switch to Tenant A — RLS should filter out Tenant B's data
        TenantContext.setTenantId("tenant-a")

        val roundsForA = roundRepository.findAll()
        assertEquals(0, roundsForA.size, "Tenant A should see 0 rounds")

        val roundBFromA = roundRepository.findById(roundB.id!!)
        assertEquals(false, roundBFromA.isPresent, "Tenant A should not find Tenant B round by ID")
    }
}