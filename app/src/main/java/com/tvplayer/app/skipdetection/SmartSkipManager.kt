package com.tvplayer.app.skipdetection

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import com.tvplayer.app.PreferencesHelper
import com.tvplayer.app.models.DetectionSource
import com.tvplayer.app.skipdetection.strategies.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SmartSkipManager(
    private val context: Context,
    private val prefsHelper: PreferencesHelper
) {
    
    private val TAG = "SmartSkipManager"
    private val DETECTION_TIMEOUT_MS = 10000L
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheStrategy = CacheStrategy(context)
    private val strategies = CopyOnWriteArrayList<SkipStrategy>()
    private var chapterStrategy: ChapterStrategy? = null
    
    init {
        initializeStrategies()
    }
    
    private fun initializeStrategies() {
        strategies.add(ManualPreferenceStrategy(prefsHelper))
        strategies.add(IntroHaterStrategy())
        strategies.add(IntroSkipperStrategy())
        strategies.add(MetadataHeuristicStrategy(prefsHelper))
        strategies.add(AudioFingerprintStrategy())
        sortStrategies()
        Log.d(TAG, "Initialized strategies. Count: ${strategies.size}")
    }

    private fun sortStrategies() {
        strategies.sortBy { it.getPriority() }
    }
    
    fun addChapterStrategy(strategy: ChapterStrategy) {
        strategies.removeAll { it is ChapterStrategy }
        this.chapterStrategy = strategy
        strategies.add(strategy)
        sortStrategies()
        Log.d(TAG, "Added new ChapterStrategy.")
    }

    fun rebindPlayer(newPlayer: Player?) {
        chapterStrategy?.rebindPlayer(newPlayer)
        Log.d(TAG, "Rebinding player in ChapterStrategy.")
    }

    fun detectSkipSegmentsAsync(
        mediaIdentifier: MediaIdentifier,
        callback: SkipDetectionCallback
    ) {
        detectSkipMarkers(mediaIdentifier, callback)
    }

    fun detectSkipMarkers(mediaIdentifier: MediaIdentifier, callback: SkipDetectionCallback) {
        val cachedResult = cacheStrategy.execute(mediaIdentifier)
        if (cachedResult.isSuccess) {
            Log.d(TAG, "Using cached skip markers for: ${mediaIdentifier.getCacheKey()}")
            mainHandler.post { callback.onDetectionComplete(cachedResult) }
            return
        }
        
        Log.d(TAG, "Cache miss, attempting detection for: ${mediaIdentifier.getCacheKey()}")
        executorService.execute {
            val bestResult = runDetectionStrategies(mediaIdentifier)
            
            if (bestResult.isSuccess) {
                cacheStrategy.cacheResult(mediaIdentifier, bestResult)
                Log.d(TAG, "Detection successful using: ${bestResult.source.name} (confidence: ${bestResult.confidence})")
            } else {
                Log.d(TAG, "All detection methods failed, using manual preferences.")
            }
            
            mainHandler.post { callback.onDetectionComplete(bestResult) }
        }
    }
    
    private fun runDetectionStrategies(mediaIdentifier: MediaIdentifier): SkipDetectionResult {
        val bestResult = AtomicReference<SkipDetectionResult>(null)
        val completed = AtomicBoolean(false)
        val futures = mutableListOf<Future<*>>()
        
        for (strategy in strategies) {
            if (!strategy.isAvailable(mediaIdentifier)) {
                Log.d(TAG, "Strategy not available: ${strategy.getStrategyName()}")
                continue
            }
            
            val future = executorService.submit {
                if (completed.get()) return@submit
                
                try {
                    Log.d(TAG, "Trying strategy: ${strategy.getStrategyName()}")
                    val result = strategy.execute(mediaIdentifier)
                    
                    if (result.isSuccess) {
                        synchronized(bestResult) {
                            val current = bestResult.get()
                            if (current == null || result.confidence > current.confidence) {
                                bestResult.set(result)
                                Log.d(TAG, "New best: ${strategy.getStrategyName()} (${result.confidence})")
                                
                                if (result.confidence >= 0.85f) {
                                    completed.set(true)
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Strategy failed: ${strategy.getStrategyName()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in strategy: ${strategy.getStrategyName()}", e)
                }
            }
            futures.add(future)
        }
        
        val startTime = System.currentTimeMillis()
        while (!completed.get() && 
               (System.currentTimeMillis() - startTime) < DETECTION_TIMEOUT_MS &&
               !allFuturesCompleted(futures)) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        
        for (future in futures) {
            if (!future.isDone) {
                future.cancel(true)
            }
        }
        
        var result = bestResult.get()
        if (result == null) {
            val fallback = ManualPreferenceStrategy(prefsHelper)
            result = fallback.execute(mediaIdentifier)
            Log.d(TAG, "No successful strategy; returning ManualPreference fallback.")
        }
        
        return result!!
    }
    
    private fun allFuturesCompleted(futures: List<Future<*>>): Boolean {
        return futures.all { it.isDone }
    }
    
    fun clearCache() {
        cacheStrategy.clearCache()
    }
    
    fun invalidateCache(mediaIdentifier: MediaIdentifier) {
        cacheStrategy.invalidateCache(mediaIdentifier)
    }
    
    fun shutdown() {
        executorService.shutdown()
    }
}
