package io.qplay.quickpicksgameservice.infra.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RenewableRedisLock(
    private val redisTemplate: StringRedisTemplate,
    private val lockKey: String,
    private val lockValue: String = UUID.randomUUID().toString(),
    private val ttlSeconds: Long = 30,
    private val renewalIntervalSeconds: Long = 10,
) {
    private val logger = LoggerFactory.getLogger(RenewableRedisLock::class.java)
    private val renewalExecutor = Executors.newSingleThreadScheduledExecutor()
    private var renewalFuture: ScheduledFuture<*>? = null
    private val lockLost = AtomicBoolean(false)

    /** Check this between each wallet credit call. If true, abort settlement immediately. */
    fun isLockLost(): Boolean = lockLost.get()

    fun tryAcquire(): Boolean {
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds))
            ?: false
        if (acquired) startRenewal()
        return acquired
    }

    private fun startRenewal() {
        val renewScript = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('expire', KEYS[1], ARGV[2])
            else
                return 0
            end
        """.trimIndent()
        renewalFuture = renewalExecutor.scheduleAtFixedRate({
            try {
                val renewed = redisTemplate.execute(
                    DefaultRedisScript<Long>(renewScript, Long::class.java),
                    listOf(lockKey), lockValue, ttlSeconds.toString()
                )
                if (renewed == 0L) {
                    renewalFuture?.cancel(false)
                    logger.error("Settlement lock lost for $lockKey — aborting renewal")
                    lockLost.set(true)
                }
            } catch (e: Exception) {
                logger.error("Error during lock renewal for $lockKey", e)
                // We don't set lockLost here yet, but the process might fail on next renewal
            }
        }, renewalIntervalSeconds, renewalIntervalSeconds, TimeUnit.SECONDS)
    }

    fun release() {
        renewalFuture?.cancel(false)
        renewalExecutor.shutdown()
        val script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"
        redisTemplate.execute(DefaultRedisScript<Long>(script, Long::class.java), listOf(lockKey), lockValue)
    }
}
