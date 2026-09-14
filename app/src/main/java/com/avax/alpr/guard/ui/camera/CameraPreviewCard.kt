package com.avax.alpr.guard.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ZoomState
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avax.alpr.guard.ai.detector.OnnxPlateDetector
import com.avax.alpr.guard.ai.ocr.AutomaticPlateOcrProcessor
import com.avax.alpr.guard.ai.ocr.AutomaticPlateRecognition
import com.avax.alpr.guard.ai.ocr.MlKitPlateOcr
import com.avax.alpr.guard.camera.CameraFocusAssist
import com.avax.alpr.guard.camera.CameraPermissionState
import com.avax.alpr.guard.camera.CameraPermissionStateResolver
import com.avax.alpr.guard.camera.CameraRuntimeState
import com.avax.alpr.guard.camera.CameraSession
import com.avax.alpr.guard.camera.CameraZoomMath
import com.avax.alpr.guard.camera.DetectorRuntimeState
import com.avax.alpr.guard.camera.PlateDetectorFrameProcessor
import com.avax.alpr.guard.domain.AutomaticScanRearmGate
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun CameraPreviewCard(
    onAutomaticRecognition: (AutomaticPlateRecognition) -> Unit,
    onAutomaticOcrFailure: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    fun resolvePermissionState(): CameraPermissionState {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.CAMERA
            )
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
    ) {
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
                    CameraPreviewContent(
                        onAutomaticRecognition = onAutomaticRecognition,
                        onAutomaticOcrFailure = onAutomaticOcrFailure
                    )
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
private fun CameraPreviewContent(
    onAutomaticRecognition: (AutomaticPlateRecognition) -> Unit,
    onAutomaticOcrFailure: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnAutomaticRecognition by rememberUpdatedState(onAutomaticRecognition)
    val currentOnAutomaticOcrFailure by rememberUpdatedState(onAutomaticOcrFailure)

    val detector = remember { OnnxPlateDetector(context.applicationContext) }
    val plateOcr = remember { MlKitPlateOcr() }

    val automaticScanRearmGate = remember {
        AutomaticScanRearmGate(
            rearmDelayMs = 1_500L,
            clockMs = { SystemClock.elapsedRealtime() }
        )
    }

    val automaticOcrProcessor = remember(plateOcr, automaticScanRearmGate) {
        AutomaticPlateOcrProcessor(
            plateOcr = plateOcr,
            onRecognition = { recognition ->
                if (automaticScanRearmGate.tryAcquireAutomaticVerification()) {
                    currentOnAutomaticRecognition(recognition)
                }
            },
            onFailure = { message ->
                currentOnAutomaticOcrFailure(message)
            }
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        automaticScanRearmGate.reset()
        automaticOcrProcessor.resetConfirmation()
    }

    val frameProcessor = remember(
        detector,
        automaticOcrProcessor,
        automaticScanRearmGate
    ) {
        PlateDetectorFrameProcessor(
            detector = detector,
            minInferenceIntervalMs = 0L,
            shouldProcessFrame = { !automaticOcrProcessor.isProcessing() },
            onDetections = { frame, detections ->
                automaticScanRearmGate.onDetectorResult(
                    hasPlateDetection = detections.isNotEmpty()
                )

                if (!automaticScanRearmGate.isLocked()) {
                    automaticOcrProcessor.process(frame, detections)
                }
            }
        )
    }

    val diagnostics by frameProcessor.diagnostics.collectAsStateWithLifecycle()
    val ocrDiagnostics by automaticOcrProcessor.diagnostics.collectAsStateWithLifecycle()

    var cameraState by remember {
        mutableStateOf<CameraRuntimeState>(CameraRuntimeState.Starting)
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var zoomFocusRequestVersion by remember { mutableIntStateOf(0) }
    var tapFocusVersion by remember { mutableIntStateOf(0) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            isClickable = true
        }
    }

    fun requestZoom(requestedZoomRatio: Float) {
        val camera = boundCamera ?: return
        val state = camera.cameraInfo.zoomState.value ?: return

        val safeZoom = CameraZoomMath.clampZoomRatio(
            requested = requestedZoomRatio,
            minZoomRatio = state.minZoomRatio,
            maxZoomRatio = state.maxZoomRatio
        ) ?: return

        camera.cameraControl.setZoomRatio(safeZoom)
        zoomFocusRequestVersion++
    }

    val currentCamera = rememberUpdatedState(boundCamera)
    val currentCameraState = rememberUpdatedState(cameraState)
    val currentTapFocusVersion = rememberUpdatedState(tapFocusVersion)

    LaunchedEffect(zoomFocusRequestVersion) {
        if (zoomFocusRequestVersion == 0) return@LaunchedEffect

        val tapVersionAtSchedule = currentTapFocusVersion.value
        delay(350L)

        if (tapVersionAtSchedule != currentTapFocusVersion.value) {
            return@LaunchedEffect
        }

        if (currentCameraState.value != CameraRuntimeState.Active) {
            return@LaunchedEffect
        }

        val camera = currentCamera.value ?: return@LaunchedEffect

        CameraFocusAssist.requestCenterFocus(
            camera = camera,
            previewView = previewView
        )
    }

    val scaleGestureDetector = remember(context) {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val camera = currentCamera.value ?: return false
                    val state = camera.cameraInfo.zoomState.value ?: return false

                    val requestedZoom = CameraZoomMath.scaleZoomRatio(
                        currentZoomRatio = state.zoomRatio,
                        scaleFactor = detector.scaleFactor,
                        minZoomRatio = state.minZoomRatio,
                        maxZoomRatio = state.maxZoomRatio
                    ) ?: return false

                    camera.cameraControl.setZoomRatio(requestedZoom)
                    zoomFocusRequestVersion++
                    return true
                }
            }
        )
    }

    val tapGestureDetector = remember(context, previewView) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    previewView.performClick()
                    tapFocusVersion++

                    val camera = currentCamera.value ?: return true

                    CameraFocusAssist.requestFocus(
                        camera = camera,
                        previewView = previewView,
                        x = event.x,
                        y = event.y
                    )

                    return true
                }
            }
        )
    }

    val cameraSession = remember {
        CameraSession(context.applicationContext)
    }

    DisposableEffect(
        cameraSession,
        previewView,
        lifecycleOwner,
        detector
    ) {
        cameraSession.bind(
            previewView = previewView,
            lifecycleOwner = lifecycleOwner,
            frameProcessor = frameProcessor,
            onStateChanged = { cameraState = it },
            onCameraBound = { boundCamera = it }
        )

        onDispose {
            boundCamera = null
            cameraSession.close()
            automaticOcrProcessor.close()
            detector.close()
        }
    }

    DisposableEffect(boundCamera, lifecycleOwner) {
        val camera = boundCamera

        if (camera == null) {
            onDispose { }
        } else {
            val observer = Observer<ZoomState> { state ->
                if (state != null) {
                    zoomRatio = state.zoomRatio
                    minZoomRatio = state.minZoomRatio
                    maxZoomRatio = state.maxZoomRatio
                }
            }

            camera.cameraInfo.zoomState.observe(
                lifecycleOwner,
                observer
            )

            onDispose {
                camera.cameraInfo.zoomState.removeObserver(observer)
            }
        }
    }

    DisposableEffect(
        previewView,
        scaleGestureDetector,
        tapGestureDetector
    ) {
        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            tapGestureDetector.onTouchEvent(event)
            true
        }

        onDispose {
            previewView.setOnTouchListener(null)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            DetectorOverlay(
                detections = diagnostics.lastDetections,
                frameMetadata = diagnostics.frameMetadata,
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

        if (maxZoomRatio > minZoomRatio) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { requestZoom(1f) }
                ) {
                    Text("1×")
                }

                androidx.compose.material3.Slider(
                    value = zoomRatio.coerceIn(
                        minZoomRatio,
                        maxZoomRatio
                    ),
                    onValueChange = { requestZoom(it) },
                    valueRange = minZoomRatio..maxZoomRatio,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = String.format(
                        Locale.US,
                        "%.1f×",
                        zoomRatio
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            text = "Camera: ${cameraState.displayName()}",
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = "Detector: ${diagnostics.runtimeState.displayName()}",
            style = MaterialTheme.typography.bodySmall
        )

        diagnostics.modelLoadTimeMs?.let {
            Text(
                text = "Model load: ${formatMilliseconds(it)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        diagnostics.inferenceTimeMs?.let { inference ->
            Text(
                text = "Detections: ${diagnostics.detectionCount} | Inference: ${formatMilliseconds(inference)} ms | Total: ${formatMilliseconds(diagnostics.totalProcessingTimeMs ?: 0.0)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        diagnostics.preprocessingTimeMs?.let { preprocessing ->
            Text(
                text = "Preprocess: ${formatMilliseconds(preprocessing)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        diagnostics.yuvConversionTimeMs?.let { yuvConversion ->
            Text(
                text = "YUV -> BGR: ${formatMilliseconds(yuvConversion)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        diagnostics.rotationTimeMs?.let { rotation ->
            Text(
                text = "Rotation: ${formatMilliseconds(rotation)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        diagnostics.resizeTensorTimeMs?.let { resizeTensor ->
            Text(
                text = "Resize + tensor: ${formatMilliseconds(resizeTensor)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        diagnostics.postprocessingTimeMs?.let { postprocessing ->
            Text(
                text = "Postprocess: ${formatMilliseconds(postprocessing)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        diagnostics.cadenceFps?.let {
            Text(
                text = "Detector cadence: ${String.format(Locale.US, "%.1f", it)} fps",
                style = MaterialTheme.typography.bodySmall
            )
        }

        ocrDiagnostics.recognizedText?.let { text ->
            Text(
                text = "OCR plate: $text",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        ocrDiagnostics.ocrLatencyMs?.let { latency ->
            Text(
                text = "OCR latency: ${formatMilliseconds(latency)} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }

        ocrDiagnostics.detectorConfidence?.let { confidence ->
            Text(
                text = "Detector confidence: ${String.format(Locale.US, "%.3f", confidence)}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        ocrDiagnostics.ocrConfidence?.let { confidence ->
            Text(
                text = "OCR confidence: ${String.format(Locale.US, "%.3f", confidence)}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (
            ocrDiagnostics.detectorBoxWidth != null &&
            ocrDiagnostics.detectorBoxHeight != null
        ) {
            Text(
                text = "Detector bbox: ${ocrDiagnostics.detectorBoxWidth} x ${ocrDiagnostics.detectorBoxHeight} px",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (
            ocrDiagnostics.cropWidth != null &&
            ocrDiagnostics.cropHeight != null
        ) {
            Text(
                text = "Crop: ${ocrDiagnostics.cropWidth} x ${ocrDiagnostics.cropHeight} px | Padding: 8%",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (
            ocrDiagnostics.ocrInputWidth != null &&
            ocrDiagnostics.ocrInputHeight != null
        ) {
            Text(
                text = "OCR input: ${ocrDiagnostics.ocrInputWidth} x ${ocrDiagnostics.ocrInputHeight} px | Upscaled: ${if (ocrDiagnostics.wasUpscaled == true) "YES" else "NO"}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        ocrDiagnostics.message?.let { message ->
            Text(
                text = "OCR: $message",
                style = MaterialTheme.typography.bodySmall
            )
        }

        val detectorState = diagnostics.runtimeState

        if (detectorState is DetectorRuntimeState.Unavailable) {
            Text(
                text = "Detector unavailable: ${detectorState.reason}. Manual verification remains available.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun DetectorRuntimeState.displayName(): String {
    return when (this) {
        DetectorRuntimeState.WaitingForFirstFrame -> "LOADING"
        DetectorRuntimeState.Ready -> "READY"
        is DetectorRuntimeState.Unavailable -> "UNAVAILABLE"
    }
}

private fun formatMilliseconds(value: Double): String {
    return String.format(
        Locale.US,
        "%.1f",
        value
    )
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
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    ).apply {
        data = Uri.fromParts(
            "package",
            packageName,
            null
        )
    }

    startActivity(intent)
}