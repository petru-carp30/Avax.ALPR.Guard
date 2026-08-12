package com.avax.alpr.guard.domain

object PlateNormalizer {

    fun normalize(plate: String?): String {
        if (plate.isNullOrBlank()) return ""

        val result = StringBuilder(plate.length)

        for (character in plate) {
            if (Character.isLetterOrDigit(character)) result.append(Character.toUpperCase(character))
        }

        return result.toString()
    }
}