package io.qplay.quickpicksgameservice.ratelimit

import io.qplay.quickpicksgameservice.security.PlayerJwtClaims
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.Instant

@Aspect
@Component
class RateLimitAspect(
    private val redisTemplate: StringRedisTemplate
) {

    /**
     * Returns Any? because AOP joinpoints wrap arbitrary controller methods
     * with heterogeneous return types. The concrete return type is preserved
     * at runtime — this is a limitation of Spring AOP's ProceedingJoinPoint
     * contract, not a type safety violation.
     */
    @Around("@annotation(io.qplay.quickpicksgameservice.ratelimit.RateLimited)")
    fun enforceRateLimit(joinPoint: ProceedingJoinPoint): Any? {
        // Redis is required for rate limiting on write endpoints.
        // Unlike trending (fire-and-forget) and targeting (Postgres fallback),
        // these are safety mechanisms protecting real money. If Redis is unavailable,
        // we fail the request rather than risk unlimited entries.
        val auth = SecurityContextHolder.getContext().authentication ?: return joinPoint.proceed()
        val claims = auth.principal as? PlayerJwtClaims
            ?: return joinPoint.proceed() // Only rate limit players

        val playerId = claims.playerId
        val tenantId = claims.tenantId
        val redisKey = "ratelimit:$tenantId:$playerId"
        
        val now = Instant.now().toEpochMilli()
        val windowStart = now - 60000 // 1 minute window

        // Redis sliding window using Sorted Set
        val count = redisTemplate.execute { connection ->
            val keyBytes = redisKey.toByteArray()
            connection.zSetCommands().zRemRangeByScore(keyBytes, 0.0, windowStart.toDouble())
            connection.zSetCommands().zAdd(keyBytes, now.toDouble(), now.toString().toByteArray())
            connection.expire(keyBytes, 65)
            connection.zSetCommands().zCard(keyBytes)
        }

        if (count != null && count > 10) {
            return ResponseEntity.status(429).body(mapOf("error" to "Too Many Requests - Rate limit exceeded"))
        }

        return joinPoint.proceed()
    }
}
