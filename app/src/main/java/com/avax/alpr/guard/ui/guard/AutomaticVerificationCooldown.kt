package com.avax.alpr.guard.ui.guard

internal class AutomaticVerificationCooldown(
    private val cooldownMs: Long = 10_000L
) {
    private var lastPlate: String? = null
    private var lastEventAtMs: Long? = null

    init {
        require(cooldownMs > 0L)
    }

    fun shouldSuppress(normalizedPlate: String, nowMs: Long): Boolean {
        val previousEventAtMs = lastEventAtMs ?: return false
        if (normalizedPlate != lastPlate) return false

        val elapsedMs = nowMs - previousEventAtMs
        return elapsedMs >= 0L && elapsedMs < cooldownMs
    }

    fun mark(normalizedPlate: String, nowMs: Long) {
        lastPlate = normalizedPlate
        lastEventAtMs = nowMs
    }
}