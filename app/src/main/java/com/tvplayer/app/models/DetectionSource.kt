package com.tvplayer.app.models

enum class DetectionSource {
    MANUAL,
    MANUAL_PREFERENCES,
    CACHE,
    CHAPTER,
    INTRO_SKIPPER,
    INTRO_SKIPPER_API,
    INTRO_HATER,
    INTROHATER_API,
    METADATA_HEURISTIC,
    AUDIO_FINGERPRINT,
    UNKNOWN
}
