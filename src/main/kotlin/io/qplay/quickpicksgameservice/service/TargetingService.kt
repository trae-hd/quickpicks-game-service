package io.qplay.quickpicksgameservice.service

import io.qplay.quickpicksgameservice.tenant.persistence.PlayerExclusionRepository
import io.qplay.quickpicksgameservice.tenant.persistence.TenantPlayerAccessRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

enum class AccessResult { ALLOWED, BLOCKED, COMING_SOON }

@Service
class TargetingService(
    private val playerExclusionRepository: PlayerExclusionRepository,
    private val tenantPlayerAccessRepository: TenantPlayerAccessRepository,
    private val redisTemplate: StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(TargetingService::class.java)

    fun checkAccess(tenantId: String, playerId: String, targetingMode: String = "OPEN"): AccessResult {
        if (checkExplicitBlock(tenantId, playerId)) return AccessResult.BLOCKED
        if (targetingMode == "ALLOWLIST" && !checkAllowlist(tenantId, playerId)) return AccessResult.COMING_SOON
        return AccessResult.ALLOWED
    }

    private fun checkExplicitBlock(tenantId: String, playerId: String): Boolean {
        val cacheKey = "exclusion:$tenantId:$playerId"
        try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached != null) return cached.toBoolean()
        } catch (e: Exception) {
            logger.warn("Redis failure in TargetingService (block), falling back to DB: ${e.message}")
        }

        val excluded = playerExclusionRepository.findByTenantIdAndPlayerId(tenantId, playerId) != null
        
        try {
            redisTemplate.opsForValue().set(cacheKey, excluded.toString(), Duration.ofHours(1))
        } catch (e: Exception) { /* ignore */ }
        
        return excluded
    }

    fun invalidatePlayerCache(tenantId: String, playerId: String) {
        try {
            redisTemplate.delete("exclusion:$tenantId:$playerId")
            redisTemplate.delete("allowlist:$tenantId:$playerId")
        } catch (e: Exception) {
            logger.warn("Redis failure invalidating player cache for $tenantId/$playerId: ${e.message}")
        }
    }

    private fun checkAllowlist(tenantId: String, playerId: String): Boolean {
        val cacheKey = "allowlist:$tenantId:$playerId"
        try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached != null) return cached.toBoolean()
        } catch (e: Exception) {
            logger.warn("Redis failure in TargetingService (allowlist), falling back to DB: ${e.message}")
        }

        val allowed = tenantPlayerAccessRepository.findByTenantIdAndPlayerId(tenantId, playerId)?.accessLevel == "ALLOW"
        
        try {
            redisTemplate.opsForValue().set(cacheKey, allowed.toString(), Duration.ofHours(1))
        } catch (e: Exception) { /* ignore */ }
        
        return allowed
    }
}
