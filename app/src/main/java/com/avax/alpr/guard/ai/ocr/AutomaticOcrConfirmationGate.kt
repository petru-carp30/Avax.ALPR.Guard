package com.avax.alpr.guard.ai.ocr

internal class AutomaticOcrConfirmationGate(
    private val requiredMatches: Int = 2,
    private val maxCandidates: Int = 3,
    private val collectionWindowMs: Long = 6_000L,
    private val noPlateResetMs: Long = 1_500L,
    private val clockMs: () -> Long
) {

    sealed interface Outcome {
        data class Collecting(val candidates: List<String>) : Outcome
        data class Confirmed(val normalizedPlate: String, val candidates: List<String>) : Outcome
        data class Uncertain(val candidates: List<String>, val reason: Reason) : Outcome
    }

    enum class Reason {
        NoAgreement,
        WindowExpired
    }

    private val candidates = mutableListOf<String>()
    private var collectionStartedAtMs: Long? = null
    private var noPlateSinceMs: Long? = null

    @Synchronized
    fun submit(normalizedPlate: String): Outcome {
        require(normalizedPlate.isNotBlank())

        val nowMs = clockMs()
        noPlateSinceMs = null

        val startedAtMs = collectionStartedAtMs

        if (startedAtMs != null && nowMs - startedAtMs > collectionWindowMs) {
            val expiredCandidates = candidates.toList()

            resetCollection()

            collectionStartedAtMs = nowMs
            candidates += normalizedPlate

            return Outcome.Uncertain(
                candidates = expiredCandidates,
                reason = Reason.WindowExpired
            )
        }

        if (collectionStartedAtMs == null) {
            collectionStartedAtMs = nowMs
        }

        candidates += normalizedPlate

        val snapshot = candidates.toList()

        val confirmedPlate = snapshot
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value >= requiredMatches }
            ?.key

        if (confirmedPlate != null) {
            resetCollection()

            return Outcome.Confirmed(
                normalizedPlate = confirmedPlate,
                candidates = snapshot
            )
        }

        if (snapshot.size >= maxCandidates) {
            resetCollection()

            return Outcome.Uncertain(
                candidates = snapshot,
                reason = Reason.NoAgreement
            )
        }

        return Outcome.Collecting(snapshot)
    }

    @Synchronized
    fun onDetectorResult(hasPlateDetection: Boolean) {
        if (hasPlateDetection) {
            noPlateSinceMs = null
            return
        }

        val nowMs = clockMs()
        val noPlateSince = noPlateSinceMs

        if (noPlateSince == null) {
            noPlateSinceMs = nowMs
            return
        }

        if (nowMs - noPlateSince >= noPlateResetMs) {
            resetCollection()
        }
    }

    @Synchronized
    fun reset() {
        resetCollection()
        noPlateSinceMs = null
    }

    private fun resetCollection() {
        candidates.clear()
        collectionStartedAtMs = null
    }
}