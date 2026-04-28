package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.infra.wallet.TransactionStatus
import io.qplay.quickpicksgameservice.infra.wallet.TransactionType
import io.qplay.quickpicksgameservice.infra.wallet.WalletTransactionRepository
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
@Profile("worker-reconciliation")
class ReconciliationService(
    private val tenantRepository: TenantRepository,
    private val walletTransactionRepository: WalletTransactionRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(ReconciliationService::class.java)

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "reconciliation-hourly", lockAtMostFor = "50m", lockAtLeastFor = "1m")
    fun runAtTenantLocalMidnight() {
        val now = OffsetDateTime.now()
        tenantRepository.findAll().forEach { tenant ->
            val tenantHour = now.atZoneSameInstant(ZoneId.of(tenant.primaryTimezone)).hour
            if (tenantHour == 3) {
                reconcileForTenant(tenant)
            }
        }
    }

    fun performReconciliation() {
        tenantRepository.findAll().forEach { reconcileForTenant(it) }
    }

    private fun reconcileForTenant(tenant: Tenant) {
        logger.info("Starting reconciliation for tenant ${tenant.id}")
        
        // 1. Fetch host statement (Mocked for POC)
        val hostTransactions = fetchHostStatement(tenant)
        
        // 2. Fetch our successful transactions (DEBIT/CREDIT, skip PROMOTION) for the last 24h
        val ourTransactions = walletTransactionRepository.findAll() // In real: use date range and tenant filter
            .filter { it.tenantId == tenant.id && it.status == TransactionStatus.SUCCESS && it.type != TransactionType.PROMOTION }

        val ourTxMap = ourTransactions.associateBy { it.providerTxId ?: it.idempotencyKey }
        val hostTxMap = hostTransactions.associateBy { it.id }

        // 3. Compare
        ourTransactions.forEach { ourTx ->
            val hostTx = hostTxMap[ourTx.providerTxId]
            if (hostTx == null) {
                logException(tenant.id, ourTx.playerId, ourTx.type.name, ourTx.amountPence, null, "MISSING_ON_HOST", "Missing providerTxId: ${ourTx.providerTxId}")
            } else if (hostTx.amountPence != ourTx.amountPence) {
                logException(tenant.id, ourTx.playerId, ourTx.type.name, ourTx.amountPence, hostTx.amountPence, "AMOUNT_MISMATCH", "Host amount: ${hostTx.amountPence}")
            }
        }

        hostTransactions.forEach { hostTx ->
            if (!ourTxMap.containsKey(hostTx.id)) {
                logException(tenant.id, hostTx.playerId, hostTx.type, 0, hostTx.amountPence, "MISSING_ON_US", "Host txn ID: ${hostTx.id}")
            }
        }
    }

    private fun logException(tenantId: String, playerId: String, type: String, expected: Long, actual: Long?, exceptionType: String, notes: String) {
        jdbcTemplate.update("""
            INSERT INTO reconciliation_exceptions (tenant_id, player_id, transaction_type, expected_amount_pence, actual_amount_pence, exception_type, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, tenantId, playerId, type, expected, actual, exceptionType, notes)
    }

    private fun fetchHostStatement(tenant: Tenant): List<HostTransaction> {
        // POC stub — real implementation requires a tenant-specific wallet statement API endpoint.
        // Until integrated, reconciliation will only detect entries present on our side but
        // missing from the host (MISSING_ON_HOST), not the reverse direction.
        logger.warn("fetchHostStatement is a POC stub for tenant ${tenant.id} — host-side cross-check skipped")
        return emptyList()
    }
}

data class HostTransaction(
    val id: String,
    val playerId: String,
    val amountPence: Long,
    val type: String
)
