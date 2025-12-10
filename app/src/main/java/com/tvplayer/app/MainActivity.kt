package com.tvplayer.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import com.tvplayer.app.models.SkipMarkers
import com.tvplayer.app.skipdetection.SkipDetectionResult
import com.tvplayer.app.skipdetection.SmartSkipManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), PlayerController.PlayerStateListener {

    companion object {
        private const val CONTROLS_TIMEOUT = 5000
        private const val REWIND_MS = 10000L
        private const val FORWARD_MS = 30000L
        private const val DPAD_QUICK_SEEK_MS = 10000L
        private const val TAG = "MainActivity"
    }

    private lateinit var preferencesHelper: PreferencesHelper
    private lateinit var skipMarkers: SkipMarkers
    private lateinit var smartSkipManager: SmartSkipManager
    private lateinit var playerController: PlayerController
    private lateinit var uiController: UIController

    private lateinit var playerView: PlayerView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvFinishTime: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var tvEpisode: TextView
    private lateinit var tvError: TextView
    private lateinit var customControls: View
    private lateinit var skipButtonsContainer: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnFastForward: ImageButton
    private lateinit var btnRewind: ImageButton
    
    private val controlsHandler = Handler(Looper.getMainLooper())
    private var controlsRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 1. Initialize Views
        playerView = findViewById(R.id.player_view)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        tvRemainingTime = findViewById(R.id.tv_remaining_time)
        tvFinishTime = findViewById(R.id.tv_finish_time)
        progressBar = findViewById(R.id.progress_bar)
        tvTitle = findViewById(R.id.tv_title)
        tvEpisode = findViewById(R.id.tv_episode)
        tvError = findViewById(R.id.tv_error_message)
        customControls = findViewById(R.id.custom_controls_container)
        skipButtonsContainer = findViewById(R.id.skip_buttons_overlay)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnFastForward = findViewById(R.id.btn_fast_forward)
        btnRewind = findViewById(R.id.btn_rewind)
        
        tvError.visibility = View.GONE

        // 2. Initialize Helpers
        preferencesHelper = PreferencesHelper(this)
        skipMarkers = SkipMarkers()
        smartSkipManager = SmartSkipManager(this, preferencesHelper)

        // 3. Initialize Controllers
        playerController = PlayerController(this, playerView, this)
        uiController = UIController(this, playerView, playerController, skipMarkers, preferencesHelper)

        uiController.setCustomControls(customControls, skipButtonsContainer)
        
        playerController.setHelpers(preferencesHelper, smartSkipManager, skipMarkers)

        btnPlayPause.setOnClickListener { playerController.togglePlayPause(); updatePlayPauseButton() }
        btnFastForward.setOnClickListener { playerController.fastForward(FORWARD_MS) }
        btnRewind.setOnClickListener { playerController.rewind(REWIND_MS) }

        val mediaUri: Uri? = intent.data
        if (mediaUri != null) {
            playerController.initPlayer(playerView, mediaUri)
        } else {
            Log.e(TAG, "No media URI provided.")
            onPlayerError("No media file selected.")
        }

        resetControlsTimeout()
        updatePlayPauseButton()
    }

    override fun onStart() {
        super.onStart()
        playerController.onResume()
    }

    override fun onStop() {
        super.onStop()
        playerController.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.onDestroy()
    }

    override fun onProgressUpdate(currentPos: Long, duration: Long) {
        val currentSeconds = TimeUnit.MILLISECONDS.toSeconds(currentPos)
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(duration)
        
        val hours = currentSeconds / 3600
        val minutes = (currentSeconds % 3600) / 60
        val seconds = currentSeconds % 60
        
        val durHours = durationSeconds / 3600
        val durMinutes = (durationSeconds % 3600) / 60
        val durSeconds = durationSeconds % 60

        val timeStr = if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
        val durStr = if (durHours > 0) String.format("%d:%02d:%02d", durHours, durMinutes, durSeconds) else String.format("%02d:%02d", durMinutes, durSeconds)
        
        val remainingSeconds = durationSeconds - currentSeconds
        val remHours = remainingSeconds / 3600
        val remMinutes = (remainingSeconds % 3600) / 60
        val remSeconds = remainingSeconds % 60
        val remStr = if (remHours > 0) String.format("-%d:%02d:%02d", remHours, remMinutes, remSeconds) else String.format("-%02d:%02d", remMinutes, remSeconds)

        tvCurrentTime.text = timeStr
        tvTotalTime.text = durStr
        tvRemainingTime.text = remStr
        
        val remainingMs = duration - currentPos
        val finishTimeMs = System.currentTimeMillis() + remainingMs
        val finishFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        tvFinishTime.text = "Finish ${finishFormat.format(java.util.Date(finishTimeMs))}"
        
        if (duration > 0) {
            progressBar.progress = ((currentPos.toDouble() / duration) * 100).toInt()
        }
    }
    
    override fun updateMediaInfo(title: String?, season: Int?, episode: Int?) {
        tvTitle.text = title ?: "Loading..."
        if (season != null && episode != null) {
            tvEpisode.text = String.format("S%02dE%02d", season, episode)
            tvEpisode.visibility = View.VISIBLE
        } else {
            tvEpisode.visibility = View.GONE
        }
    }

    override fun onPlayerError(error: String) {
        tvError.text = "Error: $error"
        tvError.visibility = View.VISIBLE
        
        AlertDialog.Builder(this)
            .setTitle("Playback Error")
            .setMessage(error)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onPlayerReady() {
        tvError.visibility = View.GONE
        updatePlayPauseButton()
    }
    
    override fun onMediaEnded() {
        uiController.handleMediaEnd()
    }

    override fun onSkipSegmentsDetected(result: SkipDetectionResult) {
        // No-op, handled via detection complete
    }

    override fun onDetectionComplete(result: SkipDetectionResult) {
        uiController.applyDetectedSkipMarkers(result)
        uiController.showSkipButtons(result)
    }
    
    override fun onDetectionFailed(errorMessage: String) {
        Log.e(TAG, "Skip detection failed: $errorMessage")
    }

    private fun updatePlayPauseButton() {
        val icon = if (playerController.isPlaying()) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        btnPlayPause.setImageResource(icon)
    }

    private fun resetControlsTimeout() {
        controlsRunnable?.let { controlsHandler.removeCallbacks(it) }
        controlsRunnable = Runnable {
            customControls.visibility = View.GONE
            playerView.hideController()
        }
        controlsHandler.postDelayed(controlsRunnable!!, CONTROLS_TIMEOUT.toLong())
    }

    private fun toggleControls() {
        if (customControls.visibility == View.VISIBLE) {
            customControls.visibility = View.GONE
            controlsHandler.removeCallbacks(controlsRunnable!!)
        } else {
            customControls.visibility = View.VISIBLE
            resetControlsTimeout()
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            toggleControls()
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (customControls.visibility != View.VISIBLE) {
            customControls.visibility = View.VISIBLE
            resetControlsTimeout()
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    playerController.togglePlayPause()
                    updatePlayPauseButton()
                    return true
                }
                
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    playerController.fastForward(DPAD_QUICK_SEEK_MS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    playerController.rewind(DPAD_QUICK_SEEK_MS)
                    return true
                }

                KeyEvent.KEYCODE_MENU -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    finish()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    playerController.togglePlayPause()
                    updatePlayPauseButton()
                    toggleControls()
                    return true
                }
            }
        }

        if (playerView.dispatchKeyEvent(event)) {
            customControls.visibility = View.VISIBLE
            resetControlsTimeout()
            return true
        }

        return super.dispatchKeyEvent(event)
    }
}
