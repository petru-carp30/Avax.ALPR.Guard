package com.avax.alpr.guard.ai.detector

import android.graphics.ImageFormat
import com.avax.alpr.guard.camera.CameraFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Arrays
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
    val geometry: DetectorGeometry,
    val yuvConversionTimeMs: Double,
    val rotationTimeMs: Double,
    val resizeTensorTimeMs: Double
)

internal class PlateDetectorPreprocessor {

    private var bgrBuffer = ByteArray(0)
    private var orientedBuffer = ByteArray(0)

    private val tensorElementCount =
        DetectorModelConfig.INPUT_SIZE * DetectorModelConfig.INPUT_SIZE * 3

    private val tensorByteBuffer = ByteBuffer
        .allocateDirect(tensorElementCount * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val tensorBuffer = tensorByteBuffer.asFloatBuffer()
    private val tensorValues = FloatArray(tensorElementCount)

    private val x0Cache = IntArray(DetectorModelConfig.INPUT_SIZE)
    private val x1Cache = IntArray(DetectorModelConfig.INPUT_SIZE)
    private val xWeightCache = FloatArray(DetectorModelConfig.INPUT_SIZE)

    private val y0Cache = IntArray(DetectorModelConfig.INPUT_SIZE)
    private val y1Cache = IntArray(DetectorModelConfig.INPUT_SIZE)
    private val yWeightCache = FloatArray(DetectorModelConfig.INPUT_SIZE)

    fun prepare(frame: CameraFrame): PreparedDetectorInput {
        val width = frame.metadata.width
        val height = frame.metadata.height
        val rotation = frame.metadata.rotationDegrees

        require(width > 0 && height > 0) { "Invalid camera frame dimensions." }
        require(frame.metadata.imageFormat == ImageFormat.YUV_420_888) {
            "Unsupported image format: ${frame.metadata.imageFormat}"
        }
        require(frame.planes.size >= 3) { "YUV_420_888 frame requires three planes." }
        require(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270) {
            "Unsupported rotationDegrees: $rotation"
        }

        val sourceSize = width * height * 3
        if (bgrBuffer.size < sourceSize) {
            bgrBuffer = ByteArray(sourceSize)
        }

        val yuvStart = System.nanoTime()
        convertYuv420ToBgr(frame, bgrBuffer)
        val yuvConversionTimeMs = elapsedMilliseconds(yuvStart)

        val orientedWidth = if (rotation == 90 || rotation == 270) height else width
        val orientedHeight = if (rotation == 90 || rotation == 270) width else height

        val rotationStart = System.nanoTime()

        val oriented = if (rotation == 0) {
            bgrBuffer
        } else {
            val orientedSize = orientedWidth * orientedHeight * 3

            if (orientedBuffer.size < orientedSize) {
                orientedBuffer = ByteArray(orientedSize)
            }

            rotateBgr(
                source = bgrBuffer,
                width = width,
                height = height,
                rotation = rotation,
                destination = orientedBuffer
            )

            orientedBuffer
        }

        val rotationTimeMs = elapsedMilliseconds(rotationStart)

        val scale = minOf(
            DetectorModelConfig.INPUT_SIZE.toFloat() / orientedHeight.toFloat(),
            DetectorModelConfig.INPUT_SIZE.toFloat() / orientedWidth.toFloat()
        )

        val resizedWidth = (orientedWidth * scale)
            .toInt()
            .coerceIn(1, DetectorModelConfig.INPUT_SIZE)

        val resizedHeight = (orientedHeight * scale)
            .toInt()
            .coerceIn(1, DetectorModelConfig.INPUT_SIZE)

        val resizeStart = System.nanoTime()

        resizeBilinearToChw(
            source = oriented,
            sourceWidth = orientedWidth,
            sourceHeight = orientedHeight,
            resizedWidth = resizedWidth,
            resizedHeight = resizedHeight
        )

        val resizeTensorTimeMs = elapsedMilliseconds(resizeStart)

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
            ),
            yuvConversionTimeMs = yuvConversionTimeMs,
            rotationTimeMs = rotationTimeMs,
            resizeTensorTimeMs = resizeTensorTimeMs
        )
    }

    private fun convertYuv420ToBgr(
        frame: CameraFrame,
        output: ByteArray
    ) {
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
                val yValue =
                    yBuffer.get(yBase + yRow + x * yPlane.pixelStride).toInt() and 0xFF

                val uValue =
                    uBuffer.get(uBase + uvRow + (x shr 1) * uPlane.pixelStride).toInt() and 0xFF

                val vValue =
                    vBuffer.get(vBase + vRow + (x shr 1) * vPlane.pixelStride).toInt() and 0xFF

                val c = (yValue - 16).coerceAtLeast(0)
                val d = uValue - 128
                val e = vValue - 128

                val red =
                    ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)

                val green =
                    ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)

                val blue =
                    ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                output[outputIndex++] = blue.toByte()
                output[outputIndex++] = green.toByte()
                output[outputIndex++] = red.toByte()
            }
        }
    }

    private fun rotateBgr(
        source: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        destination: ByteArray
    ) {
        val destinationWidth =
            if (rotation == 90 || rotation == 270) height else width

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

                val destinationIndex =
                    (destinationY * destinationWidth + destinationX) * 3

                destination[destinationIndex] = source[sourceIndex]
                destination[destinationIndex + 1] = source[sourceIndex + 1]
                destination[destinationIndex + 2] = source[sourceIndex + 2]
            }
        }
    }

    private fun resizeBilinearToChw(
        source: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        resizedWidth: Int,
        resizedHeight: Int
    ) {
        val inputSize = DetectorModelConfig.INPUT_SIZE
        val channelSize = inputSize * inputSize

        Arrays.fill(
            tensorValues,
            DetectorModelConfig.PADDING_VALUE
        )

        val xScale =
            sourceWidth.toFloat() / resizedWidth.toFloat()

        val yScale =
            sourceHeight.toFloat() / resizedHeight.toFloat()

        for (destinationX in 0 until resizedWidth) {
            val sourceX =
                (destinationX + 0.5f) * xScale - 0.5f

            val sourceXFloor =
                floor(sourceX).toInt()

            x0Cache[destinationX] =
                sourceXFloor.coerceIn(0, sourceWidth - 1)

            x1Cache[destinationX] =
                (sourceXFloor + 1).coerceIn(0, sourceWidth - 1)

            xWeightCache[destinationX] =
                sourceX - sourceXFloor
        }

        for (destinationY in 0 until resizedHeight) {
            val sourceY =
                (destinationY + 0.5f) * yScale - 0.5f

            val sourceYFloor =
                floor(sourceY).toInt()

            y0Cache[destinationY] =
                sourceYFloor.coerceIn(0, sourceHeight - 1)

            y1Cache[destinationY] =
                (sourceYFloor + 1).coerceIn(0, sourceHeight - 1)

            yWeightCache[destinationY] =
                sourceY - sourceYFloor
        }

        for (destinationY in 0 until resizedHeight) {
            val y0 = y0Cache[destinationY]
            val y1 = y1Cache[destinationY]
            val yWeight = yWeightCache[destinationY]

            val row0 = y0 * sourceWidth * 3
            val row1 = y1 * sourceWidth * 3

            val outputRow = destinationY * inputSize

            for (destinationX in 0 until resizedWidth) {
                val x0 = x0Cache[destinationX]
                val x1 = x1Cache[destinationX]
                val xWeight = xWeightCache[destinationX]

                val topLeftIndex = row0 + x0 * 3
                val topRightIndex = row0 + x1 * 3
                val bottomLeftIndex = row1 + x0 * 3
                val bottomRightIndex = row1 + x1 * 3

                val topLeftBlue =
                    source[topLeftIndex].toInt() and 0xFF

                val topRightBlue =
                    source[topRightIndex].toInt() and 0xFF

                val bottomLeftBlue =
                    source[bottomLeftIndex].toInt() and 0xFF

                val bottomRightBlue =
                    source[bottomRightIndex].toInt() and 0xFF

                val topLeftGreen =
                    source[topLeftIndex + 1].toInt() and 0xFF

                val topRightGreen =
                    source[topRightIndex + 1].toInt() and 0xFF

                val bottomLeftGreen =
                    source[bottomLeftIndex + 1].toInt() and 0xFF

                val bottomRightGreen =
                    source[bottomRightIndex + 1].toInt() and 0xFF

                val topLeftRed =
                    source[topLeftIndex + 2].toInt() and 0xFF

                val topRightRed =
                    source[topRightIndex + 2].toInt() and 0xFF

                val bottomLeftRed =
                    source[bottomLeftIndex + 2].toInt() and 0xFF

                val bottomRightRed =
                    source[bottomRightIndex + 2].toInt() and 0xFF

                val topBlue =
                    topLeftBlue + (topRightBlue - topLeftBlue) * xWeight

                val bottomBlue =
                    bottomLeftBlue + (bottomRightBlue - bottomLeftBlue) * xWeight

                val topGreen =
                    topLeftGreen + (topRightGreen - topLeftGreen) * xWeight

                val bottomGreen =
                    bottomLeftGreen + (bottomRightGreen - bottomLeftGreen) * xWeight

                val topRed =
                    topLeftRed + (topRightRed - topLeftRed) * xWeight

                val bottomRed =
                    bottomLeftRed + (bottomRightRed - bottomLeftRed) * xWeight

                val blue =
                    (topBlue + (bottomBlue - topBlue) * yWeight)
                        .roundToInt()
                        .coerceIn(0, 255)
                        .toFloat()

                val green =
                    (topGreen + (bottomGreen - topGreen) * yWeight)
                        .roundToInt()
                        .coerceIn(0, 255)
                        .toFloat()

                val red =
                    (topRed + (bottomRed - topRed) * yWeight)
                        .roundToInt()
                        .coerceIn(0, 255)
                        .toFloat()

                val outputPixel =
                    outputRow + destinationX

                tensorValues[outputPixel] =
                    blue

                tensorValues[channelSize + outputPixel] =
                    green

                tensorValues[channelSize * 2 + outputPixel] =
                    red
            }
        }

        tensorBuffer.clear()
        tensorBuffer.put(tensorValues)
        tensorBuffer.position(0)
        tensorBuffer.limit(tensorBuffer.capacity())
    }

    private fun elapsedMilliseconds(
        startNanos: Long
    ): Double {
        return (
                System.nanoTime() - startNanos
                ) / 1_000_000.0
    }
}