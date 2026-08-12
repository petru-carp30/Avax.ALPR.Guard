package com.avax.alpr.guard.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlateNormalizerTest {

    @Test
    fun normalizesSpaces() {
        assertEquals("CJ12ABC", PlateNormalizer.normalize("CJ 12 ABC"))
    }

    @Test
    fun normalizesHyphens() {
        assertEquals("CJ12ABC", PlateNormalizer.normalize("CJ-12-ABC"))
    }

    @Test
    fun normalizesLowercase() {
        assertEquals("CJ12ABC", PlateNormalizer.normalize("cj12abc"))
    }

    @Test
    fun removesNonAlphanumericCharacters() {
        assertEquals("CJ12ABC", PlateNormalizer.normalize("CJ.12/ABC"))
    }

    @Test
    fun blankInputReturnsEmptyValue() {
        assertEquals("", PlateNormalizer.normalize("   "))
    }

    @Test
    fun nullInputReturnsEmptyValue() {
        assertEquals("", PlateNormalizer.normalize(null))
    }

    @Test
    fun doesNotConvertSimilarCharacters() {
        assertEquals("CJ1O0ABC", PlateNormalizer.normalize("CJ1O0ABC"))
    }
}