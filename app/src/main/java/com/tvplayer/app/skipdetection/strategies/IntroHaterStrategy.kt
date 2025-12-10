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

class IntroHaterStrategy : SkipStrategy {
    
    companion object {
        private const val TAG = "IntroHaterStrategy"
        private const val API_BASE_URL = "https://api.example.com/introhater" 
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
            return SkipDetectionResult.failed(DetectionSource.INTRO_HATER, "Strategy not available")
        }
        
        return try {
            val endpoint = buildEndpoint(identifier)
                ?: return SkipDetectionResult.failed(DetectionSource.INTRO_HATER, "Missing episode data")
            
            val request = Request.Builder().url(endpoint).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val jsonResponse = response.body?.string()
                val segments = parseResponse(jsonResponse)
                
                if (segments.isNotEmpty()) {
                    SkipDetectionResult.success(
                        DetectionSource.INTRO_HATER, 
                        0.7f,
                        *segments.toTypedArray()
                    )
                } else {
                    SkipDetectionResult.failed(DetectionSource.INTRO_HATER, "API returned no segments")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IntroHater detection failed", e)
            SkipDetectionResult.failed(DetectionSource.INTRO_HATER, "API error: ${e.message}")
        }
    }
    
    private fun buildEndpoint(identifier: MediaIdentifier): String? {
        val showName = identifier.showName ?: return null
        val season = identifier.seasonNumber ?: return null
        val episode = identifier.episodeNumber ?: return null
        return "$API_BASE_URL/skip_segments?show=$showName&s=$season&e=$episode"
    }

    private fun parseResponse(json: String?): List<SkipSegment> {
        val segments = mutableListOf<SkipSegment>()
        if (json.isNullOrEmpty()) return segments
        
        try {
            val root = gson.fromJson(json, JsonObject::class.java)
            root.getAsJsonArray("segments")?.let { segmentsArray ->
                for (i in 0 until segmentsArray.size()) {
                    val segment = segmentsArray[i].asJsonObject
                    val type = segment.get("type")?.asString?.lowercase() ?: ""
                    val start = segment.get("start")?.asInt ?: 0
                    val end = segment.get("end")?.asInt ?: 0
                    
                    if (start >= 0 && end > start) {
                        val segmentType = when {
                            type.contains("intro") || type.contains("opening") -> SkipSegmentType.INTRO
                            type.contains("recap") -> SkipSegmentType.RECAP
                            type.contains("credits") || type.contains("outro") -> SkipSegmentType.CREDITS
                            else -> null
                        }
                        
                        segmentType?.let {
                            segments.add(SkipSegment(it, start.toLong(), end.toLong(), DetectionSource.INTRO_HATER, 0.7f))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
        }
        return segments
    }
    
    override fun getStrategyName(): String = "IntroHater Community API"
    
    override fun isAvailable(identifier: MediaIdentifier): Boolean {
        return identifier.showName != null && identifier.seasonNumber != null && identifier.episodeNumber != null
    }
    
    override fun getPriority(): Int = 250
}
