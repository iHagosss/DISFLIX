package com.tvplayer.app.models

data class MediaItemData(
    val chapters: List<Chapter> = emptyList()
)

data class Chapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)
