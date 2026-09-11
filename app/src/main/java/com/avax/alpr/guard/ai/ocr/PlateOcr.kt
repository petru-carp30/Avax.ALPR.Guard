package com.avax.alpr.guard.ai.ocr

import android.graphics.Bitmap

interface PlateOcr : AutoCloseable {
    fun recognize(crop: Bitmap, onComplete: (Result<PlateOcrResult?>) -> Unit)
    override fun close() = Unit
}