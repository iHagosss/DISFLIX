package com.tvplayer.app.models

data class SkipSegment(
    val type: SkipSegmentType,
    val startTime: Long,
    val endTime: Long,
    val source: DetectionSource = DetectionSource.MANUAL,
    val confidence: Float = 1.0f
) {
    val isValid: Boolean
        get() = startTime >= 0 && endTime > startTime
}
