package com.avax.alpr.guard.camera

import com.avax.alpr.guard.ai.detector.PlateDetector
import com.avax.alpr.guard.ai.detector.PlateDetectorResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateDetectorFrameProcessorTest {

    @Test
    fun detectorFailureDoesNotEscapeFrameProcessor() {
        var calls = 0

        val detector = object : PlateDetector {
            override fun detect(frame: CameraFrame): PlateDetectorResult {
                calls++
                throw IllegalStateException("Detector failure")
            }
        }

        val processor = PlateDetectorFrameProcessor(detector)

        processor.process(testFrame())
        processor.process(testFrame())

        assertTrue(processor.diagnostics.value.runtimeState is DetectorRuntimeState.Unavailable)
        assertEquals(1, calls)
    }

    @Test
    fun fixedCadenceCanSkipFrames() {
        var calls = 0

        val detector = object : PlateDetector {
            override fun detect(frame: CameraFrame): PlateDetectorResult {
                calls++

                return PlateDetectorResult(
                    detections = emptyList(),
                    modelLoadTimeMs = 10.0,
                    preprocessingTimeMs = 1.0,
                    yuvConversionTimeMs = 0.4,
                    rotationTimeMs = 0.2,
                    resizeTensorTimeMs = 0.4,
                    inferenceTimeMs = 2.0,
                    postprocessingTimeMs = 1.0,
                    totalProcessingTimeMs = 4.0
                )
            }
        }

        val processor = PlateDetectorFrameProcessor(
            detector = detector,
            minInferenceIntervalMs = 60_000L
        )

        processor.process(testFrame())
        processor.process(testFrame())

        assertEquals(1, calls)
        assertEquals(1L, processor.diagnostics.value.skippedFrameCount)
    }

    private fun testFrame(): CameraFrame {
        return CameraFrame(
            metadata = FrameMetadata(
                width = 1280,
                height = 720,
                rotationDegrees = 90,
                timestampNanos = 0L,
                imageFormat = 35
            ),
            planes = emptyList()
        )
    }
}