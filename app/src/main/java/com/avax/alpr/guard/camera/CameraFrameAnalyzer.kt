package com.avax.alpr.guard.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class CameraFrameAnalyzer(
    private val frameProcessor: FrameProcessor,
    private val onFailure: (Exception) -> Unit = {}
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        analyzeFrame(frameProvider = { FrameMapper.from(image) }, closeFrame = image::close)
    }

    internal fun analyzeFrame(frameProvider: () -> CameraFrame, closeFrame: () -> Unit) {
        try {
            frameProcessor.process(frameProvider())
        } catch (exception: Exception) {
            onFailure(exception)
        } finally {
            closeFrame()
        }
    }
}