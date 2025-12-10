package com.tvplayer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tvplayer.app.models.SkipMarkers
import com.tvplayer.app.models.SkipSegment
import com.tvplayer.app.models.SkipSegmentType
import com.tvplayer.app.skipdetection.MediaIdentifier
import com.tvplayer.app.skipdetection.SkipDetectionCallback
import com.tvplayer.app.skipdetection.SkipDetectionResult
import com.tvplayer.app.skipdetection.SmartSkipManager
import com.tvplayer.app.skipdetection.strategies.ChapterStrategy
import java.util.concurrent.TimeUnit

class PlayerController(
    private val context: Context,
    private val playerView: PlayerView,
    private val playerStateListener: PlayerStateListener
) : Player.Listener {

    private val TAG = "PlayerController"
    private var player: ExoPlayer? = null

    private lateinit var preferencesHelper: PreferencesHelper
    private lateinit var smartSkipManager: SmartSkipManager
    private lateinit var skipMarkers: SkipMarkers
    private var currentSubtitleDelayMs = 0

    private val timeUpdateHandler = Handler(Looper.getMainLooper())
    private val SCRUB_INTERVAL_MS = 100L
    private val SCRUB_BASE_STEP_MS = 5000L
    private var scrubMultiplier = 0

    interface PlayerStateListener : SkipDetectionCallback {
        fun onPlayerReady()
        fun onPlayerError(error: String)
        fun onProgressUpdate(currentPos: Long, duration: Long)
        fun updateMediaInfo(title: String?, season: Int?, episode: Int?)
        fun onSkipSegmentsDetected(result: SkipDetectionResult)
        fun onMediaEnded()
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                addListener(this@PlayerController)
                playerView.player = this
            }
            val chapterStrategy = ChapterStrategy(player)
            if (::smartSkipManager.isInitialized) {
                smartSkipManager.addChapterStrategy(chapterStrategy)
            }
        }
    }

    fun setHelpers(prefs: PreferencesHelper, skipManager: SmartSkipManager, markers: SkipMarkers) {
        preferencesHelper = prefs
        smartSkipManager = skipManager
        skipMarkers = markers
    }

    fun getPlayer(): ExoPlayer? = player

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }
    
    fun pausePlayback() {
        player?.pause()
    }
    
    fun isPlaying(): Boolean = player?.isPlaying == true

    fun fastForward(ms: Long) {
        player?.seekTo((player!!.currentPosition + ms).coerceAtMost(player!!.duration))
    }

    fun rewind(ms: Long) {
        player?.seekTo((player!!.currentPosition - ms).coerceAtLeast(0))
    }
    
    fun seekTo(ms: Long) {
        player?.seekTo(ms)
    }

    fun startScrubbing(multiplier: Int) {
        if (scrubMultiplier == 0 && multiplier != 0) {
            scrubMultiplier = multiplier
            timeUpdateHandler.post(scrubRunnable)
        } else {
            scrubMultiplier = multiplier
        }
    }

    fun stopScrubbing() {
        scrubMultiplier = 0
        timeUpdateHandler.removeCallbacks(scrubRunnable)
    }

    fun initPlayer(view: PlayerView, uri: Uri) {
        initializePlayer()
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    fun rebindPlayer(newPlayer: ExoPlayer?) {
        player?.removeListener(this)
        player = newPlayer
        player?.addListener(this)
        playerView.player = player
        
        if (::smartSkipManager.isInitialized) {
            smartSkipManager.rebindPlayer(newPlayer)
        }
    }

    fun startUpdates() {
        timeUpdateHandler.post(timeUpdateRunnable)
        loadPreferencesAndApply()
    }

    fun stopUpdates() {
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        stopScrubbing()
    }

    fun onResume() {
        player?.playWhenReady = true
    }

    fun onPause() {
        player?.playWhenReady = false
        stopUpdates()
    }

    fun onDestroy() {
        player?.release()
        player = null
        stopUpdates()
        if (::smartSkipManager.isInitialized) {
            smartSkipManager.shutdown()
        }
    }

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && p.playbackState == Player.STATE_READY && p.isPlaying) {
                updateProgress()
                checkAutoSkip(p.currentPosition)
            }
            timeUpdateHandler.postDelayed(this, 1000)
        }
    }

    fun updateProgress() {
        val p = player ?: return
        if (p.duration <= 0 || p.duration == C.TIME_UNSET) return

        val durationMs = p.duration
        val currentPositionMs = p.currentPosition

        playerStateListener.onProgressUpdate(currentPositionMs, durationMs)
    }

    private fun checkAutoSkip(positionMs: Long) {
        if (!::preferencesHelper.isInitialized || !::skipMarkers.isInitialized) return
        
        val posSec = TimeUnit.MILLISECONDS.toSeconds(positionMs)
        
        if (preferencesHelper.isAutoSkipIntro() && skipMarkers.isInIntro(posSec)) {
             skipMarkers.intro?.let { seekTo(it.endTime * 1000L) }
        }
        
        if (preferencesHelper.isAutoSkipRecap() && skipMarkers.isInRecap(posSec)) {
             skipMarkers.recap?.let { seekTo(it.endTime * 1000L) }
        }
    }

    private fun loadPreferencesAndApply() {
        if (!::preferencesHelper.isInitialized || !::skipMarkers.isInitialized) return
        setSubtitleDelay(preferencesHelper.getSubtitleDelay())
    }

    fun setSubtitleDelay(delayMs: Int) {
        currentSubtitleDelayMs = delayMs
        Log.i(TAG, "Subtitle delay set: ${delayMs}ms")
    }

    private fun startSkipDetection() {
        val p = player ?: return
        if (p.duration <= 0 || !::smartSkipManager.isInitialized) return

        val parsed = MediaMetadataParser.parseFromUri(p.currentMediaItem?.localConfiguration?.uri)
        
        var title = parsed.showName ?: "Unknown"
        if (p.mediaMetadata.title != null) {
            title = p.mediaMetadata.title.toString()
        }

        val builder = MediaIdentifier.Builder()
            .setTitle(title)
            .setRuntimeSeconds((p.duration / 1000).toLong())

        if (parsed.isTvShow) {
            builder.setShowName(parsed.showName)
            builder.setSeasonNumber(parsed.seasonNumber)
            builder.setEpisodeNumber(parsed.episodeNumber)
            playerStateListener.updateMediaInfo(parsed.showName, parsed.seasonNumber, parsed.episodeNumber)
        } else {
            playerStateListener.updateMediaInfo(title, null, null)
        }

        val intent = (context as? MainActivity)?.intent

        intent?.extras?.let { extras ->
            extras.getString("trakt_id")?.let { builder.setTraktId(it) }
            extras.getString("tmdb_id")?.let { builder.setTmdbId(it) }
            extras.getString("tvdb_id")?.let { builder.setTvdbId(it) }
            extras.getString("imdb_id")?.let { builder.setImdbId(it) }
        }

        smartSkipManager.detectSkipSegmentsAsync(builder.build(), playerStateListener)
    }

    private val scrubRunnable = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && scrubMultiplier != 0) {
                val seekAmount = scrubMultiplier * SCRUB_BASE_STEP_MS
                val newPos = (p.currentPosition + seekAmount).coerceIn(0, p.duration)
                p.seekTo(newPos)
                updateProgress()
                timeUpdateHandler.postDelayed(this, SCRUB_INTERVAL_MS)
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            playerStateListener.onPlayerReady()
            loadPreferencesAndApply()
            startSkipDetection()
            startUpdates()
        } else if (playbackState == Player.STATE_ENDED) {
            stopUpdates()
            playerStateListener.onMediaEnded()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "Error", error)
        playerStateListener.onPlayerError(error.message ?: "Unknown Error")
    }
}
