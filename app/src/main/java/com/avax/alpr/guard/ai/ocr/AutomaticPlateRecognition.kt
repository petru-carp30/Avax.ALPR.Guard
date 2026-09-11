package com.avax.alpr.guard.ai.ocr

data class AutomaticPlateRecognition(
    val ocrResult: PlateOcrResult,
    val detectorConfidence: Float
)

data class AutomaticPlateOcrDiagnostics(
    val isProcessing: Boolean = false,
    val detectorConfidence: Float? = null,
    val recognizedText: String? = null,
    val ocrConfidence: Float? = null,
    val ocrLatencyMs: Double? = null,
    val detectorBoxWidth: Int? = null,
    val detectorBoxHeight: Int? = null,
    val cropWidth: Int? = null,
    val cropHeight: Int? = null,
    val ocrInputWidth: Int? = null,
    val ocrInputHeight: Int? = null,
    val wasUpscaled: Boolean? = null,
    val message: String? = null
)