package com.tvplayer.app.skipdetection.strategies

import com.tvplayer.app.PreferencesHelper
import com.tvplayer.app.models.DetectionSource
import com.tvplayer.app.models.SkipSegment
import com.tvplayer.app.models.SkipSegmentType
import com.tvplayer.app.skipdetection.MediaIdentifier
import com.tvplayer.app.skipdetection.SkipDetectionResult
import com.tvplayer.app.skipdetection.SkipStrategy

class ManualPreferenceStrategy(
    private val prefsHelper: PreferencesHelper
) : SkipStrategy {
    
    override fun execute(identifier: MediaIdentifier): SkipDetectionResult {
        val segments = mutableListOf<SkipSegment>()
        
        val introStart = prefsHelper.getIntroStart().toLong()
        val introEnd = prefsHelper.getIntroEnd().toLong()
        if (introStart >= 0 && introEnd > introStart) {
            segments.add(SkipSegment(SkipSegmentType.INTRO, introStart, introEnd, DetectionSource.MANUAL_PREFERENCES, 0.5f))
        }
        
        val recapStart = prefsHelper.getRecapStart().toLong()
        val recapEnd = prefsHelper.getRecapEnd().toLong()
        if (recapStart >= 0 && recapEnd > recapStart) {
            segments.add(SkipSegment(SkipSegmentType.RECAP, recapStart, recapEnd, DetectionSource.MANUAL_PREFERENCES, 0.5f))
        }
        
        val creditsStart = prefsHelper.getCreditsStart().toLong()
        val runtimeSeconds = identifier.runtimeSeconds
        if (creditsStart > 0 && runtimeSeconds > 0) {
            val actualStart = (runtimeSeconds - creditsStart)
            if (actualStart > 0) {
                segments.add(SkipSegment(SkipSegmentType.CREDITS, actualStart, runtimeSeconds, DetectionSource.MANUAL_PREFERENCES, 0.5f))
            }
        }
        
        return SkipDetectionResult.success(
            DetectionSource.MANUAL_PREFERENCES,
            0.5f,
            *segments.toTypedArray()
        )
    }
    
    override fun getStrategyName(): String = "Manual Preferences (Fallback)"
    
    override fun isAvailable(identifier: MediaIdentifier): Boolean = true
    
    override fun getPriority(): Int = 1000
}
