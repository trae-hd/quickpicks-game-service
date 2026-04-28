package io.qplay.quickpicksgameservice.domain.outbox

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "event_outbox")
class OutboxEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "player_id")
    val playerId: String? = null,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Column(name = "event_version", nullable = false)
    val eventVersion: Int = 1,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    val payload: Map<String, Any?>,

    @Column(name = "batch_eligible", nullable = false)
    val batchEligible: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "sent_at")
    var sentAt: OffsetDateTime? = null,

    @Column(name = "error_log")
    var errorLog: String? = null
)

@Repository
interface OutboxRepository : JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
        SELECT * FROM event_outbox 
        WHERE sent_at IS NULL 
        ORDER BY created_at ASC 
        LIMIT :limit 
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    fun findUnsent(limit: Int): List<OutboxEvent>
}
