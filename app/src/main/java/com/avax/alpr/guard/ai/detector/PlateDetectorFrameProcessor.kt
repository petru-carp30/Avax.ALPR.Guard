package com.avax.alpr.guard.camera

import com.avax.alpr.guard.ai.detector.PlateDetection
import com.avax.alpr.guard.ai.detector.PlateDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

sealed interface DetectorRuntimeState {
    data object WaitingForFirstFrame : DetectorRuntimeState
    data object Ready : DetectorRuntimeState
    data class Unavailable(val reason: String) : DetectorRuntimeState
}

data class DetectorFrameDiagnostics(
    val runtimeState: DetectorRuntimeState = DetectorRuntimeState.WaitingForFirstFrame,
    val frameCount: Long = 0,
    val processedFrameCount: Long = 0,
    val skippedFrameCount: Long = 0,
    val detectionCount: Int = 0,
    val modelLoadTimeMs: Double? = null,
    val preprocessingTimeMs: Double? = null,
    val inferenceTimeMs: Double? = null,
    val postprocessingTimeMs: Double? = null,
    val totalProcessingTimeMs: Double? = null,
    val cadenceFps: Double? = null,
    val lastDetections: List<PlateDetection> = emptyList(),
    val frameMetadata: FrameMetadata? = null
)

class PlateDetectorFrameProcessor(
    private val detector: PlateDetector,
    private val minInferenceIntervalMs: Long = 0L,
    private val onDetections: (CameraFrame, List<PlateDetection>) -> Unit = { _, _ -> }
) : FrameProcessor {

    private val frameCounter = AtomicLong(0)
    private val _diagnostics = MutableStateFlow(DetectorFrameDiagnostics())

    val diagnostics: StateFlow<DetectorFrameDiagnostics> = _diagnostics.asStateFlow()

    private var disabled = false
    private var lastInferenceStartNanos: Long? = null
    private var lastSuccessfulCompletionNanos: Long? = null


    init {
        require(minInferenceIntervalMs >= 0L)
    }

    override fun process(frame: CameraFrame) {
        val frameCount = frameCounter.incrementAndGet()
        val current = _diagnostics.value

        if (disabled) {
            _diagnostics.value = current.copy(frameCount = frameCount)
            return
        }

        val now = System.nanoTime()
        val minimumIntervalNanos = minInferenceIntervalMs * 1_000_000L

        if (minimumIntervalNanos > 0L) {
            val lastStart = lastInferenceStartNanos

            if (lastStart != null && now - lastStart < minimumIntervalNanos) {
                _diagnostics.value = current.copy(
                    frameCount = frameCount,
                    skippedFrameCount = current.skippedFrameCount + 1
                )
                return
            }
        }

        lastInferenceStartNanos = now

        try {
            val result = detector.detect(frame)
            val completionNanos = System.nanoTime()

            val cadenceFps = lastSuccessfulCompletionNanos?.let { previous ->
                val delta = completionNanos - previous
                if (delta > 0L) 1_000_000_000.0 / delta.toDouble() else null
            }

            lastSuccessfulCompletionNanos = completionNanos

            _diagnostics.value = DetectorFrameDiagnostics(
                runtimeState = DetectorRuntimeState.Ready,
                frameCount = frameCount,
                processedFrameCount = current.processedFrameCount + 1,
                skippedFrameCount = current.skippedFrameCount,
                detectionCount = result.detections.size,
                modelLoadTimeMs = result.modelLoadTimeMs,
                preprocessingTimeMs = result.preprocessingTimeMs,
                inferenceTimeMs = result.inferenceTimeMs,
                postprocessingTimeMs = result.postprocessingTimeMs,
                totalProcessingTimeMs = result.totalProcessingTimeMs,
                cadenceFps = cadenceFps,
                lastDetections = result.detections,
                frameMetadata = frame.metadata
            )
            try {
                onDetections(frame, result.detections)
            } catch (_: Exception) {
            }
        } catch (exception: Exception) {
            disabled = true

            _diagnostics.value = current.copy(
                runtimeState = DetectorRuntimeState.Unavailable(
                    exception.message ?: exception::class.java.simpleName
                ),
                frameCount = frameCount,
                detectionCount = 0,
                lastDetections = emptyList()
            )
        }
    }
}