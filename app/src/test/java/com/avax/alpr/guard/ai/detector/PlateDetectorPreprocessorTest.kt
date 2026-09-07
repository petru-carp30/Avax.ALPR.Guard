package com.avax.alpr.guard.ai.detector

import android.graphics.ImageFormat
import com.avax.alpr.guard.camera.CameraFrame
import com.avax.alpr.guard.camera.FrameMetadata
import com.avax.alpr.guard.camera.FramePlane
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class PlateDetectorPreprocessorTest {

    @Test
    fun preprocessingProducesExpectedTensorShapeAndBottomPadding() {
        val prepared = PlateDetectorPreprocessor().prepare(
            testFrame(width = 4, height = 2, rotation = 0)
        )

        assertEquals(1 * 3 * 512 * 512, prepared.tensor.capacity())
        assertEquals(512, prepared.geometry.resizedWidth)
        assertEquals(256, prepared.geometry.resizedHeight)

        val pixel = 300 * 512 + 10
        val channelSize = 512 * 512

        assertEquals(114f, prepared.tensor.get(pixel), 0f)
        assertEquals(114f, prepared.tensor.get(channelSize + pixel), 0f)
        assertEquals(114f, prepared.tensor.get(channelSize * 2 + pixel), 0f)
    }

    @Test
    fun rotation90ProducesRightSidePadding() {
        val prepared = PlateDetectorPreprocessor().prepare(
            testFrame(width = 4, height = 2, rotation = 90)
        )

        assertEquals(2, prepared.geometry.orientedWidth)
        assertEquals(4, prepared.geometry.orientedHeight)
        assertEquals(256, prepared.geometry.resizedWidth)
        assertEquals(512, prepared.geometry.resizedHeight)

        val pixel = 10 * 512 + 300
        assertEquals(114f, prepared.tensor.get(pixel), 0f)
    }

    private fun testFrame(width: Int, height: Int, rotation: Int): CameraFrame {
        val y = ByteArray(width * height) { 128.toByte() }
        val uvWidth = width / 2
        val uvHeight = height / 2
        val u = ByteArray(uvWidth * uvHeight) { 128.toByte() }
        val v = ByteArray(uvWidth * uvHeight) { 128.toByte() }

        return CameraFrame(
            metadata = FrameMetadata(
                width = width,
                height = height,
                rotationDegrees = rotation,
                timestampNanos = 0L,
                imageFormat = ImageFormat.YUV_420_888
            ),
            planes = listOf(
                FramePlane(ByteBuffer.wrap(y), rowStride = width, pixelStride = 1),
                FramePlane(ByteBuffer.wrap(u), rowStride = uvWidth, pixelStride = 1),
                FramePlane(ByteBuffer.wrap(v), rowStride = uvWidth, pixelStride = 1)
            )
        )
    }
}