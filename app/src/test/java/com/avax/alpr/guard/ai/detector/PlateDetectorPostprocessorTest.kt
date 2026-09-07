package com.avax.alpr.guard.ai.detector

import com.avax.alpr.guard.camera.FrameMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.FloatBuffer

class PlateDetectorPostprocessorTest {

    private val postprocessor = PlateDetectorPostprocessor()

    @Test
    fun confidenceAndXywhConversionAreCorrect() {
        val output = emptyOutput()

        output.put(0, 256f)
        output.put(1, 256f)
        output.put(2, 100f)
        output.put(3, 40f)
        output.put(4, 0.5f)
        output.put(5, 0.6f)

        val detections = postprocessor.decode(
            output,
            geometry(scale = 1f),
            metadata(rotation = 0)
        )

        assertEquals(1, detections.size)

        val detection = detections.single()

        assertEquals(206f, detection.left, 0.001f)
        assertEquals(236f, detection.top, 0.001f)
        assertEquals(306f, detection.right, 0.001f)
        assertEquals(276f, detection.bottom, 0.001f)
        assertEquals(0.30f, detection.confidence, 0.0001f)
    }

    @Test
    fun confidenceBelowThresholdIsRejected() {
        val output = emptyOutput()

        output.put(0, 256f)
        output.put(1, 256f)
        output.put(2, 100f)
        output.put(3, 40f)
        output.put(4, 0.5f)
        output.put(5, 0.4f)

        val detections = postprocessor.decode(
            output,
            geometry(scale = 1f),
            metadata(rotation = 0)
        )

        assertTrue(detections.isEmpty())
    }

    @Test
    fun nmsSuppressesLowerConfidenceOverlap() {
        val output = emptyOutput()

        setCandidate(output, 0, 256f, 256f, 100f, 40f, 0.9f, 1f)
        setCandidate(output, 1, 256f, 256f, 100f, 40f, 0.8f, 1f)

        val detections = postprocessor.decode(
            output,
            geometry(scale = 1f),
            metadata(rotation = 0)
        )

        assertEquals(1, detections.size)
        assertEquals(0.9f, detections.single().confidence, 0.0001f)
    }

    @Test
    fun inverseRotation90ReturnsOriginalFrameCoordinates() {
        val box = DetectorBox(
            left = 80f,
            top = 40f,
            right = 160f,
            bottom = 80f,
            confidence = 0.9f
        )

        val detection = postprocessor.mapToOriginal(
            box = box,
            geometry = DetectorGeometry(
                scale = 0.8f,
                orientedWidth = 480,
                orientedHeight = 640,
                resizedWidth = 384,
                resizedHeight = 512
            ),
            frameMetadata = FrameMetadata(
                width = 640,
                height = 480,
                rotationDegrees = 90,
                timestampNanos = 0L,
                imageFormat = 35
            )
        )!!

        assertEquals(50f, detection.left, 0.001f)
        assertEquals(280f, detection.top, 0.001f)
        assertEquals(100f, detection.right, 0.001f)
        assertEquals(380f, detection.bottom, 0.001f)
    }

    private fun emptyOutput(): FloatBuffer {
        return FloatBuffer.allocate(
            DetectorModelConfig.CANDIDATE_COUNT * DetectorModelConfig.CANDIDATE_VALUES
        )
    }

    private fun setCandidate(
        output: FloatBuffer,
        candidate: Int,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        objectness: Float,
        classConfidence: Float
    ) {
        val offset = candidate * DetectorModelConfig.CANDIDATE_VALUES

        output.put(offset, centerX)
        output.put(offset + 1, centerY)
        output.put(offset + 2, width)
        output.put(offset + 3, height)
        output.put(offset + 4, objectness)
        output.put(offset + 5, classConfidence)
    }

    private fun geometry(scale: Float): DetectorGeometry {
        return DetectorGeometry(
            scale = scale,
            orientedWidth = 512,
            orientedHeight = 512,
            resizedWidth = 512,
            resizedHeight = 512
        )
    }

    private fun metadata(rotation: Int): FrameMetadata {
        return FrameMetadata(
            width = 512,
            height = 512,
            rotationDegrees = rotation,
            timestampNanos = 0L,
            imageFormat = 35
        )
    }
}