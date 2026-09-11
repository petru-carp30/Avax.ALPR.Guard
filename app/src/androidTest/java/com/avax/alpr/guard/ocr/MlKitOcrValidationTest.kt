package com.avax.alpr.guard.ocr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MlKitOcrValidationTest {

    companion object {
        private const val TAG = "AVAX_OCR_VALIDATION"
        private const val CSV_PATH = "ocr_validation/validation_50.csv"
        private const val IMAGE_ROOT = "ocr_validation/images"

        private const val UPSCALE_THRESHOLD_HEIGHT = 32
        private const val UPSCALE_TARGET_HEIGHT = 96
    }

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    data class ValidationSample(
        val filename: String,
        val plateText: String,
        val notes: String,
        val sourceGroup: String
    )

    data class OcrCandidate(
        val rawText: String,
        val normalizedText: String,
        val confidence: Float?,
        val score: Float
    )

    data class PreprocessedImage(
        val bitmap: Bitmap,
        val scaleFactor: Float
    )

    data class ValidationResult(
        val filename: String,
        val groundTruth: String,
        val fullRawText: String,
        val candidateRawText: String,
        val normalizedText: String,
        val confidence: Float?,
        val normalizedExactMatch: Boolean,
        val editDistance: Int,
        val candidateCount: Int,
        val originalWidth: Int,
        val originalHeight: Int,
        val processedWidth: Int,
        val processedHeight: Int,
        val scaleFactor: Float,
        val notes: String,
        val sourceGroup: String
    )

    @Test
    fun evaluateMlKitOcr() {
        logAvailableAssets()

        val samples = loadValidationSamples()
        assertEquals("Expected exactly 50 OCR validation samples", 50, samples.size)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val results = mutableListOf<ValidationResult>()

        try {
            samples.forEachIndexed { index, sample ->
                val originalBitmap = testContext.assets.open("$IMAGE_ROOT/${sample.filename}").use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: error("Failed to decode image: ${sample.filename}")

                val processed = preprocessImage(originalBitmap)

                try {
                    val inputImage = InputImage.fromBitmap(processed.bitmap, 0)
                    val recognizedText = Tasks.await(recognizer.process(inputImage), 30, TimeUnit.SECONDS)

                    val candidates = extractPlateCandidates(recognizedText)
                    val selectedCandidate = candidates.maxByOrNull { it.score }

                    val normalizedText = selectedCandidate?.normalizedText ?: ""
                    val normalizedExactMatch = normalizedText == sample.plateText
                    val editDistance = levenshteinDistance(sample.plateText, normalizedText)

                    results += ValidationResult(
                        filename = sample.filename,
                        groundTruth = sample.plateText,
                        fullRawText = recognizedText.text.trim(),
                        candidateRawText = selectedCandidate?.rawText ?: "",
                        normalizedText = normalizedText,
                        confidence = selectedCandidate?.confidence,
                        normalizedExactMatch = normalizedExactMatch,
                        editDistance = editDistance,
                        candidateCount = candidates.size,
                        originalWidth = originalBitmap.width,
                        originalHeight = originalBitmap.height,
                        processedWidth = processed.bitmap.width,
                        processedHeight = processed.bitmap.height,
                        scaleFactor = processed.scaleFactor,
                        notes = sample.notes,
                        sourceGroup = sample.sourceGroup
                    )

                    Log.i(
                        TAG,
                        "[${index + 1}/${samples.size}] FILE=${sample.filename} GT=${sample.plateText} " +
                                "SIZE=${originalBitmap.width}x${originalBitmap.height}->${processed.bitmap.width}x${processed.bitmap.height} " +
                                "SCALE=${"%.2f".format(Locale.US, processed.scaleFactor)} " +
                                "OCR=$normalizedText CONF=${selectedCandidate?.confidence ?: "N/A"} MATCH=$normalizedExactMatch"
                    )
                } catch (exception: Exception) {
                    Log.e(TAG, "OCR failed for ${sample.filename}", exception)

                    results += ValidationResult(
                        filename = sample.filename,
                        groundTruth = sample.plateText,
                        fullRawText = "",
                        candidateRawText = "",
                        normalizedText = "",
                        confidence = null,
                        normalizedExactMatch = false,
                        editDistance = sample.plateText.length,
                        candidateCount = 0,
                        originalWidth = originalBitmap.width,
                        originalHeight = originalBitmap.height,
                        processedWidth = processed.bitmap.width,
                        processedHeight = processed.bitmap.height,
                        scaleFactor = processed.scaleFactor,
                        notes = sample.notes,
                        sourceGroup = sample.sourceGroup
                    )
                } finally {
                    if (processed.bitmap !== originalBitmap) {
                        processed.bitmap.recycle()
                    }

                    originalBitmap.recycle()
                }
            }
        } finally {
            recognizer.close()
        }

        writeResults(results)
        writeSummary(results)
    }

    private fun preprocessImage(bitmap: Bitmap): PreprocessedImage {
        if (bitmap.height >= UPSCALE_THRESHOLD_HEIGHT) {
            return PreprocessedImage(bitmap, 1f)
        }

        val scaleFactor = UPSCALE_TARGET_HEIGHT.toFloat() / bitmap.height.toFloat()
        val targetWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(1)

        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            targetWidth,
            UPSCALE_TARGET_HEIGHT,
            true
        )

        return PreprocessedImage(
            bitmap = resizedBitmap,
            scaleFactor = scaleFactor
        )
    }

    private fun extractPlateCandidates(text: Text): List<OcrCandidate> {
        return text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val rawText = line.text.trim()
                val normalizedText = normalizeForEvaluation(rawText)

                if (normalizedText.isEmpty()) {
                    return@mapNotNull null
                }

                val confidence = calculateLineConfidence(line)

                OcrCandidate(
                    rawText = rawText,
                    normalizedText = normalizedText,
                    confidence = confidence,
                    score = scoreCandidate(rawText, normalizedText, confidence)
                )
            }
    }

    private fun scoreCandidate(rawText: String, normalizedText: String, confidence: Float?): Float {
        val length = normalizedText.length
        val letterCount = normalizedText.count { it in 'A'..'Z' }
        val digitCount = normalizedText.count { it in '0'..'9' }

        val visibleCharacters = rawText.count { !it.isWhitespace() }
        val alphanumericCharacters = rawText.count { it.isLetterOrDigit() }

        val alphanumericRatio = if (visibleCharacters > 0) {
            alphanumericCharacters.toFloat() / visibleCharacters
        } else {
            0f
        }

        var score = 0f

        if (length in 4..10) score += 40f else score -= 20f
        if (length in 5..9) score += 10f
        if (letterCount > 0 && digitCount > 0) score += 20f

        score += alphanumericRatio * 15f

        if (confidence != null) {
            score += confidence * 15f
        }

        if (length > 12) {
            score -= 30f
        }

        return score
    }

    private fun calculateLineConfidence(line: Text.Line): Float? {
        val symbolConfidences = line.elements
            .flatMap { it.symbols }
            .map { it.confidence }
            .filter { it >= 0f }

        if (symbolConfidences.isNotEmpty()) {
            return symbolConfidences.average().toFloat()
        }

        val elementConfidences = line.elements
            .map { it.confidence }
            .filter { it >= 0f }

        return elementConfidences.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }

    private fun loadValidationSamples(): List<ValidationSample> {
        return testContext.assets.open(CSV_PATH).bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val columns = parseCsvLine(line)

                ValidationSample(
                    filename = columns[0].trim(),
                    plateText = columns[1].trim(),
                    notes = columns[2].trim(),
                    sourceGroup = columns[5].trim()
                )
            }.toList()
        }
    }

    private fun logAvailableAssets() {
        val images = testContext.assets.list(IMAGE_ROOT)?.toList() ?: emptyList()

        Log.i(TAG, "OCR IMAGE COUNT: ${images.size}")

        require(images.size == 50) {
            "Expected 50 OCR images but found ${images.size}"
        }
    }

    private fun normalizeForEvaluation(text: String): String {
        return text.uppercase(Locale.ROOT).filter {
            it in 'A'..'Z' || it in '0'..'9'
        }
    }

    private fun levenshteinDistance(expected: String, actual: String): Int {
        if (expected.isEmpty()) return actual.length
        if (actual.isEmpty()) return expected.length

        var previous = IntArray(actual.length + 1) { it }
        var current = IntArray(actual.length + 1)

        for (expectedIndex in expected.indices) {
            current[0] = expectedIndex + 1

            for (actualIndex in actual.indices) {
                val insertion = current[actualIndex] + 1
                val deletion = previous[actualIndex + 1] + 1
                val substitution = previous[actualIndex] + if (expected[expectedIndex] == actual[actualIndex]) 0 else 1

                current[actualIndex + 1] = minOf(insertion, deletion, substitution)
            }

            val temporary = previous
            previous = current
            current = temporary
        }

        return previous[actual.length]
    }

    private fun writeResults(results: List<ValidationResult>) {
        val content = buildString {
            appendLine(
                "filename,ground_truth,full_raw_text,candidate_raw_text,normalized_text,confidence," +
                        "normalized_exact_match,edit_distance,candidate_count,original_width,original_height," +
                        "processed_width,processed_height,scale_factor,notes,source_group"
            )

            results.forEach { result ->
                appendLine(
                    listOf(
                        result.filename,
                        result.groundTruth,
                        result.fullRawText,
                        result.candidateRawText,
                        result.normalizedText,
                        result.confidence?.toString() ?: "",
                        result.normalizedExactMatch.toString(),
                        result.editDistance.toString(),
                        result.candidateCount.toString(),
                        result.originalWidth.toString(),
                        result.originalHeight.toString(),
                        result.processedWidth.toString(),
                        result.processedHeight.toString(),
                        result.scaleFactor.toString(),
                        result.notes,
                        result.sourceGroup
                    ).joinToString(",") { csvEscape(it) }
                )
            }
        }

        writeToDownloads(
            "ocr_validation_results_v4_selective_upscale.csv",
            "text/csv",
            content
        )
    }

    private fun writeSummary(results: List<ValidationResult>) {
        val normalizedMatches = results.count { it.normalizedExactMatch }
        val emptyResults = results.count { it.normalizedText.isEmpty() }

        val totalCharacters = results.sumOf { it.groundTruth.length }
        val totalEditDistance = results.sumOf { it.editDistance }

        val exactAccuracy = normalizedMatches.toDouble() / results.size
        val characterErrorRate = totalEditDistance.toDouble() / totalCharacters
        val characterAccuracy = (1.0 - characterErrorRate).coerceIn(0.0, 1.0)

        val romanianResults = results.filter { it.sourceGroup == "romanian_lp" }
        val elpdResults = results.filter { it.sourceGroup == "elpd" }

        val summary = buildString {
            appendLine("AVAX ALPR OCR MVP VALIDATION")
            appendLine()
            appendLine("engine=Google ML Kit Text Recognition v2 Latin")
            appendLine("artifact=com.google.mlkit:text-recognition:16.0.1")
            appendLine("candidate_selection=plate_like_ocr_line")
            appendLine("preprocessing=upscale_small_crops")
            appendLine("upscale_threshold_height=$UPSCALE_THRESHOLD_HEIGHT")
            appendLine("upscale_target_height=$UPSCALE_TARGET_HEIGHT")
            appendLine("samples=${results.size}")
            appendLine("normalized_exact_matches=$normalizedMatches")
            appendLine("normalized_exact_accuracy=${"%.4f".format(Locale.US, exactAccuracy)}")
            appendLine("total_ground_truth_characters=$totalCharacters")
            appendLine("total_edit_distance=$totalEditDistance")
            appendLine("character_error_rate=${"%.4f".format(Locale.US, characterErrorRate)}")
            appendLine("character_accuracy=${"%.4f".format(Locale.US, characterAccuracy)}")
            appendLine("empty_results=$emptyResults")
            appendLine()
            appendLine("romanian_samples=${romanianResults.size}")
            appendLine("romanian_normalized_exact_matches=${romanianResults.count { it.normalizedExactMatch }}")
            appendLine("elpd_samples=${elpdResults.size}")
            appendLine("elpd_normalized_exact_matches=${elpdResults.count { it.normalizedExactMatch }}")
            appendLine()
            appendLine("android_latency=NOT_YET_MEASURED")
        }

        writeToDownloads(
            "ocr_validation_summary_v4_selective_upscale.txt",
            "text/plain",
            summary
        )

        Log.i(TAG, "\n$summary")
    }

    private fun writeToDownloads(fileName: String, mimeType: String, content: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AVAX_ALPR_OCR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create $fileName")

        resolver.openOutputStream(uri)?.bufferedWriter()?.use {
            it.write(content)
        } ?: error("Failed to write $fileName")

        val completed = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }

        resolver.update(uri, completed, null, null)

        Log.i(TAG, "SAVED: Download/AVAX_ALPR_OCR/$fileName")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var index = 0

        while (index < line.length) {
            val character = line[index]

            when {
                character == '"' && insideQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }

                character == '"' -> insideQuotes = !insideQuotes

                character == ',' && !insideQuotes -> {
                    result += current.toString()
                    current.clear()
                }

                else -> current.append(character)
            }

            index++
        }

        result += current.toString()
        return result
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")

        return if (
            value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        ) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}