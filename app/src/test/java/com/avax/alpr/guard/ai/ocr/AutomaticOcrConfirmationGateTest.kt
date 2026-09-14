package com.avax.alpr.guard.ai.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticOcrConfirmationGateTest {

    @Test
    fun twoMatchingCandidatesConfirmImmediately() {
        var now = 0L
        val gate = AutomaticOcrConfirmationGate(clockMs = { now })

        val first = gate.submit("SV56GOC")

        now = 500L

        val second = gate.submit("SV56GOC")

        assertTrue(first is AutomaticOcrConfirmationGate.Outcome.Collecting)
        assertTrue(second is AutomaticOcrConfirmationGate.Outcome.Confirmed)
        assertEquals(
            "SV56GOC",
            (second as AutomaticOcrConfirmationGate.Outcome.Confirmed).normalizedPlate
        )
    }

    @Test
    fun firstAndThirdMatchingCandidatesConfirm() {
        var now = 0L
        val gate = AutomaticOcrConfirmationGate(clockMs = { now })

        gate.submit("SV56GOC")

        now = 400L
        gate.submit("SV56600")

        now = 800L
        val result = gate.submit("SV56GOC")

        assertTrue(result is AutomaticOcrConfirmationGate.Outcome.Confirmed)
        assertEquals(
            "SV56GOC",
            (result as AutomaticOcrConfirmationGate.Outcome.Confirmed).normalizedPlate
        )
    }

    @Test
    fun threeDifferentCandidatesAreUncertain() {
        var now = 0L
        val gate = AutomaticOcrConfirmationGate(clockMs = { now })

        gate.submit("SV56GOC")

        now = 400L
        gate.submit("SV56600")

        now = 800L
        val result = gate.submit("SV5860C")

        assertTrue(result is AutomaticOcrConfirmationGate.Outcome.Uncertain)
        assertEquals(
            AutomaticOcrConfirmationGate.Reason.NoAgreement,
            (result as AutomaticOcrConfirmationGate.Outcome.Uncertain).reason
        )
    }

    @Test
    fun matchingCandidateAfterSlowOcrStillConfirmsImmediately() {
        var now = 0L
        val gate = AutomaticOcrConfirmationGate(clockMs = { now })

        gate.submit("B173AVX")

        now = 4_500L
        val result = gate.submit("B173AVX")

        assertTrue(result is AutomaticOcrConfirmationGate.Outcome.Confirmed)
        assertEquals(
            "B173AVX",
            (result as AutomaticOcrConfirmationGate.Outcome.Confirmed).normalizedPlate
        )
    }

    @Test
    fun confirmationWindowStillExpiresAfterMaximumTimeout() {
        var now = 0L
        val gate = AutomaticOcrConfirmationGate(clockMs = { now })

        gate.submit("B173AVX")

        now = 6_001L
        val result = gate.submit("B173AVX")

        assertTrue(result is AutomaticOcrConfirmationGate.Outcome.Uncertain)
        assertEquals(
            AutomaticOcrConfirmationGate.Reason.WindowExpired,
            (result as AutomaticOcrConfirmationGate.Outcome.Uncertain).reason
        )
    }

    @Test
    fun noPlateFor1500MsResetsPendingConfirmation() {
        var now = 0L
        val gate = AutomaticOcrConfirmationGate(clockMs = { now })

        gate.submit("SV56GOC")

        now = 100L
        gate.onDetectorResult(hasPlateDetection = false)

        now = 1_600L
        gate.onDetectorResult(hasPlateDetection = false)

        now = 1_700L
        val result = gate.submit("SV56GOC")

        assertTrue(result is AutomaticOcrConfirmationGate.Outcome.Collecting)
    }

    @Test
    fun shortDetectorGapDoesNotResetPendingConfirmation() {
        var now = 0L
        val gate = AutomaticOcrConfirmationGate(clockMs = { now })

        gate.submit("SV56GOC")

        now = 100L
        gate.onDetectorResult(hasPlateDetection = false)

        now = 800L
        gate.onDetectorResult(hasPlateDetection = true)

        now = 900L
        val result = gate.submit("SV56GOC")

        assertTrue(result is AutomaticOcrConfirmationGate.Outcome.Confirmed)
    }
}