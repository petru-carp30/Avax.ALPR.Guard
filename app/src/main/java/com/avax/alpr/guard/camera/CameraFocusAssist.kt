package com.avax.alpr.guard.camera

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import java.util.concurrent.TimeUnit

internal object CameraFocusAssist {

    private const val AUTO_CANCEL_SECONDS = 3L
    private const val TAG = "CameraFocusAssist"

    fun requestCenterFocus(camera: Camera, previewView: PreviewView): Boolean {
        if (previewView.width <= 0 || previewView.height <= 0) return false

        return requestFocus(
            camera = camera,
            previewView = previewView,
            x = previewView.width / 2f,
            y = previewView.height / 2f
        )
    }

    fun requestFocus(camera: Camera, previewView: PreviewView, x: Float, y: Float): Boolean {
        if (previewView.width <= 0 || previewView.height <= 0) return false
        if (!x.isFinite() || !y.isFinite()) return false

        val point = previewView.meteringPointFactory.createPoint(
            x.coerceIn(0f, previewView.width.toFloat()),
            y.coerceIn(0f, previewView.height.toFloat())
        )

        val afProbe = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF
        ).build()

        if (!camera.cameraInfo.isFocusMeteringSupported(afProbe)) {
            Log.w(TAG, "AF focus metering is not supported.")
            return false
        }

        val aeProbe = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AE
        ).build()

        val meteringMode = if (camera.cameraInfo.isFocusMeteringSupported(aeProbe)) {
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        } else {
            FocusMeteringAction.FLAG_AF
        }

        val action = FocusMeteringAction.Builder(point, meteringMode)
            .setAutoCancelDuration(AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
            .build()

        return try {
            camera.cameraControl.startFocusAndMetering(action)
            true
        } catch (exception: Exception) {
            Log.w(TAG, "Focus and metering request failed.", exception)
            false
        }
    }
}