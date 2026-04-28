package io.qplay.quickpicksgameservice.infra.wallet

import io.qplay.quickpicksgameservice.domain.entry.Entry
import io.qplay.quickpicksgameservice.domain.round.Round
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime
import java.util.*

enum class TransactionType {
    DEBIT, CREDIT, REFUND, PROMOTION
}

enum class TransactionStatus {
    PENDING, SUCCESS, FAILED
}

@Entity
@Table(name = "wallet_transactions")
class WalletTransaction(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "player_id", nullable = false)
    val playerId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id")
    val round: Round?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id")
    val entry: Entry?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionType,

    @Column(name = "amount_pence", nullable = false)
    val amountPence: Long,

    @Column(nullable = false)
    val currency: String,

    @Column(name = "provider_tx_id", unique = true)
    var providerTxId: String? = null,

    @Column(name = "idempotency_key", nullable = false)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TransactionStatus = TransactionStatus.PENDING,

    @Column(name = "error_code")
    var errorCode: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

interface WalletTransactionRepository : JpaRepository<WalletTransaction, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): WalletTransaction?
}
