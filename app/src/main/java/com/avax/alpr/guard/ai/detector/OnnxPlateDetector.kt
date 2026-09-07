package com.avax.alpr.guard.ai.detector

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.avax.alpr.guard.camera.CameraFrame
import java.security.MessageDigest

class OnnxPlateDetector(context: Context) : PlateDetector {

    private val applicationContext = context.applicationContext
    private val preprocessor = PlateDetectorPreprocessor()
    private val postprocessor = PlateDetectorPostprocessor()

    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var loadFailure: Exception? = null
    private var modelLoadTimeMs: Double? = null
    private var closed = false

    @Synchronized
    override fun detect(frame: CameraFrame): PlateDetectorResult {
        check(!closed) { "Plate detector is closed." }

        val activeSession = ensureSession()
        val activeEnvironment = environment ?: error("ONNX Runtime environment is unavailable.")

        val totalStart = System.nanoTime()

        val preprocessingStart = System.nanoTime()
        val prepared = preprocessor.prepare(frame)
        val preprocessingTimeMs = elapsedMilliseconds(preprocessingStart)

        prepared.tensor.position(0)

        OnnxTensor.createTensor(activeEnvironment, prepared.tensor, DetectorModelConfig.INPUT_SHAPE).use { inputTensor ->
            val inferenceStart = System.nanoTime()

            val result = activeSession.run(
                mapOf(DetectorModelConfig.INPUT_NAME to inputTensor),
                setOf(DetectorModelConfig.OUTPUT_NAME)
            )

            val inferenceTimeMs = elapsedMilliseconds(inferenceStart)

            result.use {
                val outputValue = it.get(DetectorModelConfig.OUTPUT_NAME).orElse(null)
                    ?: error("Detector output '${DetectorModelConfig.OUTPUT_NAME}' is missing.")

                val outputTensor = outputValue as? OnnxTensor
                    ?: error("Detector output is not a tensor.")

                val outputBuffer = outputTensor.floatBuffer
                    ?: error("Detector output is not float32.")

                val postprocessingStart = System.nanoTime()

                val detections = postprocessor.decode(
                    output = outputBuffer,
                    geometry = prepared.geometry,
                    frameMetadata = frame.metadata
                )

                val postprocessingTimeMs = elapsedMilliseconds(postprocessingStart)

                return PlateDetectorResult(
                    detections = detections,
                    modelLoadTimeMs = modelLoadTimeMs,
                    preprocessingTimeMs = preprocessingTimeMs,
                    inferenceTimeMs = inferenceTimeMs,
                    postprocessingTimeMs = postprocessingTimeMs,
                    totalProcessingTimeMs = elapsedMilliseconds(totalStart)
                )
            }
        }
    }

    private fun ensureSession(): OrtSession {
        session?.let { return it }

        loadFailure?.let {
            throw IllegalStateException("Detector model previously failed to load.", it)
        }

        try {
            val loadStart = System.nanoTime()

            val modelBytes = applicationContext.assets.open(DetectorModelConfig.MODEL_FILE_NAME).use {
                it.readBytes()
            }

            require(modelBytes.size == DetectorModelConfig.MODEL_FILE_SIZE_BYTES) {
                "Detector model size mismatch. Expected ${DetectorModelConfig.MODEL_FILE_SIZE_BYTES}, received ${modelBytes.size}."
            }

            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(modelBytes)
                .joinToString("") { "%02X".format(it.toInt() and 0xFF) }

            require(sha256 == DetectorModelConfig.MODEL_SHA256) {
                "Detector model SHA256 mismatch."
            }

            val newEnvironment = OrtEnvironment.getEnvironment()
            val newSession = newEnvironment.createSession(modelBytes)

            try {
                validateSession(newSession)
            } catch (exception: Exception) {
                newSession.close()
                throw exception
            }

            environment = newEnvironment
            session = newSession
            modelLoadTimeMs = elapsedMilliseconds(loadStart)

            return newSession
        } catch (exception: Exception) {
            loadFailure = exception
            throw exception
        }
    }

    private fun validateSession(session: OrtSession) {
        require(session.inputNames == setOf(DetectorModelConfig.INPUT_NAME)) {
            "Unexpected detector input names: ${session.inputNames}"
        }

        require(session.outputNames == setOf(DetectorModelConfig.OUTPUT_NAME)) {
            "Unexpected detector output names: ${session.outputNames}"
        }

        val inputInfo = session.inputInfo[DetectorModelConfig.INPUT_NAME]?.info as? TensorInfo
            ?: error("Detector input tensor metadata is unavailable.")

        val outputInfo = session.outputInfo[DetectorModelConfig.OUTPUT_NAME]?.info as? TensorInfo
            ?: error("Detector output tensor metadata is unavailable.")

        require(inputInfo.type == OnnxJavaType.FLOAT) { "Detector input must be float32." }
        require(outputInfo.type == OnnxJavaType.FLOAT) { "Detector output must be float32." }

        require(inputInfo.shape.contentEquals(DetectorModelConfig.INPUT_SHAPE)) {
            "Unexpected detector input shape: ${inputInfo.shape.contentToString()}"
        }

        require(outputInfo.shape.contentEquals(DetectorModelConfig.OUTPUT_SHAPE)) {
            "Unexpected detector output shape: ${outputInfo.shape.contentToString()}"
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return

        closed = true
        session?.close()
        session = null
    }

    private fun elapsedMilliseconds(startNanos: Long): Double {
        return (System.nanoTime() - startNanos) / 1_000_000.0
    }
}