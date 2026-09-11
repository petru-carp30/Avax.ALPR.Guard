package com.avax.alpr.guard.ai.ocr

internal enum class PlateCropQuality {
    Acceptable,
    LowQuality
}

internal object PlateCropQualityGate {

    const val MINIMUM_NATIVE_HEIGHT_PX = 32

    fun evaluate(nativeHeightPx: Int): PlateCropQuality {
        return if (nativeHeightPx < MINIMUM_NATIVE_HEIGHT_PX) {
            PlateCropQuality.LowQuality
        } else {
            PlateCropQuality.Acceptable
        }
    }
}