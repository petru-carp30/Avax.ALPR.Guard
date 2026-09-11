package com.avax.alpr.guard.ai.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.avax.alpr.guard.ai.detector.PlateDetection
import com.avax.alpr.guard.camera.CameraFrame
import com.avax.alpr.guard.camera.FrameMetadata
import kotlin.math.ceil
import kotlin.math.floor

internal class PlateCropper {

    fun selectBestDetection(metadata: FrameMetadata, detections: List<PlateDetection>): PlateDetection? {
        return detections
            .sortedByDescending { it.confidence }
            .firstOrNull { calculateCropRect(metadata, it) != null }
    }

    fun crop(frame: CameraFrame, detection: PlateDetection): Bitmap? {
        if (frame.planes.size < 3) return null

        val cropRect = calculateCropRect(frame.metadata, detection) ?: return null
        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()

        val pixels = IntArray(cropWidth * cropHeight)

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

        for (y in cropRect.top until cropRect.bottom) {
            val yRow = y * yPlane.rowStride
            val uRow = (y shr 1) * uPlane.rowStride
            val vRow = (y shr 1) * vPlane.rowStride

            for (x in cropRect.left until cropRect.right) {
                val yValue = yBuffer.get(yBase + yRow + x * yPlane.pixelStride).toInt() and 0xFF
                val uValue = uBuffer.get(uBase + uRow + (x shr 1) * uPlane.pixelStride).toInt() and 0xFF
                val vValue = vBuffer.get(vBase + vRow + (x shr 1) * vPlane.pixelStride).toInt() and 0xFF

                val c = (yValue - 16).coerceAtLeast(0)
                val d = uValue - 128
                val e = vValue - 128

                val red = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val green = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val blue = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)

                pixels[outputIndex++] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }

        val bitmap = Bitmap.createBitmap(pixels, cropWidth, cropHeight, Bitmap.Config.ARGB_8888)

        val rotation = frame.metadata.rotationDegrees
        if (rotation == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
        }

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        if (rotatedBitmap !== bitmap && !bitmap.isRecycled) bitmap.recycle()

        return rotatedBitmap
    }

    internal fun calculateCropRect(metadata: FrameMetadata, detection: PlateDetection): Rect? {
        val detectionWidth = detection.right - detection.left
        val detectionHeight = detection.bottom - detection.top

        if (detectionWidth <= 0f || detectionHeight <= 0f) return null

        val horizontalPadding = detectionWidth * PADDING_FRACTION
        val verticalPadding = detectionHeight * PADDING_FRACTION

        val left = floor(detection.left - horizontalPadding).toInt().coerceIn(0, metadata.width)
        val top = floor(detection.top - verticalPadding).toInt().coerceIn(0, metadata.height)
        val right = ceil(detection.right + horizontalPadding).toInt().coerceIn(0, metadata.width)
        val bottom = ceil(detection.bottom + verticalPadding).toInt().coerceIn(0, metadata.height)

        if (right - left < MINIMUM_CROP_WIDTH_PX) return null
        if (bottom - top < MINIMUM_CROP_HEIGHT_PX) return null

        return Rect(left, top, right, bottom)
    }

    private companion object {
        const val PADDING_FRACTION = 0.08f
        const val MINIMUM_CROP_WIDTH_PX = 8
        const val MINIMUM_CROP_HEIGHT_PX = 4
    }
}