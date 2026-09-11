package com.avax.alpr.guard.ai.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrCandidateSelectorTest {

    @Test
    fun selectsPlateLikeLine() {
        val result = OcrCandidateSelector.select(
            listOf(
                OcrLineCandidate("ROMANIA", 0.96f),
                OcrLineCandidate("CJ 12 ABC", 0.88f),
                OcrLineCandidate("EU", 0.99f)
            )
        )

        assertEquals("CJ 12 ABC", result?.text)
    }

    @Test
    fun preservesRecognizedCharacters() {
        val result = OcrCandidateSelector.select(
            listOf(
                OcrLineCandidate("B8O1ABC", 0.91f)
            )
        )

        assertEquals("B8O1ABC", result?.text)
    }

    @Test
    fun rejectsEmptyOrUnusableCandidates() {
        val result = OcrCandidateSelector.select(
            listOf(
                OcrLineCandidate("", null),
                OcrLineCandidate("AB", 0.99f),
                OcrLineCandidate("---", 0.95f)
            )
        )

        assertNull(result)
    }

    @Test
    fun prefersReasonableLengthOverExcessivelyLongText() {
        val result = OcrCandidateSelector.select(
            listOf(
                OcrLineCandidate("THISISAVERYLONGTEXT123", 0.99f),
                OcrLineCandidate("B123ABC", 0.80f)
            )
        )

        assertEquals("B123ABC", result?.text)
    }
}