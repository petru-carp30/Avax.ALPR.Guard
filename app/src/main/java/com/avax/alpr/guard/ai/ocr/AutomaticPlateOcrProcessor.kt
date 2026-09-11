package com.avax.alpr.guard.ai.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import com.avax.alpr.guard.ai.detector.PlateDetection
import com.avax.alpr.guard.camera.CameraFrame
import com.avax.alpr.guard.domain.PlateNormalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutomaticPlateOcrProcessor internal constructor(
    private val plateOcr: PlateOcr,
    private val cropper: PlateCropper = PlateCropper(),
    private val confirmationGate: AutomaticOcrConfirmationGate = AutomaticOcrConfirmationGate(
        clockMs = { SystemClock.elapsedRealtime() }
    ),
    private val onRecognition: (AutomaticPlateRecognition) -> Unit = {},
    private val onFailure: (String) -> Unit = {}
) : AutoCloseable {

    private val processing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private val _diagnostics = MutableStateFlow(AutomaticPlateOcrDiagnostics())
    val diagnostics: StateFlow<AutomaticPlateOcrDiagnostics> = _diagnostics.asStateFlow()

    fun process(frame: CameraFrame, detections: List<PlateDetection>) {
        if (closed.get()) return

        confirmationGate.onDetectorResult(
            hasPlateDetection = detections.isNotEmpty()
        )

        if (processing.get() || detections.isEmpty()) return

        val selectedDetection = cropper.selectBestDetection(frame.metadata, detections) ?: return

        if (!processing.compareAndSet(false, true)) return

        var crop: Bitmap? = null

        try {
            crop = cropper.crop(frame, selectedDetection)

            if (crop == null) {
                processing.set(false)

                _diagnostics.value = _diagnostics.value.copy(
                    isProcessing = false,
                    message = "Plate crop unavailable."
                )

                return
            }

            val detectorBoxWidth = (selectedDetection.right - selectedDetection.left).roundToInt()
            val detectorBoxHeight = (selectedDetection.bottom - selectedDetection.top).roundToInt()
            val cropWidth = crop.width
            val cropHeight = crop.height

            if (PlateCropQualityGate.evaluate(cropHeight) == PlateCropQuality.LowQuality) {
                _diagnostics.value = AutomaticPlateOcrDiagnostics(
                    isProcessing = false,
                    detectorConfidence = selectedDetection.confidence,
                    detectorBoxWidth = detectorBoxWidth,
                    detectorBoxHeight = detectorBoxHeight,
                    cropWidth = cropWidth,
                    cropHeight = cropHeight,
                    message = "Plate too far - move closer or zoom"
                )

                if (!crop.isRecycled) crop.recycle()
                processing.set(false)
                return
            }

            _diagnostics.value = AutomaticPlateOcrDiagnostics(
                isProcessing = true,
                detectorConfidence = selectedDetection.confidence,
                detectorBoxWidth = detectorBoxWidth,
                detectorBoxHeight = detectorBoxHeight,
                cropWidth = cropWidth,
                cropHeight = cropHeight
            )

            val ownedCrop = crop

            plateOcr.recognize(ownedCrop) { result ->
                try {
                    if (closed.get()) return@recognize

                    result.fold(
                        onSuccess = { ocrResult ->
                            if (ocrResult == null) {
                                val message = "No usable OCR text."

                                _diagnostics.value = AutomaticPlateOcrDiagnostics(
                                    isProcessing = false,
                                    detectorConfidence = selectedDetection.confidence,
                                    detectorBoxWidth = detectorBoxWidth,
                                    detectorBoxHeight = detectorBoxHeight,
                                    cropWidth = cropWidth,
                                    cropHeight = cropHeight,
                                    message = message
                                )

                                onFailure(message)
                                return@fold
                            }

                            val normalizedPlate = PlateNormalizer.normalize(ocrResult.text)

                            if (normalizedPlate.isBlank()) {
                                val message = "OCR result is not usable for confirmation."

                                _diagnostics.value = AutomaticPlateOcrDiagnostics(
                                    isProcessing = false,
                                    detectorConfidence = selectedDetection.confidence,
                                    recognizedText = ocrResult.text,
                                    ocrConfidence = ocrResult.confidence,
                                    ocrLatencyMs = ocrResult.latencyMs,
                                    detectorBoxWidth = detectorBoxWidth,
                                    detectorBoxHeight = detectorBoxHeight,
                                    cropWidth = cropWidth,
                                    cropHeight = cropHeight,
                                    ocrInputWidth = ocrResult.inputWidth,
                                    ocrInputHeight = ocrResult.inputHeight,
                                    wasUpscaled = ocrResult.wasUpscaled,
                                    message = message
                                )

                                onFailure(message)
                                return@fold
                            }

                            when (val outcome = confirmationGate.submit(normalizedPlate)) {
                                is AutomaticOcrConfirmationGate.Outcome.Collecting -> {
                                    _diagnostics.value = AutomaticPlateOcrDiagnostics(
                                        isProcessing = false,
                                        detectorConfidence = selectedDetection.confidence,
                                        recognizedText = ocrResult.text,
                                        ocrConfidence = ocrResult.confidence,
                                        ocrLatencyMs = ocrResult.latencyMs,
                                        detectorBoxWidth = detectorBoxWidth,
                                        detectorBoxHeight = detectorBoxHeight,
                                        cropWidth = cropWidth,
                                        cropHeight = cropHeight,
                                        ocrInputWidth = ocrResult.inputWidth,
                                        ocrInputHeight = ocrResult.inputHeight,
                                        wasUpscaled = ocrResult.wasUpscaled,
                                        message = "OCR confirming: ${outcome.candidates.joinToString(" | ")}"
                                    )
                                }

                                is AutomaticOcrConfirmationGate.Outcome.Confirmed -> {
                                    val recognition = AutomaticPlateRecognition(
                                        ocrResult = ocrResult,
                                        detectorConfidence = selectedDetection.confidence
                                    )

                                    _diagnostics.value = AutomaticPlateOcrDiagnostics(
                                        isProcessing = false,
                                        detectorConfidence = selectedDetection.confidence,
                                        recognizedText = ocrResult.text,
                                        ocrConfidence = ocrResult.confidence,
                                        ocrLatencyMs = ocrResult.latencyMs,
                                        detectorBoxWidth = detectorBoxWidth,
                                        detectorBoxHeight = detectorBoxHeight,
                                        cropWidth = cropWidth,
                                        cropHeight = cropHeight,
                                        ocrInputWidth = ocrResult.inputWidth,
                                        ocrInputHeight = ocrResult.inputHeight,
                                        wasUpscaled = ocrResult.wasUpscaled,
                                        message = "OCR confirmed: ${outcome.candidates.joinToString(" | ")}"
                                    )

                                    onRecognition(recognition)
                                }

                                is AutomaticOcrConfirmationGate.Outcome.Uncertain -> {
                                    val reason = when (outcome.reason) {
                                        AutomaticOcrConfirmationGate.Reason.NoAgreement -> "No 2-of-3 OCR agreement."
                                        AutomaticOcrConfirmationGate.Reason.WindowExpired -> "OCR confirmation window expired."
                                    }

                                    val candidateText = outcome.candidates
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(" | ")
                                        ?: "none"

                                    val message = "$reason Candidates: $candidateText Retrying."

                                    _diagnostics.value = AutomaticPlateOcrDiagnostics(
                                        isProcessing = false,
                                        detectorConfidence = selectedDetection.confidence,
                                        recognizedText = ocrResult.text,
                                        ocrConfidence = ocrResult.confidence,
                                        ocrLatencyMs = ocrResult.latencyMs,
                                        detectorBoxWidth = detectorBoxWidth,
                                        detectorBoxHeight = detectorBoxHeight,
                                        cropWidth = cropWidth,
                                        cropHeight = cropHeight,
                                        ocrInputWidth = ocrResult.inputWidth,
                                        ocrInputHeight = ocrResult.inputHeight,
                                        wasUpscaled = ocrResult.wasUpscaled,
                                        message = message
                                    )

                                    onFailure(message)
                                }
                            }
                        },
                        onFailure = { exception ->
                            val message = exception.message ?: "OCR failed."

                            _diagnostics.value = AutomaticPlateOcrDiagnostics(
                                isProcessing = false,
                                detectorConfidence = selectedDetection.confidence,
                                detectorBoxWidth = detectorBoxWidth,
                                detectorBoxHeight = detectorBoxHeight,
                                cropWidth = cropWidth,
                                cropHeight = cropHeight,
                                message = message
                            )

                            onFailure(message)
                        }
                    )
                } finally {
                    if (!ownedCrop.isRecycled) ownedCrop.recycle()
                    processing.set(false)
                }
            }
        } catch (exception: Exception) {
            crop?.takeIf { !it.isRecycled }?.recycle()
            processing.set(false)

            val message = exception.message ?: "OCR pipeline failed."

            _diagnostics.value = AutomaticPlateOcrDiagnostics(
                isProcessing = false,
                detectorConfidence = selectedDetection.confidence,
                message = message
            )

            onFailure(message)
        }
    }

    fun resetConfirmation() {
        confirmationGate.reset()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        confirmationGate.reset()
        plateOcr.close()
    }
}