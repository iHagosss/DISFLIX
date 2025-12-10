package com.tvplayer.app.skipdetection

interface SkipStrategy {
    fun getStrategyName(): String
    fun isAvailable(identifier: MediaIdentifier): Boolean
    fun getPriority(): Int
    fun execute(identifier: MediaIdentifier): SkipDetectionResult
}
