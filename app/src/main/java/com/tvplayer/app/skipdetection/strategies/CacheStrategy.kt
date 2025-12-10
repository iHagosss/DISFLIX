package com.tvplayer.app.skipdetection.strategies

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.tvplayer.app.models.DetectionSource
import com.tvplayer.app.models.SkipSegment
import com.tvplayer.app.skipdetection.MediaIdentifier
import com.tvplayer.app.skipdetection.SkipDetectionResult
import com.tvplayer.app.skipdetection.SkipStrategy
import java.util.concurrent.TimeUnit

class CacheStrategy(context: Context) : SkipStrategy {
    
    companion object {
        private const val PREFS_NAME = "SkipDetectionCache"
        private val CACHE_EXPIRY_MS = TimeUnit.DAYS.toMillis(30)
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    override fun execute(identifier: MediaIdentifier): SkipDetectionResult {
        val cacheKey = identifier.getCacheKey()
        val cachedJson = prefs.getString(cacheKey, null)
            ?: return SkipDetectionResult.failed(DetectionSource.CACHE, "No cached data")
        
        return try {
            val cachedData = gson.fromJson(cachedJson, CachedSkipData::class.java)
            
            if (System.currentTimeMillis() - cachedData.timestamp > CACHE_EXPIRY_MS) {
                prefs.edit().remove(cacheKey).apply()
                return SkipDetectionResult.failed(DetectionSource.CACHE, "Cache expired")
            }

            if (cachedData.segments.isEmpty()) {
                return SkipDetectionResult.failed(DetectionSource.CACHE, "Cached data is empty/invalid")
            }
      
            SkipDetectionResult.success(
                DetectionSource.CACHE,
                cachedData.confidence,
                *cachedData.segments.toTypedArray()
            )
        } catch (e: Exception) {
            SkipDetectionResult.failed(DetectionSource.CACHE, "Cache read error: ${e.message}")
        }
    }
    
    fun cacheResult(mediaIdentifier: MediaIdentifier, result: SkipDetectionResult) {
        if (!result.isSuccess || result.source == DetectionSource.CACHE) {
            return
        }
        
        val cacheKey = mediaIdentifier.getCacheKey()
        val cachedData = CachedSkipData(
            segments = result.segments,
            timestamp = System.currentTimeMillis(),
            source = result.source.name,
            confidence = result.confidence
        )
        
        val json = gson.toJson(cachedData)
        prefs.edit().putString(cacheKey, json).apply()
    }
    
    fun clearCache() {
        prefs.edit().clear().apply()
    }
    
    fun invalidateCache(mediaIdentifier: MediaIdentifier) {
        val cacheKey = mediaIdentifier.getCacheKey()
        prefs.edit().remove(cacheKey).apply()
    }
    
    override fun getStrategyName(): String = "Local Cache"
    
    override fun isAvailable(identifier: MediaIdentifier): Boolean = true
    
    override fun getPriority(): Int = 0
    
    private data class CachedSkipData(
        val segments: List<SkipSegment>,
        val timestamp: Long,
        val source: String,
        val confidence: Float
    )
}
