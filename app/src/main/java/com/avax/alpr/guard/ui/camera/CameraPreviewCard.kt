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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.exp
import kotlin.math.ln
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun CameraPreviewCard(
    onAutomaticRecognition: (AutomaticPlateRecognition) -> Unit,
    onAutomaticOcrFailure: (String) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showDiagnostics: Boolean = true,
    previewHeight: Dp = 360.dp,
    contentPadding: Dp = 16.dp,
    fullscreenPreview: Boolean = false,
    verticalZoomControls: Boolean = false,
    showContainer: Boolean = true
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

    val content: @Composable () -> Unit = {
        Column(
            modifier = if (fullscreenPreview) {
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            } else {
                Modifier.padding(contentPadding)
            },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showHeader) {
                Text(
                    text = "Camera",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            when (permissionState) {
                CameraPermissionState.NotRequested -> {
                    CameraPermissionMessage(
                        message = "Camera permission is required for live plate scanning. Manual verification remains available.",
                        buttonText = "Enable camera",
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        fullscreen = fullscreenPreview
                    )
                }

                CameraPermissionState.Granted -> {
                    CameraPreviewContent(
                        onAutomaticRecognition = onAutomaticRecognition,
                        onAutomaticOcrFailure = onAutomaticOcrFailure,
                        showDiagnostics = showDiagnostics,
                        previewHeight = previewHeight,
                        fullscreenPreview = fullscreenPreview,
                        verticalZoomControls = verticalZoomControls
                    )
                }

                CameraPermissionState.Denied -> {
                    CameraPermissionMessage(
                        message = "Camera permission was denied. You can retry or continue using manual verification.",
                        buttonText = "Retry camera permission",
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        fullscreen = fullscreenPreview
                    )
                }

                CameraPermissionState.PermanentlyDenied -> {
                    CameraPermissionMessage(
                        message = "Camera permission must be enabled from Android settings. Manual verification remains available.",
                        buttonText = "Open settings",
                        onClick = {
                            context.openApplicationSettings()
                        },
                        fullscreen = fullscreenPreview
                    )
                }
            }
        }
    }

    if (showContainer) {
        Card(modifier = modifier.fillMaxWidth()) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
private fun CameraPermissionMessage(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
    fullscreen: Boolean
) {
    Column(
        modifier = if (fullscreen) {
            Modifier
                .fillMaxSize()
                .padding(24.dp)
        } else {
            Modifier.fillMaxWidth()
        },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(message)

                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    onAutomaticRecognition: (AutomaticPlateRecognition) -> Unit,
    onAutomaticOcrFailure: (String) -> Unit,
    showDiagnostics: Boolean,
    previewHeight: Dp,
    fullscreenPreview: Boolean,
    verticalZoomControls: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnAutomaticRecognition by rememberUpdatedState(onAutomaticRecognition)
    val currentOnAutomaticOcrFailure by rememberUpdatedState(onAutomaticOcrFailure)

    val detector = remember {
        OnnxPlateDetector(context.applicationContext)
    }

    val plateOcr = remember {
        MlKitPlateOcr()
    }

    val automaticScanRearmGate = remember {
        AutomaticScanRearmGate(
            rearmDelayMs = 1_500L,
            clockMs = { SystemClock.elapsedRealtime() }
        )
    }

    val automaticOcrProcessor = remember(
        plateOcr,
        automaticScanRearmGate
    ) {
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
            shouldProcessFrame = {
                !automaticOcrProcessor.isProcessing()
            },
            onDetections = { frame, detections ->
                automaticScanRearmGate.onDetectorResult(
                    hasPlateDetection = detections.isNotEmpty()
                )

                if (!automaticScanRearmGate.isLocked()) {
                    automaticOcrProcessor.process(
                        frame,
                        detections
                    )
                }
            }
        )
    }

    val diagnostics by frameProcessor.diagnostics.collectAsStateWithLifecycle()
    val ocrDiagnostics by automaticOcrProcessor.diagnostics.collectAsStateWithLifecycle()

    var cameraState by remember {
        mutableStateOf<CameraRuntimeState>(
            CameraRuntimeState.Starting
        )
    }

    var boundCamera by remember {
        mutableStateOf<Camera?>(null)
    }

    var zoomRatio by remember {
        mutableFloatStateOf(1f)
    }

    var minZoomRatio by remember {
        mutableFloatStateOf(1f)
    }

    var maxZoomRatio by remember {
        mutableFloatStateOf(1f)
    }

    var zoomFocusRequestVersion by remember {
        mutableIntStateOf(0)
    }

    var tapFocusVersion by remember {
        mutableIntStateOf(0)
    }

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
        if (zoomFocusRequestVersion == 0) {
            return@LaunchedEffect
        }

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

    val tapGestureDetector = remember(
        context,
        previewView
    ) {
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
            onStateChanged = {
                cameraState = it
            },
            onCameraBound = {
                boundCamera = it
            }
        )

        onDispose {
            boundCamera = null
            cameraSession.close()
            automaticOcrProcessor.close()
            detector.close()
        }
    }

    DisposableEffect(
        boundCamera,
        lifecycleOwner
    ) {
        val camera = boundCamera

        if (camera == null) {
            onDispose { }
        } else {
            val observer = Observer<ZoomState> { state ->
                zoomRatio = state.zoomRatio
                minZoomRatio = state.minZoomRatio
                maxZoomRatio = state.maxZoomRatio
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
        modifier = if (fullscreenPreview) {
            Modifier.fillMaxSize()
        } else {
            Modifier
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = if (fullscreenPreview) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
            }
        ) {
            AndroidView(
                factory = {
                    previewView
                },
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
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = cameraState.displayMessage(),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (
                verticalZoomControls &&
                maxZoomRatio > minZoomRatio
            ) {
                VerticalZoomControl(
                    zoomRatio = zoomRatio,
                    minZoomRatio = minZoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    onZoomChanged = {
                        requestZoom(it)
                    },
                    onReset = {
                        requestZoom(1f)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .offset(y = (-45).dp)
                )
            }
        }

        if (
            !fullscreenPreview &&
            !verticalZoomControls &&
            maxZoomRatio > minZoomRatio
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        requestZoom(1f)
                    }
                ) {
                    Text("1×")
                }

                Slider(
                    value = zoomRatio.coerceIn(
                        minZoomRatio,
                        maxZoomRatio
                    ),
                    onValueChange = {
                        requestZoom(it)
                    },
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

        if (showDiagnostics) {
            Text(
                text = "Camera: ${cameraState.displayName()}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Detector: ${diagnostics.runtimeState.displayName()}",
                style = MaterialTheme.typography.bodySmall
            )

            diagnostics.modelLoadTimeMs?.let { modelLoadTime ->
                Text(
                    text = "Model load: ${formatMilliseconds(modelLoadTime)} ms",
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

            diagnostics.cadenceFps?.let { cadence ->
                Text(
                    text = "Detector cadence: ${String.format(Locale.US, "%.1f", cadence)} fps",
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
}

@Composable
private fun VerticalZoomControl(
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomChanged: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveMin = maxOf(1f, minZoomRatio)
    val effectiveMax = maxOf(effectiveMin, maxZoomRatio)

    fun zoomToFraction(zoom: Float): Float {
        if (effectiveMax <= effectiveMin) return 0f

        val safeZoom = zoom.coerceIn(effectiveMin, effectiveMax)
        return (
                ln(safeZoom / effectiveMin) /
                        ln(effectiveMax / effectiveMin)
                ).coerceIn(0f, 1f)
    }

    fun fractionToZoom(fraction: Float): Float {
        if (effectiveMax <= effectiveMin) return effectiveMin

        val safeFraction = fraction.coerceIn(0f, 1f)
        return (
                effectiveMin *
                        exp(
                            ln(effectiveMax / effectiveMin) *
                                    safeFraction
                        )
                ).coerceIn(effectiveMin, effectiveMax)
    }

    Surface(
        modifier = modifier.width(64.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 12.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "%.1f×",
                    zoomRatio
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )

            BoxWithConstraints(
                modifier = Modifier
                    .width(48.dp)
                    .height(360.dp)
                    .pointerInput(
                        effectiveMin,
                        effectiveMax
                    ) {
                        awaitEachGesture {
                            val down = awaitFirstDown()

                            fun updateZoom(y: Float) {
                                val fraction =
                                    1f - (y / size.height.toFloat())

                                onZoomChanged(
                                    fractionToZoom(fraction)
                                )
                            }

                            updateZoom(down.position.y)

                            var pressed = true

                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break

                                updateZoom(change.position.y)
                                change.consume()

                                pressed = change.pressed
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxSize()
                        .align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxSize()
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.outline.copy(
                                    alpha = 0.65f
                                )
                            )
                    )
                }

                val fraction = zoomToFraction(zoomRatio)
                val thumbTravel = maxHeight - 26.dp

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(
                            y = -(thumbTravel * fraction)
                        )
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary
                        )
                )
            }

            TextButton(
                onClick = onReset
            ) {
                Text("1×")
            }
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
        CameraRuntimeState.Unavailable ->
            "Rear camera is unavailable. Manual verification remains available."

        CameraRuntimeState.Error ->
            "Camera could not be started. Manual verification remains available."
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