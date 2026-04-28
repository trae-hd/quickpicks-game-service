package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.domain.entry.EntryRepository
import io.qplay.quickpicksgameservice.domain.player.PlayerExclusionEvent
import io.qplay.quickpicksgameservice.domain.player.PlayerExclusionEventRepository
import io.qplay.quickpicksgameservice.domain.round.RoundStatus
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import io.qplay.quickpicksgameservice.tenant.persistence.TenantPlayerAccess
import io.qplay.quickpicksgameservice.tenant.persistence.TenantPlayerAccessRepository
import io.qplay.quickpicksgameservice.tenant.persistence.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

enum class SyncMode { FULL_REPLACE, DELTA }

data class PlayerSyncEntry(val hostPlayerId: String, val action: String)

data class SyncStats(
    val processed: Int,
    val added: Int,
    val removed: Int,
    val skipped: Int,
    val syncMode: String,
    val syncedAt: OffsetDateTime
)

data class SingleUpdateResult(
    val hostPlayerId: String,
    val action: String,
    val appliedAt: OffsetDateTime
)

data class ExclusionNotifyResult(
    val hostPlayerId: String,
    val unsettledEntriesReviewed: Int,
    val targetingCacheInvalidated: Boolean,
    val auditEventId: String
)

data class PlayerAccessStatus(
    val hostPlayerId: String,
    val accessLevel: String?,
    val source: String?,
    val isExplicitlyExcluded: Boolean
)

@Service
class HostPlayerFeedService(
    private val playerAccessRepository: TenantPlayerAccessRepository,
    private val playerExclusionEventRepository: PlayerExclusionEventRepository,
    private val entryRepository: EntryRepository,
    private val tenantRepository: TenantRepository,
    private val targetingService: TargetingService,
    private val auditService: AuditService
) {
    @Transactional
    fun syncPlayers(
        tenant: Tenant,
        syncMode: SyncMode,
        accessMode: String,
        players: List<PlayerSyncEntry>
    ): SyncStats {
        val accessLevel = if (accessMode == "ALLOWLIST") "ALLOW" else "BLOCK"
        var added = 0
        var removed = 0
        var skipped = 0
        val now = OffsetDateTime.now()

        if (syncMode == SyncMode.FULL_REPLACE) {
            val existingPlayerIds = playerAccessRepository
                .findAllByTenantIdAndSource(tenant.id, "HOST_FEED")
                .map { it.playerId }.toSet()

            playerAccessRepository.deleteAllByTenantIdAndSource(tenant.id, "HOST_FEED")

            val toAdd = players.filter { it.action == "ADD" }
            skipped = players.size - toAdd.size

            toAdd.forEach { p ->
                playerAccessRepository.save(
                    TenantPlayerAccess(
                        tenantId = tenant.id,
                        playerId = p.hostPlayerId,
                        accessLevel = accessLevel,
                        source = "HOST_FEED",
                        updatedBy = "host-system",
                        syncedAt = now
                    )
                )
                added++
            }

            (existingPlayerIds + toAdd.map { it.hostPlayerId }).forEach { pid ->
                targetingService.invalidatePlayerCache(tenant.id, pid)
            }
        } else {
            players.forEach { p ->
                val existing = playerAccessRepository.findByTenantIdAndPlayerId(tenant.id, p.hostPlayerId)
                when (p.action) {
                    "ADD" -> when {
                        existing == null -> {
                            playerAccessRepository.save(
                                TenantPlayerAccess(
                                    tenantId = tenant.id,
                                    playerId = p.hostPlayerId,
                                    accessLevel = accessLevel,
                                    source = "HOST_FEED",
                                    updatedBy = "host-system",
                                    syncedAt = now
                                )
                            )
                            targetingService.invalidatePlayerCache(tenant.id, p.hostPlayerId)
                            added++
                        }
                        existing.source == "HOST_FEED" -> {
                            playerAccessRepository.save(
                                TenantPlayerAccess(
                                    id = existing.id,
                                    tenantId = tenant.id,
                                    playerId = p.hostPlayerId,
                                    accessLevel = accessLevel,
                                    source = "HOST_FEED",
                                    updatedBy = "host-system",
                                    syncedAt = now,
                                    createdAt = existing.createdAt
                                )
                            )
                            targetingService.invalidatePlayerCache(tenant.id, p.hostPlayerId)
                            added++
                        }
                        else -> skipped++ // MANUAL entry exists — don't overwrite
                    }
                    "REMOVE" -> if (existing != null && existing.source == "HOST_FEED") {
                        playerAccessRepository.delete(existing)
                        targetingService.invalidatePlayerCache(tenant.id, p.hostPlayerId)
                        removed++
                    } else {
                        skipped++
                    }
                    else -> skipped++
                }
            }
        }

        tenant.playerFeedLastSyncAt = now
        tenant.updatedAt = now
        tenantRepository.save(tenant)

        return SyncStats(
            processed = players.size,
            added = added,
            removed = removed,
            skipped = skipped,
            syncMode = syncMode.name,
            syncedAt = now
        )
    }

    @Transactional
    fun updateSinglePlayer(
        tenant: Tenant,
        hostPlayerId: String,
        action: String,
        accessMode: String,
        reason: String?
    ): SingleUpdateResult {
        val accessLevel = if (accessMode == "ALLOWLIST") "ALLOW" else "BLOCK"
        val now = OffsetDateTime.now()
        val existing = playerAccessRepository.findByTenantIdAndPlayerId(tenant.id, hostPlayerId)

        when (action) {
            "ADD" -> {
                val entry = when {
                    existing == null -> TenantPlayerAccess(
                        tenantId = tenant.id,
                        playerId = hostPlayerId,
                        accessLevel = accessLevel,
                        source = "HOST_FEED",
                        updatedBy = "host-system",
                        reason = reason,
                        syncedAt = now
                    )
                    existing.source == "HOST_FEED" -> TenantPlayerAccess(
                        id = existing.id,
                        tenantId = tenant.id,
                        playerId = hostPlayerId,
                        accessLevel = accessLevel,
                        source = "HOST_FEED",
                        updatedBy = "host-system",
                        reason = reason,
                        syncedAt = now,
                        createdAt = existing.createdAt
                    )
                    else -> null // MANUAL entry — leave it alone
                }
                if (entry != null) {
                    playerAccessRepository.save(entry)
                    targetingService.invalidatePlayerCache(tenant.id, hostPlayerId)
                }
            }
            "REMOVE" -> if (existing != null && existing.source == "HOST_FEED") {
                playerAccessRepository.delete(existing)
                targetingService.invalidatePlayerCache(tenant.id, hostPlayerId)
            }
        }

        return SingleUpdateResult(hostPlayerId, action, now)
    }

    @Transactional
    fun notifyExclusion(
        tenant: Tenant,
        hostPlayerId: String,
        exclusionType: String,
        effectiveAt: OffsetDateTime,
        reason: String?
    ): ExclusionNotifyResult {
        val unsettledEntries = entryRepository.findUnsettledByTenantIdAndPlayerId(
            tenant.id,
            hostPlayerId,
            listOf(RoundStatus.OPEN, RoundStatus.LOCKED)
        )

        val event = playerExclusionEventRepository.save(
            PlayerExclusionEvent(
                tenantId = tenant.id,
                hostPlayerId = hostPlayerId,
                exclusionType = exclusionType,
                effectiveAt = effectiveAt,
                reason = reason,
                entriesFlagged = unsettledEntries.size
            )
        )

        targetingService.invalidatePlayerCache(tenant.id, hostPlayerId)

        auditService.log(
            tenantId = tenant.id,
            actorId = "host-system",
            action = "exclusion_notified",
            targetId = hostPlayerId,
            payload = mapOf(
                "exclusionType" to exclusionType,
                "effectiveAt" to effectiveAt.toString(),
                "entriesFlagged" to unsettledEntries.size,
                "eventId" to event.id.toString()
            )
        )

        return ExclusionNotifyResult(
            hostPlayerId = hostPlayerId,
            unsettledEntriesReviewed = unsettledEntries.size,
            targetingCacheInvalidated = true,
            auditEventId = "evt_${event.id}"
        )
    }

    fun getPlayerStatus(tenant: Tenant, hostPlayerId: String): PlayerAccessStatus {
        val access = playerAccessRepository.findByTenantIdAndPlayerId(tenant.id, hostPlayerId)
        val isExplicitlyExcluded = playerExclusionEventRepository
            .findByTenantIdAndHostPlayerId(tenant.id, hostPlayerId)
            .isNotEmpty()
        return PlayerAccessStatus(
            hostPlayerId = hostPlayerId,
            accessLevel = access?.accessLevel,
            source = access?.source,
            isExplicitlyExcluded = isExplicitlyExcluded
        )
    }
}