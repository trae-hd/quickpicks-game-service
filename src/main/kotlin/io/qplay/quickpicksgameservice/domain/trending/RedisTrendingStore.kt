package io.qplay.quickpicksgameservice.domain.trending

import org.springframework.data.redis.connection.StringRedisConnection
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisTrendingStore(
    private val redisTemplate: StringRedisTemplate
) {
    private fun key(roundId: String) = "trending:round:$roundId"

    fun increment(roundId: String, matchId: String, outcome: String) {
        try {
            redisTemplate.executePipelined { conn ->
                val c = conn as StringRedisConnection
                c.hIncrBy(key(roundId), "$matchId:$outcome", 1)
                c.expire(key(roundId), 30L * 24 * 3600)
                null
            }
        } catch (e: Exception) {
            // Fire-and-forget: Redis failure must NOT block entry creation
        }
    }

    fun getTrending(roundId: String): Map<String, Map<String, Int>> {
        val hash = redisTemplate.opsForHash<String, String>().entries(key(roundId))
        val result = mutableMapOf<String, MutableMap<String, Int>>()
        
        hash.forEach { (field, value) ->
            val parts = field.split(":")
            if (parts.size == 2) {
                val matchId = parts[0]
                val outcome = parts[1]
                val count = value.toIntOrNull() ?: 0
                result.getOrPut(matchId) { mutableMapOf() }[outcome] = count
            }
        }
        return result
    }
}
