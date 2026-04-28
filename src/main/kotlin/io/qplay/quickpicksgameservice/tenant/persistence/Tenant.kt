package io.qplay.quickpicksgameservice.tenant.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "tenants")
class Tenant(
    @Id
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(name = "jwt_secret", nullable = false)
    val jwtSecret: String,

    @Column(name = "wallet_base_url", nullable = false)
    val walletBaseUrl: String,

    @Column(name = "wallet_hmac_secret", nullable = false)
    val walletHmacSecret: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_host_origins", nullable = false, columnDefinition = "jsonb")
    val allowedHostOrigins: String = "[]",

    @Column(nullable = false)
    val currency: String = "GBP",

    @Column(name = "optimove_api_key_encrypted")
    val optimoveApiKeyEncrypted: String? = null,

    @Column(name = "optimove_stream_id")
    val optimoveStreamId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_flags", nullable = false, columnDefinition = "jsonb")
    val featureFlags: String = "{}",

    @Column(name = "targeting_mode", nullable = false)
    val targetingMode: String = "OPEN",

    @Column(name = "free_entry_enabled", nullable = false)
    val freeEntryEnabled: Boolean = false,

    @Column(name = "quick_picks_launched_at")
    val quickPicksLaunchedAt: OffsetDateTime? = null,

    @Column(name = "free_entry_grace_minutes", nullable = false)
    val freeEntryGraceMinutes: Int = 120,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configured_leagues", nullable = false, columnDefinition = "jsonb")
    val configuredLeagues: String = "[]",

    @Column(name = "slate_window_hours", nullable = false)
    val slateWindowHours: Int = 48,

    @Column(name = "primary_timezone", nullable = false)
    val primaryTimezone: String = "Europe/London",

    @Column(name = "dominant_style_threshold_pct", nullable = false)
    val dominantStyleThresholdPct: Int = 60,

    @Column(name = "max_active_free_entries_per_player", nullable = false)
    val maxActiveFreeEntriesPerPlayer: Int = 3,

    @Column(name = "cross_sell_compliance_mode", nullable = false)
    val crossSellComplianceMode: String = "PERMISSIVE", // UK_STRICT | PERMISSIVE | DISABLED

    @Column(name = "player_feed_enabled", nullable = false)
    val playerFeedEnabled: Boolean = false,

    @Column(name = "player_feed_hmac_secret")
    val playerFeedHmacSecret: String? = null,

    @Column(name = "player_feed_last_sync_at")
    var playerFeedLastSyncAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
