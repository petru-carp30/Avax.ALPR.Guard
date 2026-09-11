package com.avax.alpr.guard.ai.ocr

internal data class OcrLineCandidate(
    val text: String,
    val confidence: Float?
)

internal object OcrCandidateSelector {

    fun select(candidates: List<OcrLineCandidate>): OcrLineCandidate? {
        return candidates
            .mapNotNull { candidate ->
                candidate.score()?.let { score -> candidate to score }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun OcrLineCandidate.score(): Float? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val visibleCharacterCount = trimmed.count { !it.isWhitespace() }
        if (visibleCharacterCount == 0) return null

        val alphanumeric = trimmed.filter { it.isLetterOrDigit() }

        if (alphanumeric.length < 4 || alphanumeric.length > 16) return null

        val alphanumericRatio = alphanumeric.length.toFloat() / visibleCharacterCount.toFloat()
        if (alphanumericRatio < 0.60f) return null

        val hasLetter = alphanumeric.any { it.isLetter() }
        val hasDigit = alphanumeric.any { it.isDigit() }

        var score = alphanumericRatio * 3f

        score += when (alphanumeric.length) {
            in 5..9 -> 4f
            in 4..12 -> 2f
            else -> -2f - (alphanumeric.length - 12) * 0.5f
        }

        if (hasLetter) score += 1f
        if (hasDigit) score += 1f
        if (hasLetter && hasDigit) score += 2f

        confidence
            ?.takeIf { it > 0f && it <= 1f }
            ?.let { score += it * 2f }

        return score
    }
}