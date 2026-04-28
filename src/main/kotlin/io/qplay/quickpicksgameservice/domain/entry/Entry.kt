package io.qplay.quickpicksgameservice.domain.entry

import io.qplay.quickpicksgameservice.domain.round.PrizeTier
import io.qplay.quickpicksgameservice.domain.round.Round
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(name = "entries")
class Entry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    val round: Round,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "player_id", nullable = false)
    val playerId: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "picks_json", nullable = false, columnDefinition = "jsonb")
    val picks: PicksPayload,

    @Column(name = "tiebreaker")
    val tiebreaker: Int? = null,

    @Column(name = "stake_pence", nullable = false)
    val stakePence: Long,

    @Column(nullable = false)
    val currency: String,

    @Column(name = "is_free_entry", nullable = false)
    val isFreeEntry: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EntryStatus = EntryStatus.PENDING,

    @Column(name = "transaction_id")
    var transactionId: String? = null,

    @Column(name = "share_token")
    var shareToken: String? = null,

    @Column(name = "idempotency_key")
    val idempotencyKey: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
)

@Entity
@Table(name = "entry_results")
class EntryResult(
    @Id
    @Column(name = "entry_id")
    val entryId: UUID,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "entry_id")
    val entry: Entry,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "correct_picks", nullable = false)
    var correctPicks: Int = 0,

    @Column(name = "is_jackpot_winner", nullable = false)
    var isJackpotWinner: Boolean = false,

    @Column(name = "prize_pence", nullable = false)
    var prizePence: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "prize_tier")
    var prizeTier: PrizeTier? = null,

    @Column(name = "settled_at")
    var settledAt: Instant? = null,

    @Column(name = "credit_issued", nullable = false)
    var creditIssued: Boolean = false,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
)
