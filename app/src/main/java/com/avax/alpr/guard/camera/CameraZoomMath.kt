package com.avax.alpr.guard.camera

internal object CameraZoomMath {

    fun clampZoomRatio(requested: Float, minZoomRatio: Float, maxZoomRatio: Float): Float? {
        if (!requested.isFinite() || !minZoomRatio.isFinite() || !maxZoomRatio.isFinite()) return null
        if (minZoomRatio <= 0f || maxZoomRatio < minZoomRatio) return null

        return requested.coerceIn(minZoomRatio, maxZoomRatio)
    }

    fun scaleZoomRatio(
        currentZoomRatio: Float,
        scaleFactor: Float,
        minZoomRatio: Float,
        maxZoomRatio: Float
    ): Float? {
        if (!currentZoomRatio.isFinite() || !scaleFactor.isFinite() || scaleFactor <= 0f) return null

        return clampZoomRatio(
            requested = currentZoomRatio * scaleFactor,
            minZoomRatio = minZoomRatio,
            maxZoomRatio = maxZoomRatio
        )
    }

    fun resetZoomRatio(minZoomRatio: Float, maxZoomRatio: Float): Float? {
        return clampZoomRatio(
            requested = 1f,
            minZoomRatio = minZoomRatio,
            maxZoomRatio = maxZoomRatio
        )
    }
}