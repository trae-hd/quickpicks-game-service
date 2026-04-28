package io.qplay.quickpicksgameservice.domain.round

enum class Outcome {
    HOME,
    AWAY,
    DRAW
}

enum class RoundStatus {
    OPEN,
    LOCKED,
    SETTLED,
    CANCELLED,
    VOIDED,
    REQUIRES_REVIEW
}

enum class PrizeTier {
    JACKPOT_12_12,      // 12 correct out of 12
    RUNNER_UP_11_12,    // 11 correct out of 12
    CONSOLATION_10_12,  // 10 correct out of 12
}
