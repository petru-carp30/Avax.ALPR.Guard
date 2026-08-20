package com.avax.alpr.guard.camera

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

sealed interface CameraPermissionState {
    data object NotRequested : CameraPermissionState
    data object Granted : CameraPermissionState
    data object Denied : CameraPermissionState
    data object PermanentlyDenied : CameraPermissionState
}

sealed interface CameraRuntimeState {
    data object Starting : CameraRuntimeState
    data object Active : CameraRuntimeState
    data object Unavailable : CameraRuntimeState
    data object Error : CameraRuntimeState
}

data class FrameMetadata(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val imageFormat: Int
)

data class FramePlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int
)

data class CameraFrame(
    val metadata: FrameMetadata,
    val planes: List<FramePlane>
)

data class FrameDiagnostics(
    val frameCount: Long,
    val metadata: FrameMetadata
)

fun interface FrameProcessor {
    /**
     * Processing is synchronous. Implementations must not retain CameraFrame or its plane buffers after this method returns.
     */
    fun process(frame: CameraFrame)
}

object CameraPermissionStateResolver {

    fun resolve(isGranted: Boolean, hasRequestedPermission: Boolean, shouldShowRationale: Boolean): CameraPermissionState {
        if (isGranted) return CameraPermissionState.Granted
        if (!hasRequestedPermission) return CameraPermissionState.NotRequested
        return if (shouldShowRationale) CameraPermissionState.Denied else CameraPermissionState.PermanentlyDenied
    }
}

internal object FrameMapper {

    fun from(imageProxy: ImageProxy): CameraFrame {
        val metadata = metadata(
            width = imageProxy.width,
            height = imageProxy.height,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
            timestampNanos = imageProxy.imageInfo.timestamp,
            imageFormat = imageProxy.format
        )

        val planes = imageProxy.planes.map { plane ->
            FramePlane(
                buffer = plane.buffer.asReadOnlyBuffer(),
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride
            )
        }

        return CameraFrame(metadata = metadata, planes = planes)
    }

    fun metadata(width: Int, height: Int, rotationDegrees: Int, timestampNanos: Long, imageFormat: Int): FrameMetadata {
        return FrameMetadata(
            width = width,
            height = height,
            rotationDegrees = rotationDegrees,
            timestampNanos = timestampNanos,
            imageFormat = imageFormat
        )
    }
}