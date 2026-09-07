package com.avax.alpr.guard.ai.detector

import com.avax.alpr.guard.camera.FrameMetadata
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

internal data class DetectorBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

internal class PlateDetectorPostprocessor {

    fun decode(output: FloatBuffer, geometry: DetectorGeometry, frameMetadata: FrameMetadata): List<PlateDetection> {
        require(output.capacity() >= DetectorModelConfig.CANDIDATE_COUNT * DetectorModelConfig.CANDIDATE_VALUES) {
            "Detector output is smaller than expected."
        }

        val candidates = ArrayList<DetectorBox>()

        for (index in 0 until DetectorModelConfig.CANDIDATE_COUNT) {
            val offset = index * DetectorModelConfig.CANDIDATE_VALUES

            val centerX = output.get(offset)
            val centerY = output.get(offset + 1)
            val width = output.get(offset + 2)
            val height = output.get(offset + 3)
            val objectness = output.get(offset + 4)
            val classConfidence = output.get(offset + 5)

            val confidence = objectness * classConfidence

            if (!confidence.isFinite() || confidence < DetectorModelConfig.CONFIDENCE_THRESHOLD) continue
            if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) continue

            candidates += xywhToBox(centerX, centerY, width, height, confidence)
        }

        return nonMaxSuppression(candidates, DetectorModelConfig.NMS_THRESHOLD).mapNotNull {
            mapToOriginal(it, geometry, frameMetadata)
        }
    }

    internal fun xywhToBox(centerX: Float, centerY: Float, width: Float, height: Float, confidence: Float): DetectorBox {
        return DetectorBox(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
            confidence = confidence
        )
    }

    internal fun nonMaxSuppression(boxes: List<DetectorBox>, threshold: Float): List<DetectorBox> {
        val sorted = boxes.sortedByDescending { it.confidence }
        val kept = ArrayList<DetectorBox>()

        for (candidate in sorted) {
            if (kept.none { intersectionOverUnion(candidate, it) > threshold }) {
                kept += candidate
            }
        }

        return kept
    }

    internal fun intersectionOverUnion(first: DetectorBox, second: DetectorBox): Float {
        val intersectionLeft = max(first.left, second.left)
        val intersectionTop = max(first.top, second.top)
        val intersectionRight = min(first.right, second.right)
        val intersectionBottom = min(first.bottom, second.bottom)

        val intersectionWidth = max(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight

        val firstArea = max(0f, first.right - first.left) * max(0f, first.bottom - first.top)
        val secondArea = max(0f, second.right - second.left) * max(0f, second.bottom - second.top)
        val unionArea = firstArea + secondArea - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    internal fun mapToOriginal(box: DetectorBox, geometry: DetectorGeometry, frameMetadata: FrameMetadata): PlateDetection? {
        val orientedLeft = (box.left / geometry.scale).coerceIn(0f, geometry.orientedWidth.toFloat())
        val orientedTop = (box.top / geometry.scale).coerceIn(0f, geometry.orientedHeight.toFloat())
        val orientedRight = (box.right / geometry.scale).coerceIn(0f, geometry.orientedWidth.toFloat())
        val orientedBottom = (box.bottom / geometry.scale).coerceIn(0f, geometry.orientedHeight.toFloat())

        if (orientedLeft >= orientedRight || orientedTop >= orientedBottom) return null

        val frameWidth = frameMetadata.width.toFloat()
        val frameHeight = frameMetadata.height.toFloat()

        val mapped = when (frameMetadata.rotationDegrees) {
            0 -> floatArrayOf(orientedLeft, orientedTop, orientedRight, orientedBottom)

            90 -> floatArrayOf(
                orientedTop,
                frameHeight - orientedRight,
                orientedBottom,
                frameHeight - orientedLeft
            )

            180 -> floatArrayOf(
                frameWidth - orientedRight,
                frameHeight - orientedBottom,
                frameWidth - orientedLeft,
                frameHeight - orientedTop
            )

            270 -> floatArrayOf(
                frameWidth - orientedBottom,
                orientedLeft,
                frameWidth - orientedTop,
                orientedRight
            )

            else -> return null
        }

        val left = mapped[0].coerceIn(0f, frameWidth)
        val top = mapped[1].coerceIn(0f, frameHeight)
        val right = mapped[2].coerceIn(0f, frameWidth)
        val bottom = mapped[3].coerceIn(0f, frameHeight)

        if (left >= right || top >= bottom) return null

        return PlateDetection(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            confidence = box.confidence.coerceIn(0f, 1f)
        )
    }
}