package com.tvplayer.app.skipdetection

interface SkipDetectionCallback {
    fun onDetectionComplete(result: SkipDetectionResult)
    fun onDetectionFailed(errorMessage: String)
}
