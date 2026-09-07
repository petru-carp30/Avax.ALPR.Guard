package com.avax.alpr.guard.ai.detector

import android.graphics.ImageFormat
import com.avax.alpr.guard.camera.CameraFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class DetectorGeometry(
    val scale: Float,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val resizedWidth: Int,
    val resizedHeight: Int
)

internal data class PreparedDetectorInput(
    val tensor: FloatBuffer,
    val geometry: DetectorGeometry
)

internal class PlateDetectorPreprocessor {

    private var bgrBuffer = ByteArray(0)
    private var orientedBuffer = ByteArray(0)

    private val tensorByteBuffer = ByteBuffer
        .allocateDirect(DetectorModelConfig.INPUT_SIZE * DetectorModelConfig.INPUT_SIZE * 3 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val tensorBuffer = tensorByteBuffer.asFloatBuffer()

    fun prepare(frame: CameraFrame): PreparedDetectorInput {
        val width = frame.metadata.width
        val height = frame.metadata.height
        val rotation = frame.metadata.rotationDegrees

        require(width > 0 && height > 0) { "Invalid camera frame dimensions." }
        require(frame.metadata.imageFormat == ImageFormat.YUV_420_888) { "Unsupported image format: ${frame.metadata.imageFormat}" }
        require(frame.planes.size >= 3) { "YUV_420_888 frame requires three planes." }
        require(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270) { "Unsupported rotationDegrees: $rotation" }

        val sourceSize = width * height * 3
        if (bgrBuffer.size < sourceSize) bgrBuffer = ByteArray(sourceSize)

        convertYuv420ToBgr(frame, bgrBuffer)

        val orientedWidth = if (rotation == 90 || rotation == 270) height else width
        val orientedHeight = if (rotation == 90 || rotation == 270) width else height

        val oriented = if (rotation == 0) {
            bgrBuffer
        } else {
            val orientedSize = orientedWidth * orientedHeight * 3
            if (orientedBuffer.size < orientedSize) orientedBuffer = ByteArray(orientedSize)
            rotateBgr(bgrBuffer, width, height, rotation, orientedBuffer)
            orientedBuffer
        }

        val scale = minOf(
            DetectorModelConfig.INPUT_SIZE.toFloat() / orientedHeight.toFloat(),
            DetectorModelConfig.INPUT_SIZE.toFloat() / orientedWidth.toFloat()
        )

        val resizedWidth = (orientedWidth * scale).toInt().coerceIn(1, DetectorModelConfig.INPUT_SIZE)
        val resizedHeight = (orientedHeight * scale).toInt().coerceIn(1, DetectorModelConfig.INPUT_SIZE)

        resizeBilinearToChw(oriented, orientedWidth, orientedHeight, resizedWidth, resizedHeight)

        tensorBuffer.position(0)
        tensorBuffer.limit(tensorBuffer.capacity())

        return PreparedDetectorInput(
            tensor = tensorBuffer,
            geometry = DetectorGeometry(
                scale = scale,
                orientedWidth = orientedWidth,
                orientedHeight = orientedHeight,
                resizedWidth = resizedWidth,
                resizedHeight = resizedHeight
            )
        )
    }

    private fun convertYuv420ToBgr(frame: CameraFrame, output: ByteArray) {
        val width = frame.metadata.width
        val height = frame.metadata.height

        val yPlane = frame.planes[0]
        val uPlane = frame.planes[1]
        val vPlane = frame.planes[2]

        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()

        val yBase = yBuffer.position()
        val uBase = uBuffer.position()
        val vBase = vBuffer.position()

        var outputIndex = 0

        for (y in 0 until height) {
            val yRow = y * yPlane.rowStride
            val uvRow = (y shr 1) * uPlane.rowStride
            val vRow = (y shr 1) * vPlane.rowStride

            for (x in 0 until width) {
                val yValue = yBuffer.get(yBase + yRow + x * yPlane.pixelStride).toInt() and 0xFF
                val uValue = uBuffer.get(uBase + uvRow + (x shr 1) * uPlane.pixelStride).toInt() and 0xFF
                val vValue = vBuffer.get(vBase + vRow + (x shr 1) * vPlane.pixelStride).toInt() and 0xFF

                val c = (yValue - 16).coerceAtLeast(0)
                val d = uValue - 128
                val e = vValue - 128

                val red = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val green = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val blue = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                output[outputIndex++] = blue.toByte()
                output[outputIndex++] = green.toByte()
                output[outputIndex++] = red.toByte()
            }
        }
    }

    private fun rotateBgr(source: ByteArray, width: Int, height: Int, rotation: Int, destination: ByteArray) {
        val destinationWidth = if (rotation == 90 || rotation == 270) height else width

        for (y in 0 until height) {
            for (x in 0 until width) {
                val destinationX: Int
                val destinationY: Int

                when (rotation) {
                    90 -> {
                        destinationX = height - 1 - y
                        destinationY = x
                    }

                    180 -> {
                        destinationX = width - 1 - x
                        destinationY = height - 1 - y
                    }

                    270 -> {
                        destinationX = y
                        destinationY = width - 1 - x
                    }

                    else -> error("Unsupported rotation: $rotation")
                }

                val sourceIndex = (y * width + x) * 3
                val destinationIndex = (destinationY * destinationWidth + destinationX) * 3

                destination[destinationIndex] = source[sourceIndex]
                destination[destinationIndex + 1] = source[sourceIndex + 1]
                destination[destinationIndex + 2] = source[sourceIndex + 2]
            }
        }
    }

    private fun resizeBilinearToChw(source: ByteArray, sourceWidth: Int, sourceHeight: Int, resizedWidth: Int, resizedHeight: Int) {
        val inputSize = DetectorModelConfig.INPUT_SIZE
        val channelSize = inputSize * inputSize

        tensorBuffer.clear()

        for (index in 0 until tensorBuffer.capacity()) {
            tensorBuffer.put(index, DetectorModelConfig.PADDING_VALUE)
        }

        val xScale = sourceWidth.toFloat() / resizedWidth.toFloat()
        val yScale = sourceHeight.toFloat() / resizedHeight.toFloat()

        for (destinationY in 0 until resizedHeight) {
            val sourceY = (destinationY + 0.5f) * yScale - 0.5f
            val sourceYFloor = floor(sourceY).toInt()
            val y0 = sourceYFloor.coerceIn(0, sourceHeight - 1)
            val y1 = (sourceYFloor + 1).coerceIn(0, sourceHeight - 1)
            val yWeight = sourceY - sourceYFloor

            for (destinationX in 0 until resizedWidth) {
                val sourceX = (destinationX + 0.5f) * xScale - 0.5f
                val sourceXFloor = floor(sourceX).toInt()
                val x0 = sourceXFloor.coerceIn(0, sourceWidth - 1)
                val x1 = (sourceXFloor + 1).coerceIn(0, sourceWidth - 1)
                val xWeight = sourceX - sourceXFloor

                val outputPixel = destinationY * inputSize + destinationX

                for (channel in 0..2) {
                    val topLeft = source[(y0 * sourceWidth + x0) * 3 + channel].toInt() and 0xFF
                    val topRight = source[(y0 * sourceWidth + x1) * 3 + channel].toInt() and 0xFF
                    val bottomLeft = source[(y1 * sourceWidth + x0) * 3 + channel].toInt() and 0xFF
                    val bottomRight = source[(y1 * sourceWidth + x1) * 3 + channel].toInt() and 0xFF

                    val top = topLeft + (topRight - topLeft) * xWeight
                    val bottom = bottomLeft + (bottomRight - bottomLeft) * xWeight
                    val value = (top + (bottom - top) * yWeight).roundToInt().coerceIn(0, 255).toFloat()

                    tensorBuffer.put(channel * channelSize + outputPixel, value)
                }
            }
        }
    }
}