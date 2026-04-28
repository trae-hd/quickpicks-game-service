package io.qplay.quickpicksgameservice.domain.sportsfeed

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "feed_providers")
class FeedProvider(
    @Id
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(name = "base_url", nullable = false)
    val baseUrl: String,

    @Column(name = "api_key_vault_path", nullable = false)
    val apiKeyVaultPath: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "polling_intervals_json", columnDefinition = "jsonb")
    val pollingIntervalsJson: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant? = null
)

@Entity
@Table(name = "feed_field_mappings")
class FeedFieldMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: java.util.UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    val provider: FeedProvider,

    @Column(name = "canonical_field", nullable = false)
    val canonicalField: String,

    @Column(name = "provider_json_path", nullable = false)
    val providerJsonPath: String
)

@Entity
@Table(name = "feed_status_translations")
class FeedStatusTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: java.util.UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    val provider: FeedProvider,

    @Column(name = "provider_status", nullable = false)
    val providerStatus: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "canonical_status", nullable = false)
    val canonicalStatus: CanonicalMatchStatus
)
