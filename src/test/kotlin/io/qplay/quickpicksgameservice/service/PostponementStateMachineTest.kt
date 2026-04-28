package io.qplay.quickpicksgameservice.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostponementStateMachineTest {

    @Test
    fun `1 postponed match should settle on 11 with adjusted thresholds`() {
        val result = PostponementStateMachine.evaluate(1)
        assertEquals(PostponementBehaviour.SETTLE_ON_11, result.behaviour)
        assertEquals(11, result.jackpotThreshold)
        assertEquals(10, result.runnerUpThreshold)
        assertEquals(9, result.consolationThreshold)
    }

    @Test
    fun `2 postponed matches should settle on 10 with adjusted thresholds`() {
        val result = PostponementStateMachine.evaluate(2)
        assertEquals(PostponementBehaviour.SETTLE_ON_10, result.behaviour)
        assertEquals(10, result.jackpotThreshold)
        assertEquals(9, result.runnerUpThreshold)
        assertEquals(8, result.consolationThreshold)
    }

    @Test
    fun `3 postponed matches should void the round`() {
        val result = PostponementStateMachine.evaluate(3)
        assertEquals(PostponementBehaviour.VOID_ROUND, result.behaviour)
        assertEquals(0, result.jackpotThreshold)
    }

    @Test
    fun `more than 3 postponed matches should void the round`() {
        listOf(4, 5, 10, 12).forEach { count ->
            val result = PostponementStateMachine.evaluate(count)
            assertEquals(PostponementBehaviour.VOID_ROUND, result.behaviour,
                "Expected VOID_ROUND for $count postponed matches")
        }
    }

    @Test
    fun `0 postponed should return standard 12-match thresholds`() {
        val result = PostponementStateMachine.evaluate(0)
        assertEquals(12, result.jackpotThreshold)
        assertEquals(11, result.runnerUpThreshold)
        assertEquals(10, result.consolationThreshold)
    }
}