package com.avax.alpr.guard.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraZoomMathTest {

    @Test
    fun clampZoomRatio_clampsToSupportedRange() {
        assertEquals(1f, CameraZoomMath.clampZoomRatio(0.5f, 1f, 13.5f)!!, 0.001f)
        assertEquals(5f, CameraZoomMath.clampZoomRatio(5f, 1f, 13.5f)!!, 0.001f)
        assertEquals(13.5f, CameraZoomMath.clampZoomRatio(20f, 1f, 13.5f)!!, 0.001f)
    }

    @Test
    fun scaleZoomRatio_multipliesCurrentZoom() {
        val result = CameraZoomMath.scaleZoomRatio(2f, 1.5f, 1f, 13.5f)

        assertEquals(3f, result!!, 0.001f)
    }

    @Test
    fun scaleZoomRatio_clampsToMaximum() {
        val result = CameraZoomMath.scaleZoomRatio(10f, 2f, 1f, 13.5f)

        assertEquals(13.5f, result!!, 0.001f)
    }

    @Test
    fun resetZoomRatio_returnsOne() {
        val result = CameraZoomMath.resetZoomRatio(1f, 13.5f)

        assertEquals(1f, result!!, 0.001f)
    }

    @Test
    fun invalidZoomState_returnsNull() {
        assertNull(CameraZoomMath.clampZoomRatio(2f, 5f, 1f))
        assertNull(CameraZoomMath.scaleZoomRatio(2f, Float.NaN, 1f, 13.5f))
    }
}