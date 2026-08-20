package com.avax.alpr.guard.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraSession(context: Context) {

    private val applicationContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(applicationContext)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AvaxCameraAnalysis")
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var binding = false
    private var closed = false

    fun bind(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        frameProcessor: FrameProcessor,
        onStateChanged: (CameraRuntimeState) -> Unit
    ) {
        if (closed || binding || previewUseCase != null || analysisUseCase != null) return

        binding = true
        onStateChanged(CameraRuntimeState.Starting)

        val providerFuture = ProcessCameraProvider.getInstance(applicationContext)

        providerFuture.addListener({
            if (closed) return@addListener

            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    binding = false
                    onStateChanged(CameraRuntimeState.Unavailable)
                    return@addListener
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(
                    analysisExecutor,
                    CameraFrameAnalyzer(frameProcessor) { exception ->
                        Log.e(TAG, "Camera frame analysis failed.", exception)
                    }
                )

                previewUseCase = preview
                analysisUseCase = analysis

                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )

                binding = false
                onStateChanged(CameraRuntimeState.Active)
            } catch (exception: Exception) {
                binding = false
                releaseUseCases()
                Log.e(TAG, "CameraX binding failed.", exception)
                onStateChanged(CameraRuntimeState.Error)
            }
        }, mainExecutor)
    }

    fun close() {
        if (closed) return

        closed = true
        analysisUseCase?.clearAnalyzer()
        releaseUseCases()
        analysisExecutor.shutdown()
    }

    private fun releaseUseCases() {
        val provider = cameraProvider
        previewUseCase?.let { preview -> provider?.unbind(preview) }
        analysisUseCase?.let { analysis -> provider?.unbind(analysis) }

        previewUseCase = null
        analysisUseCase = null
    }

    private companion object {
        const val TAG = "CameraSession"
    }
}