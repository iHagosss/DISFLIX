package com.tvplayer.app.skipdetection.strategies

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
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

class IntroSkipperStrategy @JvmOverloads constructor(
    private val customEndpoint: String? = null
) : SkipStrategy {
    
    companion object {
        private const val TAG = "IntroSkipperStrategy"
        private const val API_BASE_URL = "https://api.example.com/introskipper" 
        private const val TIMEOUT_SECONDS = 8L
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    override fun execute(identifier: MediaIdentifier): SkipDetectionResult {
        if (!isAvailable(identifier)) {
            return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER, "Strategy not available")
        }
        
        return try {
            val endpoint = buildEndpoint(identifier)
                ?: return SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER, "Missing IDs")
            
            val request = Request.Builder().url(endpoint).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val jsonResponse = response.body?.string()
                val segments = parseResponse(jsonResponse)
                
                if (segments.isNotEmpty()) {
                    SkipDetectionResult.success(
                        DetectionSource.INTRO_SKIPPER, 
                        0.6f, 
                        *segments.toTypedArray()
                    )
                } else {
                    SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER, "API returned no segments")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IntroSkipper detection failed", e)
            SkipDetectionResult.failed(DetectionSource.INTRO_SKIPPER, "API error: ${e.message}")
        }
    }
    
    private fun buildEndpoint(identifier: MediaIdentifier): String? {
        val tmdbId = identifier.tmdbId
        val season = identifier.seasonNumber
        val episode = identifier.episodeNumber
        
        return if (tmdbId != null && season != null && episode != null) {
            "$API_BASE_URL/tmdb/$tmdbId/season/$season/episode/$episode"
        } else {
            null 
        }
    }

    private fun parseResponse(json: String?): List<SkipSegment> {
        val segments = mutableListOf<SkipSegment>()
        if (json.isNullOrEmpty()) return segments
        
        try {
            val root = gson.fromJson(json, JsonObject::class.java)
            root.getAsJsonArray("segments")?.let { segmentsArray ->
                for (i in 0 until segmentsArray.size()) {
                    val segment = segmentsArray[i].asJsonObject
                    val typeStr = segment.get("skipType")?.asString?.lowercase() ?: ""
                    val start = segment.get("showSkipPromptAt")?.asDouble?.toLong() ?: 0L
                    val end = segment.get("hideSkipPromptAt")?.asDouble?.toLong() ?: 0L
                    
                    if (start >= 0 && end > start) {
                        val segmentType = when {
                            typeStr.contains("intro") || typeStr.contains("opening") -> SkipSegmentType.INTRO
                            typeStr.contains("recap") -> SkipSegmentType.RECAP
                            typeStr.contains("credits") || typeStr.contains("outro") -> SkipSegmentType.CREDITS
                            else -> null
                        }
                        
                        segmentType?.let {
                            segments.add(SkipSegment(it, start, end, DetectionSource.INTRO_SKIPPER, 0.6f))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing intro-skipper response", e)
        }
        return segments
    }
    
    override fun getStrategyName(): String = "Intro-Skipper API"
    
    override fun isAvailable(identifier: MediaIdentifier): Boolean {
        return identifier.tmdbId != null || identifier.traktId != null
    }
    
    override fun getPriority(): Int = 350
}
