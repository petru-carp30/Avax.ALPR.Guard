package com.avax.alpr.guard.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFrameAnalyzerTest {

    @Test
    fun metadataMappingPreservesDimensionsTimestampAndFormat() {
        val metadata = FrameMapper.metadata(
            width = 1920,
            height = 1080,
            rotationDegrees = 90,
            timestampNanos = 123456789L,
            imageFormat = 35
        )

        assertEquals(1920, metadata.width)
        assertEquals(1080, metadata.height)
        assertEquals(123456789L, metadata.timestampNanos)
        assertEquals(35, metadata.imageFormat)
    }

    @Test
    fun metadataMappingPreservesRotation() {
        val metadata = FrameMapper.metadata(
            width = 1280,
            height = 720,
            rotationDegrees = 270,
            timestampNanos = 1L,
            imageFormat = 35
        )

        assertEquals(270, metadata.rotationDegrees)
    }

    @Test
    fun analyzerInvokesFrameProcessor() {
        val frame = testFrame()
        var receivedFrame: CameraFrame? = null

        val analyzer = CameraFrameAnalyzer(
            frameProcessor = FrameProcessor { receivedFrame = it }
        )

        analyzer.analyzeFrame(
            frameProvider = { frame },
            closeFrame = {}
        )

        assertSame(frame, receivedFrame)
    }

    @Test
    fun analyzerClosesFrameAfterSuccessfulProcessing() {
        var closed = false

        val analyzer = CameraFrameAnalyzer(
            frameProcessor = FrameProcessor {}
        )

        analyzer.analyzeFrame(
            frameProvider = { testFrame() },
            closeFrame = { closed = true }
        )

        assertTrue(closed)
    }

    @Test
    fun analyzerClosesFrameWhenProcessorThrows() {
        var closed = false

        val analyzer = CameraFrameAnalyzer(
            frameProcessor = FrameProcessor { throw IllegalStateException("Test failure") }
        )

        analyzer.analyzeFrame(
            frameProvider = { testFrame() },
            closeFrame = { closed = true }
        )

        assertTrue(closed)
    }

    @Test
    fun analyzerClosesFrameWhenFrameMappingThrows() {
        var closed = false

        val analyzer = CameraFrameAnalyzer(
            frameProcessor = FrameProcessor {}
        )

        analyzer.analyzeFrame(
            frameProvider = { throw IllegalArgumentException("Mapping failure") },
            closeFrame = { closed = true }
        )

        assertTrue(closed)
    }

    @Test
    fun analyzerReportsProcessorFailure() {
        var reportedFailure: Exception? = null

        val analyzer = CameraFrameAnalyzer(
            frameProcessor = FrameProcessor { throw IllegalStateException("Test failure") },
            onFailure = { reportedFailure = it }
        )

        analyzer.analyzeFrame(
            frameProvider = { testFrame() },
            closeFrame = {}
        )

        assertTrue(reportedFailure is IllegalStateException)
    }

    @Test
    fun developmentProcessorReceivesMetadataWithoutRetainingCameraFrames() {
        val processor = DevelopmentFrameProcessor()
        val frame = testFrame()

        processor.process(frame)

        val diagnostics = processor.diagnostics.value
        assertEquals(1L, diagnostics?.frameCount)
        assertEquals(frame.metadata, diagnostics?.metadata)
    }

    private fun testFrame(): CameraFrame {
        return CameraFrame(
            metadata = FrameMetadata(
                width = 1280,
                height = 720,
                rotationDegrees = 90,
                timestampNanos = 123L,
                imageFormat = 35
            ),
            planes = emptyList()
        )
    }
}