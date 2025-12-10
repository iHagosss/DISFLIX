package com.tvplayer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.media3.ui.PlayerView
import com.tvplayer.app.models.SkipMarkers
import com.tvplayer.app.models.SkipSegmentType
import com.tvplayer.app.skipdetection.SkipDetectionResult

class UIController(
    private val context: Context,
    private val playerView: PlayerView,
    private val playerController: PlayerController,
    private val skipMarkers: SkipMarkers,
    private val preferencesHelper: PreferencesHelper
) {

    private var customControls: View? = null
    private var skipButtonsOverlayContainer: View? = null
    private var btnSkipIntro: Button? = null
    private var btnSkipRecap: Button? = null
    private var btnSkipCredits: Button? = null
    private var btnNextEpisode: Button? = null
    private var btnSettings: ImageButton? = null
    private var btnSubtitleDelayUp: ImageButton? = null
    private var btnSubtitleDelayDown: ImageButton? = null
    private var tvSubtitleDelay: TextView? = null

    private val SUBTITLE_DELAY_STEP_MS = 100
    private var currentSubtitleDelayMs: Int = 0

    fun setCustomControls(controls: View, skipContainer: View) {
        customControls = controls
        skipButtonsOverlayContainer = skipContainer

        btnSkipIntro = skipContainer.findViewById(R.id.btn_skip_intro)
        btnSkipRecap = skipContainer.findViewById(R.id.btn_skip_recap)
        btnSkipCredits = skipContainer.findViewById(R.id.btn_skip_credits)
        btnNextEpisode = skipContainer.findViewById(R.id.btn_next_episode)
        
        btnSettings = controls.findViewById(R.id.btn_settings)
        btnSubtitleDelayUp = controls.findViewById(R.id.btn_subtitle_delay_up)
        btnSubtitleDelayDown = controls.findViewById(R.id.btn_subtitle_delay_down)
        tvSubtitleDelay = controls.findViewById(R.id.tv_subtitle_delay)

        currentSubtitleDelayMs = preferencesHelper.getSubtitleDelay()
        updateSubtitleDelayText()
        
        setupListeners()
        hideSkipButtons()
    }

    private fun setupListeners() {
        btnSkipIntro?.setOnClickListener { skipSegment(SkipSegmentType.INTRO) }
        btnSkipRecap?.setOnClickListener { skipSegment(SkipSegmentType.RECAP) }
        btnSkipCredits?.setOnClickListener { skipSegment(SkipSegmentType.CREDITS) }
        btnNextEpisode?.setOnClickListener { handleNextEpisodeClick() }
        
        btnSettings?.setOnClickListener {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }

        btnSubtitleDelayUp?.setOnClickListener { adjustSubtitleDelay(SUBTITLE_DELAY_STEP_MS) }
        btnSubtitleDelayDown?.setOnClickListener { adjustSubtitleDelay(-SUBTITLE_DELAY_STEP_MS) }
    }

    private fun adjustSubtitleDelay(deltaMs: Int) {
        currentSubtitleDelayMs += deltaMs
        preferencesHelper.setSubtitleDelay(currentSubtitleDelayMs)
        playerController.setSubtitleDelay(currentSubtitleDelayMs)
        updateSubtitleDelayText()
    }
    
    private fun updateSubtitleDelayText() {
        val delaySec = currentSubtitleDelayMs / 1000.0f
        tvSubtitleDelay?.text = String.format("%.1fs", delaySec)
    }

    fun hideSkipButtons() {
        skipButtonsOverlayContainer?.visibility = View.GONE
        btnSkipIntro?.visibility = View.GONE
        btnSkipRecap?.visibility = View.GONE
        btnSkipCredits?.visibility = View.GONE
        btnNextEpisode?.visibility = View.GONE
    }
    
    fun showSkipButtons(result: SkipDetectionResult) {
        hideSkipButtons()
        var anyVisible = false

        result.getSegmentByType(SkipSegmentType.INTRO)?.let {
            btnSkipIntro?.visibility = View.VISIBLE
            btnSkipIntro?.text = "Skip Intro"
            anyVisible = true
        }

        result.getSegmentByType(SkipSegmentType.RECAP)?.let {
            btnSkipRecap?.visibility = View.VISIBLE
            btnSkipRecap?.text = "Skip Recap"
            anyVisible = true
        }

        result.getSegmentByType(SkipSegmentType.CREDITS)?.let {
            btnSkipCredits?.visibility = View.VISIBLE
            btnSkipCredits?.text = "Skip Credits"
            anyVisible = true
        }
        
        if (skipMarkers.nextEpisodeStart != null && skipMarkers.nextEpisodeStart!! > 0) {
             btnNextEpisode?.visibility = View.VISIBLE
             btnNextEpisode?.text = "Next Episode"
             anyVisible = true
        }

        if (anyVisible) {
            skipButtonsOverlayContainer?.visibility = View.VISIBLE
        }
    }

    private fun skipSegment(type: SkipSegmentType) {
        val segment = when (type) {
            SkipSegmentType.INTRO -> skipMarkers.intro
            SkipSegmentType.RECAP -> skipMarkers.recap
            SkipSegmentType.CREDITS -> skipMarkers.credits
            else -> null
        }

        if (segment != null) {
            val seekTimeMs = segment.endTime * 1000L + 500
            playerController.seekTo(seekTimeMs)
            hideSkipButtons()
            Toast.makeText(context, "Skipping...", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun applyDetectedSkipMarkers(result: SkipDetectionResult) {
        skipMarkers.intro = result.getSegmentByType(SkipSegmentType.INTRO)
        skipMarkers.recap = result.getSegmentByType(SkipSegmentType.RECAP)
        skipMarkers.credits = result.getSegmentByType(SkipSegmentType.CREDITS)
        
        val credits = skipMarkers.credits
        if (credits != null) {
            skipMarkers.nextEpisodeStart = credits.startTime
        }
    }

    fun handleNextEpisodeClick() {
        val player = playerController.getPlayer() ?: return
        val currentUri = player.currentMediaItem?.localConfiguration?.uri ?: return
        
        val parsed = MediaMetadataParser.parseFromUri(currentUri)
        if (parsed.isTvShow && parsed.seasonNumber != null && parsed.episodeNumber != null) {
            val nextEp = parsed.episodeNumber!! + 1
            
            val currentEpStr = "E${parsed.episodeNumber.toString().padStart(2, '0')}"
            val nextEpStr = "E${nextEp.toString().padStart(2, '0')}"
            
            val nextUriString = currentUri.toString().replace(currentEpStr, nextEpStr)
            
            Toast.makeText(context, "Loading next episode: $nextEpStr", Toast.LENGTH_SHORT).show()
            
            val intent = Intent(context, MainActivity::class.java)
            intent.data = Uri.parse(nextUriString)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    fun handleMediaEnd() {
        handleNextEpisodeClick()
    }
}
