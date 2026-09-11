package com.avax.alpr.guard.ui.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticVerificationCooldownTest {

    @Test
    fun firstPlateIsNotSuppressed() {
        val cooldown = AutomaticVerificationCooldown(10_000L)

        assertFalse(cooldown.shouldSuppress("CJ12ABC", 1_000L))
    }

    @Test
    fun samePlateWithinTenSecondsIsSuppressed() {
        val cooldown = AutomaticVerificationCooldown(10_000L)
        cooldown.mark("CJ12ABC", 1_000L)

        assertTrue(cooldown.shouldSuppress("CJ12ABC", 10_999L))
    }

    @Test
    fun samePlateAtTenSecondsIsAllowed() {
        val cooldown = AutomaticVerificationCooldown(10_000L)
        cooldown.mark("CJ12ABC", 1_000L)

        assertFalse(cooldown.shouldSuppress("CJ12ABC", 11_000L))
    }

    @Test
    fun differentPlateIsNotSuppressed() {
        val cooldown = AutomaticVerificationCooldown(10_000L)
        cooldown.mark("CJ12ABC", 1_000L)

        assertFalse(cooldown.shouldSuppress("B123ABC", 2_000L))
    }
}