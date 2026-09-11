package com.avax.alpr.guard.domain

internal class AutomaticScanRearmGate(
    private val rearmDelayMs: Long = 1_500L,
    private val clockMs: () -> Long
) {
    private var locked = false
    private var noPlateSinceMs: Long? = null

    fun tryAcquireAutomaticVerification(): Boolean {
        if (locked) return false

        locked = true
        noPlateSinceMs = null
        return true
    }

    fun onDetectorResult(hasPlateDetection: Boolean) {
        if (!locked) return

        if (hasPlateDetection) {
            noPlateSinceMs = null
            return
        }

        val now = clockMs()
        val noPlateSince = noPlateSinceMs

        if (noPlateSince == null) {
            noPlateSinceMs = now
            return
        }

        if (now - noPlateSince >= rearmDelayMs) {
            locked = false
            noPlateSinceMs = null
        }
    }

    fun isLocked(): Boolean = locked

    fun reset() {
        locked = false
        noPlateSinceMs = null
    }
}