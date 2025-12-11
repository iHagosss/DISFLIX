package com.tvplayer.app.models

data class StreamData(
    val url: String,
    val title: String,
    val type: String = "movie", // "movie" or "series"
    val subtitles: List<Subtitle> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val behaviorHints: BehaviorHints? = null
)

data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val label: String? = null
)

data class BehaviorHints(
    val notWebReady: Boolean? = null,
    val bingeGroup: String? = null,
    val countryWhitelist: List<String>? = null,
    val proxyHeaders: Map<String, String>? = null
)
