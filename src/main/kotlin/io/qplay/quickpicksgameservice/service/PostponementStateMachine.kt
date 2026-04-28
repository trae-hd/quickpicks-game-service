package io.qplay.quickpicksgameservice.service

enum class PostponementBehaviour {
    SETTLE_ON_11,
    SETTLE_ON_10,
    VOID_ROUND
}

data class PostponementResult(
    val behaviour: PostponementBehaviour,
    val jackpotThreshold: Int,
    val runnerUpThreshold: Int,
    val consolationThreshold: Int
)

object PostponementStateMachine {
    fun evaluate(postponedCount: Int): PostponementResult {
        return when (postponedCount) {
            0 -> PostponementResult(PostponementBehaviour.SETTLE_ON_11, 12, 11, 10) // Should not happen in this logic if called correctly, but 0 is standard 12
            1 -> PostponementResult(PostponementBehaviour.SETTLE_ON_11, 11, 10, 9)
            2 -> PostponementResult(PostponementBehaviour.SETTLE_ON_10, 10, 9, 8)
            else -> PostponementResult(PostponementBehaviour.VOID_ROUND, 0, 0, 0)
        }
    }
}
