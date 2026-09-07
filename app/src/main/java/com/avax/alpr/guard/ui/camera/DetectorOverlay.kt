package com.avax.alpr.guard.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.avax.alpr.guard.ai.detector.PlateDetection
import com.avax.alpr.guard.camera.FrameMetadata
import kotlin.math.max

@Composable
fun DetectorOverlay(
    detections: List<PlateDetection>,
    frameMetadata: FrameMetadata?,
    modifier: Modifier = Modifier
) {
    if (frameMetadata == null || detections.isEmpty()) return

    Canvas(modifier = modifier) {
        val orientedWidth = if (frameMetadata.rotationDegrees == 90 || frameMetadata.rotationDegrees == 270) {
            frameMetadata.height.toFloat()
        } else {
            frameMetadata.width.toFloat()
        }

        val orientedHeight = if (frameMetadata.rotationDegrees == 90 || frameMetadata.rotationDegrees == 270) {
            frameMetadata.width.toFloat()
        } else {
            frameMetadata.height.toFloat()
        }

        val previewScale = max(size.width / orientedWidth, size.height / orientedHeight)
        val scaledWidth = orientedWidth * previewScale
        val scaledHeight = orientedHeight * previewScale
        val offsetX = (size.width - scaledWidth) / 2f
        val offsetY = (size.height - scaledHeight) / 2f

        detections.forEach { detection ->
            val oriented = rotateBoxToDisplay(detection, frameMetadata)

            val left = oriented.left * previewScale + offsetX
            val top = oriented.top * previewScale + offsetY
            val right = oriented.right * previewScale + offsetX
            val bottom = oriented.bottom * previewScale + offsetY

            if (right > left && bottom > top) {
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}

private data class DisplayBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun rotateBoxToDisplay(detection: PlateDetection, metadata: FrameMetadata): DisplayBox {
    val width = metadata.width.toFloat()
    val height = metadata.height.toFloat()

    return when (metadata.rotationDegrees) {
        0 -> DisplayBox(
            left = detection.left,
            top = detection.top,
            right = detection.right,
            bottom = detection.bottom
        )

        90 -> DisplayBox(
            left = height - detection.bottom,
            top = detection.left,
            right = height - detection.top,
            bottom = detection.right
        )

        180 -> DisplayBox(
            left = width - detection.right,
            top = height - detection.bottom,
            right = width - detection.left,
            bottom = height - detection.top
        )

        270 -> DisplayBox(
            left = detection.top,
            top = width - detection.right,
            right = detection.bottom,
            bottom = width - detection.left
        )

        else -> error("Unsupported rotationDegrees: ${metadata.rotationDegrees}")
    }
}