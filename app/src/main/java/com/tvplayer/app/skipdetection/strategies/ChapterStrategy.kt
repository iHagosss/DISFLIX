package com.tvplayer.app.skipdetection.strategies

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tvplayer.app.models.DetectionSource
import com.tvplayer.app.models.SkipSegment
import com.tvplayer.app.models.SkipSegmentType
import com.tvplayer.app.skipdetection.MediaIdentifier
import com.tvplayer.app.skipdetection.SkipDetectionResult
import com.tvplayer.app.skipdetection.SkipStrategy
import java.util.concurrent.TimeUnit

private const val TAG = "ChapterStrategy"

class ChapterStrategy(private var player: ExoPlayer?) : SkipStrategy {

    override fun getStrategyName(): String = "ChapterStrategy"

    fun rebindPlayer(newPlayer: Player?) {
        this.player = newPlayer as ExoPlayer?
    }

    override fun isAvailable(identifier: MediaIdentifier): Boolean {
        return player != null && player?.playbackState == ExoPlayer.STATE_READY
    }

    override fun getPriority(): Int = 200

    override fun execute(identifier: MediaIdentifier): SkipDetectionResult {
        val currentPlayer = player ?: return SkipDetectionResult.EMPTY
        
        val chapters = currentPlayer.currentMediaItem?.mediaMetadata?.extras?.get("chapters") as? List<*> ?: emptyList<Any>()
        
        if (chapters.isEmpty()) {
             return SkipDetectionResult.failed(DetectionSource.CHAPTER, "No chapters found in media.")
        }

        // NOTE: Full chapter extraction logic is omitted for brevity as it requires platform-specific metadata keys.
        // In a real implementation, iterate through 'chapters' and create SkipSegments.
        val segments = mutableListOf<SkipSegment>()
        return SkipDetectionResult.success(
            DetectionSource.CHAPTER,
            1.0f,
            *segments.toTypedArray()
        )
    }
}
