package io.qplay.quickpicksgameservice.domain.round

import io.qplay.quickpicksgameservice.domain.slate.Slate
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(name = "rounds")
class Round(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slate_id", nullable = false)
    val slate: Slate,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "jackpot_pool_pence", nullable = false)
    var jackpotPoolPence: Long = 0,

    @Column(name = "eleven_pool_pence", nullable = false)
    var elevenPoolPence: Long = 0,

    @Column(name = "ten_pool_pence", nullable = false)
    var tenPoolPence: Long = 0,

    @Column(name = "rollover_in_pence", nullable = false)
    var rolloverInPence: Long = 0,

    @Column(name = "remainder_pence", nullable = false)
    var remainderPence: Long = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_not_flags", columnDefinition = "jsonb")
    var requiredNotFlags: String = "[]",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RoundStatus = RoundStatus.OPEN,

    @Column(name = "locked_at")
    var lockedAt: Instant? = null,

    @Column(name = "full_time_at")
    var fullTimeAt: Instant? = null,

    @Column(name = "settle_after")
    var settleAfter: Instant? = null,

    @Column(name = "settled_at")
    var settledAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
)
