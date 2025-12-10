package com.tvplayer.app.skipdetection.strategies

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tvplayer.app.PreferencesHelper
import com.tvplayer.app.models.DetectionSource
import com.tvplayer.app.models.SkipSegment
import com.tvplayer.app.models.SkipSegmentType
import com.tvplayer.app.skipdetection.MediaIdentifier
import com.tvplayer.app.skipdetection.SkipDetectionResult
import com.tvplayer.app.skipdetection.SkipStrategy
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MetadataHeuristicStrategy(
    private val prefsHelper: PreferencesHelper
) : SkipStrategy {
    
    companion object {
        private const val TAG = "MetadataHeuristicStrategy"
        private const val TIMEOUT_SECONDS = 8L
        private const val TRAKT_API_URL = "https://api.example.com/trakt"
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    override fun execute(identifier: MediaIdentifier): SkipDetectionResult {
        if (!isAvailable(identifier)) {
            return SkipDetectionResult.failed(DetectionSource.METADATA_HEURISTIC, "No API keys")
        }
        
        val segments = mutableListOf<SkipSegment>()
        
        var runtimeSeconds = identifier.runtimeSeconds
        if (runtimeSeconds <= 0 && identifier.traktId != null) {
            runtimeSeconds = fetchRuntimeFromTrakt(identifier)
        }
        
        val finalIdentifier = if (runtimeSeconds > 0 && identifier.runtimeSeconds <= 0) {
            runtimeSeconds
        } else {
            identifier.runtimeSeconds
        }
        
        applyHeuristics(segments, finalIdentifier, identifier.showName != null)
        
        return if (segments.isNotEmpty()) {
            SkipDetectionResult.success(
                DetectionSource.METADATA_HEURISTIC,
                0.5f,
                *segments.toTypedArray()
            )
        } else {
            SkipDetectionResult.failed(DetectionSource.METADATA_HEURISTIC, "No segments found")
        }
    }
    
    private fun fetchRuntimeFromTrakt(identifier: MediaIdentifier): Long {
        val traktId = identifier.traktId ?: return 0L
        val season = identifier.seasonNumber ?: return 0L
        val episode = identifier.episodeNumber ?: return 0L
        
        val url = "$TRAKT_API_URL/shows/$traktId/seasons/$season/episodes/$episode"
        
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Error: $response")
                
                val jsonResponse = response.body?.string()
                val root = gson.fromJson(jsonResponse, JsonObject::class.java)
                val runtimeMinutes = root.get("runtime_minutes")?.asInt ?: 0
                
                if (runtimeMinutes > 0) {
                    (runtimeMinutes * 60).toLong()
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Trakt", e)
            0L
        }
    }
    
    private fun applyHeuristics(segments: MutableList<SkipSegment>, runtimeSeconds: Long, isTvShow: Boolean) {
        if (!isTvShow || runtimeSeconds <= 0) return
        
        if (runtimeSeconds >= 20 * 60) {
            val introEnd = 90L
            segments.add(SkipSegment(SkipSegmentType.INTRO, 0L, introEnd, DetectionSource.METADATA_HEURISTIC, 0.5f))
            
            val creditsStart = (runtimeSeconds - 180)
            if (creditsStart > introEnd) {
                segments.add(SkipSegment(SkipSegmentType.CREDITS, creditsStart, runtimeSeconds, DetectionSource.METADATA_HEURISTIC, 0.5f))
            }
        }
        else if (runtimeSeconds >= 15 * 60) {
            val introEnd = 60L
            segments.add(SkipSegment(SkipSegmentType.INTRO, 0L, introEnd, DetectionSource.METADATA_HEURISTIC, 0.5f))
            
            val creditsStart = (runtimeSeconds - 120)
            if (creditsStart > introEnd) {
                segments.add(SkipSegment(SkipSegmentType.CREDITS, creditsStart, runtimeSeconds, DetectionSource.METADATA_HEURISTIC, 0.5f))
            }
        }
    }
    
    override fun getStrategyName(): String = "Metadata-Based Heuristics"
    
    override fun isAvailable(identifier: MediaIdentifier): Boolean {
        return (prefsHelper.getTmdbApiKey().isNotEmpty() ||
                prefsHelper.getTraktApiKey().isNotEmpty() ||
                prefsHelper.getTvdbApiKey().isNotEmpty()) &&
               identifier.showName != null &&
               identifier.seasonNumber != null &&
               identifier.episodeNumber != null
    }
    
    override fun getPriority(): Int = 400
}
