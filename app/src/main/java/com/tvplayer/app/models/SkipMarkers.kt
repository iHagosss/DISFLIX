package com.tvplayer.app.models

data class SkipMarkers(
    var intro: SkipSegment? = null,
    var recap: SkipSegment? = null,
    var credits: SkipSegment? = null,
    var nextEpisodeStart: Long? = null
) {
    val isInitialized: Boolean
        get() = true 

    fun getCreditsEnd(): Long? {
        return credits?.endTime
    }

    fun clearAll(): SkipMarkers {
        intro = null
        recap = null
        credits = null
        nextEpisodeStart = null
        return this
    }

    fun isInIntro(position: Long): Boolean {
        return intro?.let { position in it.startTime..it.endTime } ?: false
    }

    fun isInRecap(position: Long): Boolean {
        return recap?.let { position in it.startTime..it.endTime } ?: false
    }

    fun isInCredits(position: Long): Boolean {
        return credits?.let { position in it.startTime..it.endTime } ?: false
    }

    fun isAtNextEpisode(position: Long): Boolean {
        return nextEpisodeStart?.let { position >= it } ?: false
    }
}
