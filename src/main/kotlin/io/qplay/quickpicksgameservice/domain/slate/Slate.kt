package io.qplay.quickpicksgameservice.domain.slate

import io.qplay.quickpicksgameservice.domain.round.Outcome
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(name = "slates")
class Slate(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SlateStatus = SlateStatus.DRAFT,

    @Column(name = "round_window_start", nullable = false)
    var roundWindowStart: Instant,

    @Column(name = "round_window_end", nullable = false)
    var roundWindowEnd: Instant,

    @Column(name = "created_by", nullable = false)
    val createdBy: String,

    @Column(name = "approved_by")
    var approvedBy: String? = null,

    @OneToMany(mappedBy = "slate", cascade = [CascadeType.ALL], orphanRemoval = true)
    var matches: MutableList<Match> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
)

@Entity
@Table(name = "matches")
class Match(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slate_id", nullable = false)
    val slate: Slate,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "provider_match_id", nullable = false)
    val providerMatchId: String,

    @Column(name = "home_team", nullable = false)
    val homeTeam: String,

    @Column(name = "away_team", nullable = false)
    val awayTeam: String,

    @Column(name = "kick_off", nullable = false)
    val kickOff: Instant,

    @Column(nullable = false)
    val league: String,

    @Column(name = "regulation_result_home")
    var regulationResultHome: Int? = null,

    @Column(name = "regulation_result_away")
    var regulationResultAway: Int? = null,

    @Column(nullable = false)
    var status: String = "SCHEDULED",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_unknown_payload", columnDefinition = "jsonb")
    var statusUnknownPayload: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
)
