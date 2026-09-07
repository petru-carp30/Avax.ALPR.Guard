package com.avax.alpr.guard.ai.detector

internal object DetectorModelConfig {
    const val MODEL_FILE_NAME = "avax_plate_detector_yolox_nano_512_v1.onnx"
    const val MODEL_FILE_SIZE_BYTES = 3_703_049
    const val MODEL_SHA256 = "B42313FE76EFCD98332430A25FE6BEEC553FC5FE7633957917453836AEA81793"

    const val INPUT_NAME = "images"
    const val OUTPUT_NAME = "output"

    const val INPUT_SIZE = 512
    const val CANDIDATE_COUNT = 5376
    const val CANDIDATE_VALUES = 6

    const val CONFIDENCE_THRESHOLD = 0.225f
    const val NMS_THRESHOLD = 0.45f
    const val PADDING_VALUE = 114f

    val INPUT_SHAPE = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
    val OUTPUT_SHAPE = longArrayOf(1, CANDIDATE_COUNT.toLong(), CANDIDATE_VALUES.toLong())
}