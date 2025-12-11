package com.tvplayer.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// Correct import to point to your provided SmartSkipManager location
import com.tvplayer.app.skipdetection.SmartSkipManager
import com.tvplayer.app.skipdetection.SmartSkipManager.SkipDetectionCallback
import com.tvplayer.app.skipdetection.SmartSkipManager.SkipDetectionResult
import com.tvplayer.app.skipdetection.SmartSkipManager.MediaIdentifier
// Import Stremio classes
import com.tvplayer.app.stremio.StremioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Change reference from SkipDetectionManager to SmartSkipManager
    private lateinit var skipManager: SmartSkipManager
    private lateinit var btnSkip: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI
        btnSkip = findViewById(R.id.btnSkip) // Ensure this ID exists in your layout xml
        btnSkip.visibility = View.GONE
        
        // Instantiate the SmartSkipManager
        // Note: You must initialize PreferencesHelper here
        val prefsHelper = PreferencesHelper() 
        skipManager = SmartSkipManager(this, prefsHelper)

        // Example: Detect segments for a specific media item (e.g., episode-1)
        val mediaId = MediaIdentifier("tt1234567:1:1") // Placeholder ID
        
        skipManager.detectSkipSegmentsAsync(mediaId, object : SkipDetectionCallback {
            override fun onDetectionComplete(result: SkipDetectionResult) {
                if (result.isSuccess && result.segments.isNotEmpty()) {
                    runOnUiThread {
                        btnSkip.text = "Skip Intro" // Update based on actual segment type
                        btnSkip.visibility = View.VISIBLE
                        btnSkip.setOnClickListener {
                            Toast.makeText(this@MainActivity, "Skipped via SmartSkipManager!", Toast.LENGTH_SHORT).show()
                            // Logic to seek the player to the end time of the first segment would go here
                            btnSkip.visibility = View.GONE
                        }
                    }
                }
            }
        })
        
        // Example: Accessing Stremio Manager from App
        val app = application as StremioPlayerApp
        val stremio = app.stremioManager
        
        // Monitor Stremio Ready State
        CoroutineScope(Dispatchers.Main).launch {
            stremio.isReady.collect { ready ->
                if (ready) {
                    Toast.makeText(this@MainActivity, "Stremio Ready", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure the thread pool is shut down when the activity is destroyed
        skipManager.shutdown() 
    }
}
