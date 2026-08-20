package com.avax.alpr.guard.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionStateResolverTest {

    @Test
    fun grantedPermissionReturnsGranted() {
        val result = CameraPermissionStateResolver.resolve(
            isGranted = true,
            hasRequestedPermission = true,
            shouldShowRationale = false
        )

        assertEquals(CameraPermissionState.Granted, result)
    }

    @Test
    fun permissionNotRequestedReturnsNotRequested() {
        val result = CameraPermissionStateResolver.resolve(
            isGranted = false,
            hasRequestedPermission = false,
            shouldShowRationale = false
        )

        assertEquals(CameraPermissionState.NotRequested, result)
    }

    @Test
    fun deniedPermissionWithRationaleReturnsDenied() {
        val result = CameraPermissionStateResolver.resolve(
            isGranted = false,
            hasRequestedPermission = true,
            shouldShowRationale = true
        )

        assertEquals(CameraPermissionState.Denied, result)
    }

    @Test
    fun deniedPermissionWithoutRationaleReturnsPermanentlyDenied() {
        val result = CameraPermissionStateResolver.resolve(
            isGranted = false,
            hasRequestedPermission = true,
            shouldShowRationale = false
        )

        assertEquals(CameraPermissionState.PermanentlyDenied, result)
    }
}