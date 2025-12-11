package com.tvplayer.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tvplayer.app.models.StreamData
import com.tvplayer.app.skipdetection.SkipSegment
import com.tvplayer.app.skipdetection.SkipDetectionManager
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private lateinit var skipDetectionManager: SkipDetectionManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)
        skipDetectionManager = SkipDetectionManager(this)

        // Hide system UI for immersive experience
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        // Get stream data from intent
        val streamDataJson = intent.getStringExtra("streamData")
        val metaDataJson = intent.getStringExtra("metaData")
        
        if (streamDataJson != null) {
            try {
                val streamData = decodeStreamData(streamDataJson)
                if (streamData != null) {
                    initializePlayer(streamData, metaDataJson)
                } else {
                    showError("Failed to decode stream data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing stream data", e)
                showError("Error loading stream: ${e.message}")
            }
        } else {
            // For testing, load a sample stream
            loadSampleStream()
        }
    }

    private fun decodeStreamData(json: String): StreamData? {
        return try {
            val gson = Gson()
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
            
            // Parse the stream data based on Stremio format
            val url = jsonObj.get("url")?.asString ?: ""
            val title = jsonObj.get("title")?.asString ?: ""
            val type = jsonObj.get("type")?.asString ?: "movie"
            
            StreamData(
                url = url,
                title = title,
                type = type,
                subtitles = emptyList() // Parse subtitles if available
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding stream data", e)
            null
        }
    }

    private fun initializePlayer(streamData: StreamData, metaDataJson: String?) {
        try {
            // Initialize ExoPlayer
            player = ExoPlayer.Builder(this).build()
            playerView.player = player

            // Parse metadata if available
            var title = streamData.title
            var description = ""
            
            if (metaDataJson != null) {
                try {
                    val gson = Gson()
                    val metaObj = gson.fromJson(metaDataJson, JsonObject::class.java)
                    title = metaObj.get("name")?.asString ?: metaObj.get("title")?.asString ?: title
                    description = metaObj.get("description")?.asString ?: ""
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing metadata", e)
                }
            }

            // Set up media item
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(streamData.url))
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setDescription(description)
                        .build()
                )
                .build()

            player?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                
                // Add listener for skip detection
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            // Start skip detection
                            skipDetectionManager.startDetection(
                                this@MainActivity.player!!,
                                streamData.type == "series"
                            )
                        }
                    }
                })
            }

            Log.d(TAG, "Player initialized with URL: ${streamData.url}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player", e)
            showError("Failed to initialize player: ${e.message}")
        }
    }

    private fun loadSampleStream() {
        // Sample stream for testing
        val sampleUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val streamData = StreamData(
            url = sampleUrl,
            title = "Sample Video - Big Buck Bunny",
            type = "movie",
            subtitles = emptyList()
        )
        initializePlayer(streamData, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle D-pad navigation for TV
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                player?.let {
                    if (it.isPlaying) {
                        it.pause()
                    } else {
                        it.play()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.seekBack()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.seekForward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let {
                    if (it.isPlaying) {
                        it.pause()
                    } else {
                        it.play()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player?.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player?.seekForward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player?.seekBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.e(TAG, message)
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        skipDetectionManager.stopDetection()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        coroutineScope.cancel()
    }
}
