package com.tvplayer.app.skipdetection

import com.tvplayer.app.models.DetectionSource
import com.tvplayer.app.models.SkipSegment
import com.tvplayer.app.models.SkipSegmentType

data class SkipDetectionResult(
    val segments: List<SkipSegment>,
    val source: DetectionSource,
    val confidence: Float
) {
    val isSuccess: Boolean
        get() = segments.isNotEmpty()

    fun getSegmentByType(type: SkipSegmentType): SkipSegment? {
        return segments.firstOrNull { it.type == type }
    }

    companion object {
        val EMPTY = SkipDetectionResult(
            segments = emptyList(),
            source = DetectionSource.MANUAL,
            confidence = 0f
        )

        fun success(source: DetectionSource, confidence: Float, vararg segments: SkipSegment): SkipDetectionResult {
            return SkipDetectionResult(segments.toList(), source, confidence)
        }

        fun failed(source: DetectionSource, errorMessage: String?): SkipDetectionResult {
            return SkipDetectionResult(emptyList(), source, 0f)
        }
    }
}
