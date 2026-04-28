package io.qplay.quickpicksgameservice.domain.share

import io.qplay.quickpicksgameservice.domain.entry.PicksPayload
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "share_links")
class ShareLink(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "player_id", nullable = false)
    val playerId: String,

    @Column(name = "entry_id", nullable = false)
    val entryId: UUID,

    @Column(name = "token", nullable = false, unique = true)
    val token: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "picks_json", nullable = false)
    val picksJson: PicksPayload,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime
)

interface ShareLinkRepository : org.springframework.data.jpa.repository.JpaRepository<ShareLink, UUID> {
    fun findByToken(token: String): ShareLink?
}
