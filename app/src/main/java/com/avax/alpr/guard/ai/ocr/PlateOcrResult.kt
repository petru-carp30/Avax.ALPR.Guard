package com.avax.alpr.guard.ai.ocr

data class PlateOcrResult(
    val text: String,
    val confidence: Float?,
    val rawText: String,
    val latencyMs: Double,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    val inputWidth: Int? = null,
    val inputHeight: Int? = null,
    val wasUpscaled: Boolean? = null
)