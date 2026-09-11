package com.avax.alpr.guard.ai.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MlKitPlateOcr : PlateOcr {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AvaxPlateOcr")
    }

    override fun recognize(crop: Bitmap, onComplete: (Result<PlateOcrResult?>) -> Unit) {
        val preprocessing = preprocess(crop)
        val inputBitmap = preprocessing.bitmap
        val ownsInputBitmap = inputBitmap !== crop
        val startedAtNanos = System.nanoTime()

        recognizer.process(InputImage.fromBitmap(inputBitmap, 0))
            .addOnCompleteListener(callbackExecutor) { task ->
                try {
                    if (!task.isSuccessful) {
                        val exception = task.exception ?: IllegalStateException("ML Kit OCR failed.")
                        onComplete(Result.failure(exception))
                        return@addOnCompleteListener
                    }

                    val recognizedText = task.result

                    val candidates = buildList {
                        recognizedText.textBlocks.forEach { block ->
                            val blockConfidenceValues = block.lines
                                .map { it.confidence }
                                .filter { it > 0f && it <= 1f }

                            val blockConfidence = blockConfidenceValues
                                .takeIf { it.isNotEmpty() }
                                ?.average()
                                ?.toFloat()

                            add(
                                OcrLineCandidate(
                                    text = block.lines.joinToString(" ") { it.text },
                                    confidence = blockConfidence
                                )
                            )

                            block.lines.forEach { line ->
                                add(
                                    OcrLineCandidate(
                                        text = line.text,
                                        confidence = line.confidence.takeIf { it > 0f && it <= 1f }
                                    )
                                )
                            }
                        }
                    }

                    val selected = OcrCandidateSelector.select(candidates)
                    val latencyMs = (System.nanoTime() - startedAtNanos) / 1_000_000.0

                    val result = selected?.let {
                        PlateOcrResult(
                            text = it.text.trim(),
                            confidence = it.confidence,
                            rawText = recognizedText.text,
                            latencyMs = latencyMs,
                            sourceWidth = crop.width,
                            sourceHeight = crop.height,
                            inputWidth = inputBitmap.width,
                            inputHeight = inputBitmap.height,
                            wasUpscaled = preprocessing.wasUpscaled
                        )
                    }

                    onComplete(Result.success(result))
                } catch (exception: Exception) {
                    onComplete(Result.failure(exception))
                } finally {
                    if (ownsInputBitmap && !inputBitmap.isRecycled) inputBitmap.recycle()
                }
            }
    }

    override fun close() {
        recognizer.close()
        callbackExecutor.shutdown()
    }

    private fun preprocess(crop: Bitmap): PreprocessingResult {
        if (crop.height >= MINIMUM_ORIGINAL_HEIGHT_PX) {
            return PreprocessingResult(
                bitmap = crop,
                wasUpscaled = false
            )
        }

        val scale = UPSCALED_HEIGHT_PX.toFloat() / crop.height.toFloat()
        val targetWidth = (crop.width * scale).roundToInt().coerceAtLeast(1)

        return PreprocessingResult(
            bitmap = Bitmap.createScaledBitmap(crop, targetWidth, UPSCALED_HEIGHT_PX, true),
            wasUpscaled = true
        )
    }

    private data class PreprocessingResult(
        val bitmap: Bitmap,
        val wasUpscaled: Boolean
    )

    private companion object {
        const val MINIMUM_ORIGINAL_HEIGHT_PX = 64
        const val UPSCALED_HEIGHT_PX = 128
    }
}