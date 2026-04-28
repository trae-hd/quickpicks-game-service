package io.qplay.quickpicksgameservice.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration

@Aspect
@Component
class IdempotencyAspect(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    /**
     * Returns Any? because AOP joinpoints wrap arbitrary controller methods
     * with heterogeneous return types. The concrete return type is preserved
     * at runtime — this is a limitation of Spring AOP's ProceedingJoinPoint
     * contract, not a type safety violation.
     */
    @Around("@annotation(io.qplay.quickpicksgameservice.idempotency.Idempotent)")
    fun handleIdempotency(joinPoint: ProceedingJoinPoint): Any? {
        // Redis is required for idempotency on write endpoints.
        // Unlike trending (fire-and-forget) and targeting (Postgres fallback),
        // these are safety mechanisms protecting real money. If Redis is unavailable,
        // we fail the request rather than risk duplicate debits.
        val request = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
        val key = request.getHeader("Idempotency-Key")
            ?: return joinPoint.proceed() // Skip if no key provided

        val redisKey = "idempotency:$key"

        // Atomic reservation using SETNX — 24h covers retries across server restarts
        val isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PENDING", Duration.ofHours(24))

        if (isNew == false) {
            val cachedValue = redisTemplate.opsForValue().get(redisKey)
            if (cachedValue == "PENDING") {
                return ResponseEntity.status(409).body(mapOf("error" to "Processing in progress"))
            }
            
            // Attempt to deserialize cached response
            return try {
                val cachedResponse = objectMapper.readValue(cachedValue, CachedResponse::class.java)
                ResponseEntity.status(cachedResponse.status).body(cachedResponse.body)
            } catch (e: Exception) {
                // If corrupted, re-proceed (fallback)
                joinPoint.proceed()
            }
        }

        return try {
            val result = joinPoint.proceed()
            
            if (result is ResponseEntity<*>) {
                val cachedResponse = CachedResponse(
                    status = result.statusCode.value(),
                    body = result.body
                )
                val jsonResponse = objectMapper.writeValueAsString(cachedResponse)
                redisTemplate.opsForValue().set(redisKey, jsonResponse, Duration.ofHours(24))
            }
            
            result
        } catch (e: Exception) {
            redisTemplate.delete(redisKey) // Remove reservation on failure so client can retry
            throw e
        }
    }

    data class CachedResponse(
        val status: Int,
        val body: Any?
    )
}
