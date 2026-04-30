package io.qplay.quickpicksgameservice.domain.sportsfeed

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feed_league_mappings")
class FeedLeagueMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    val provider: FeedProvider,

    @Column(name = "provider_league_id", nullable = false)
    val providerLeagueId: String,

    @Column(name = "league_name", nullable = false)
    val leagueName: String,

    @Column(name = "country")
    val country: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
)