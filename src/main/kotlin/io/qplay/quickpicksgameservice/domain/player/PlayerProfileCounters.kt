package io.qplay.quickpicksgameservice.domain.player

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.io.Serializable
import java.time.OffsetDateTime

@Embeddable
data class PlayerProfileId(
    @Column(name = "tenant_id")
    val tenantId: String,
    @Column(name = "player_id")
    val playerId: String
) : Serializable

@Entity
@Table(name = "player_profile_counters")
class PlayerProfileCounters(
    @EmbeddedId
    val id: PlayerProfileId,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counters_json", nullable = false)
    var counters: Map<String, Any> = mutableMapOf(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

@Repository
interface PlayerProfileCountersRepository : JpaRepository<PlayerProfileCounters, PlayerProfileId>
