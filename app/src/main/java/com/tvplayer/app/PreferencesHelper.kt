package com.tvplayer.app

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_FILE = "AppPrefs"
private const val KEY_TRAKT_API = "trakt_api_key"
private const val KEY_AUDIO_DELAY = "audio_delay_ms"
private const val KEY_SUBTITLE_DELAY = "subtitle_delay_ms"
private const val KEY_INTRO_START = "intro_start_sec"
private const val KEY_INTRO_END = "intro_end_sec"
private const val KEY_RECAP_START = "recap_start_sec"
private const val KEY_RECAP_END = "recap_end_sec"
private const val KEY_CREDITS_START_OFFSET = "credits_start_offset_sec"
private const val KEY_NEXT_EPISODE_START_OFFSET = "next_episode_start_offset_sec"
private const val KEY_AUTO_SKIP_INTRO = "auto_skip_intro"
private const val KEY_AUTO_SKIP_RECAP = "auto_skip_recap"
private const val KEY_AUTO_SKIP_CREDITS = "auto_skip_credits"
private const val KEY_TMDB_API = "tmdb_api_key"
private const val KEY_TVDB_API = "tvdb_api_key"

class PreferencesHelper(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun getAudioDelay(): Int = prefs.getInt(KEY_AUDIO_DELAY, 0)
    fun getSubtitleDelay(): Int = prefs.getInt(KEY_SUBTITLE_DELAY, 0)

    fun setSubtitleDelay(value: Int) = prefs.edit().putInt(KEY_SUBTITLE_DELAY, value).apply()
    fun setAudioDelay(value: Int) = prefs.edit().putInt(KEY_AUDIO_DELAY, value).apply()

    fun getIntroStart(): Int = prefs.getInt(KEY_INTRO_START, 0)
    fun getIntroEnd(): Int = prefs.getInt(KEY_INTRO_END, 0)

    fun getRecapStart(): Int = prefs.getInt(KEY_RECAP_START, 0)
    fun getRecapEnd(): Int = prefs.getInt(KEY_RECAP_END, 0)

    fun getCreditsStart(): Int = prefs.getInt(KEY_CREDITS_START_OFFSET, 300)
    fun getNextEpisodeStart(): Int = prefs.getInt(KEY_NEXT_EPISODE_START_OFFSET, 60)

    fun isAutoSkipIntro(): Boolean = prefs.getBoolean(KEY_AUTO_SKIP_INTRO, true)
    fun isAutoSkipRecap(): Boolean = prefs.getBoolean(KEY_AUTO_SKIP_RECAP, false)
    fun isAutoSkipCredits(): Boolean = prefs.getBoolean(KEY_AUTO_SKIP_CREDITS, false)

    fun setAutoSkipIntro(value: Boolean) = prefs.edit().putBoolean(KEY_AUTO_SKIP_INTRO, value).apply()
    fun setAutoSkipRecap(value: Boolean) = prefs.edit().putBoolean(KEY_AUTO_SKIP_RECAP, value).apply()
    fun setAutoSkipCredits(value: Boolean) = prefs.edit().putBoolean(KEY_AUTO_SKIP_CREDITS, value).apply()

    fun getTmdbApiKey(): String = prefs.getString(KEY_TMDB_API, "") ?: ""
    fun getTvdbApiKey(): String = prefs.getString(KEY_TVDB_API, "") ?: ""
    fun getTraktApiKey(): String = prefs.getString(KEY_TRAKT_API, "") ?: ""

    fun setTraktApiKey(key: String) = prefs.edit().putString(KEY_TRAKT_API, key).apply()
    fun setTmdbApiKey(key: String) = prefs.edit().putString(KEY_TMDB_API, key).apply()
    fun setTvdbApiKey(key: String) = prefs.edit().putString(KEY_TVDB_API, key).apply()
}
