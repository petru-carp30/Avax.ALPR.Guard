package com.avax.alpr.guard.ai.detector

import com.avax.alpr.guard.camera.CameraFrame

data class PlateDetection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

data class PlateDetectorResult(
    val detections: List<PlateDetection>,
    val modelLoadTimeMs: Double?,
    val preprocessingTimeMs: Double,
    val yuvConversionTimeMs: Double,
    val rotationTimeMs: Double,
    val resizeTensorTimeMs: Double,
    val inferenceTimeMs: Double,
    val postprocessingTimeMs: Double,
    val totalProcessingTimeMs: Double
)

interface PlateDetector : AutoCloseable {
    fun detect(frame: CameraFrame): PlateDetectorResult
    override fun close() = Unit
}