package com.avax.alpr.guard.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avax.alpr.guard.camera.CameraPermissionState
import com.avax.alpr.guard.camera.CameraPermissionStateResolver
import com.avax.alpr.guard.camera.CameraRuntimeState
import com.avax.alpr.guard.camera.CameraSession
import com.avax.alpr.guard.camera.DevelopmentFrameProcessor

@Composable
fun CameraPreviewCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    fun resolvePermissionState(): CameraPermissionState {
        val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true

        return CameraPermissionStateResolver.resolve(
            isGranted = isGranted,
            hasRequestedPermission = hasRequestedPermission,
            shouldShowRationale = shouldShowRationale
        )
    }

    var permissionState by remember { mutableStateOf(resolvePermissionState()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRequestedPermission = true
        permissionState = resolvePermissionState()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionState = resolvePermissionState()
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Camera",
                style = MaterialTheme.typography.titleLarge
            )

            when (permissionState) {
                CameraPermissionState.NotRequested -> {
                    Text("Camera permission is required for live plate scanning. Manual verification remains available.")

                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable camera")
                    }
                }

                CameraPermissionState.Granted -> {
                    CameraPreviewContent()
                }

                CameraPermissionState.Denied -> {
                    Text("Camera permission was denied. You can retry or continue using manual verification.")

                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry camera permission")
                    }
                }

                CameraPermissionState.PermanentlyDenied -> {
                    Text("Camera permission must be enabled from Android settings. Manual verification remains available.")

                    Button(
                        onClick = { context.openApplicationSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val frameProcessor = remember { DevelopmentFrameProcessor() }
    val diagnostics by frameProcessor.diagnostics.collectAsStateWithLifecycle()

    var cameraState by remember { mutableStateOf<CameraRuntimeState>(CameraRuntimeState.Starting) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val cameraSession = remember {
        CameraSession(context.applicationContext)
    }

    DisposableEffect(cameraSession, previewView, lifecycleOwner) {
        cameraSession.bind(
            previewView = previewView,
            lifecycleOwner = lifecycleOwner,
            frameProcessor = frameProcessor,
            onStateChanged = { cameraState = it }
        )

        onDispose {
            cameraSession.close()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            if (cameraState != CameraRuntimeState.Active) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    tonalElevation = 6.dp
                ) {
                    Text(
                        text = cameraState.displayMessage(),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        Text(
            text = "Camera: ${cameraState.displayName()}",
            style = MaterialTheme.typography.bodySmall
        )

        diagnostics?.let { diagnostic ->
            Text(
                text = "Frames analyzed: ${diagnostic.frameCount} | Resolution: ${diagnostic.metadata.width}x${diagnostic.metadata.height} | Rotation: ${diagnostic.metadata.rotationDegrees}°",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun CameraRuntimeState.displayName(): String {
    return when (this) {
        CameraRuntimeState.Starting -> "STARTING"
        CameraRuntimeState.Active -> "ACTIVE"
        CameraRuntimeState.Unavailable -> "UNAVAILABLE"
        CameraRuntimeState.Error -> "ERROR"
    }
}

private fun CameraRuntimeState.displayMessage(): String {
    return when (this) {
        CameraRuntimeState.Starting -> "Starting camera..."
        CameraRuntimeState.Active -> ""
        CameraRuntimeState.Unavailable -> "Rear camera is unavailable. Manual verification remains available."
        CameraRuntimeState.Error -> "Camera could not be started. Manual verification remains available."
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun Context.openApplicationSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }

    startActivity(intent)
}