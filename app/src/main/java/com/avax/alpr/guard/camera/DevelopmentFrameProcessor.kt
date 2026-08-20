package com.avax.alpr.guard.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class DevelopmentFrameProcessor : FrameProcessor {

    private val frameCounter = AtomicLong(0)
    private val _diagnostics = MutableStateFlow<FrameDiagnostics?>(null)

    val diagnostics: StateFlow<FrameDiagnostics?> = _diagnostics.asStateFlow()

    override fun process(frame: CameraFrame) {
        val count = frameCounter.incrementAndGet()

        if (count == 1L || count % DIAGNOSTIC_INTERVAL == 0L) {
            _diagnostics.value = FrameDiagnostics(
                frameCount = count,
                metadata = frame.metadata
            )
        }
    }

    private companion object {
        const val DIAGNOSTIC_INTERVAL = 30L
    }
}