package com.tvplayer.app.skipdetection

class MediaIdentifier private constructor(
    val title: String?,
    val showName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val runtimeSeconds: Long,
    val imdbId: String?,
    val tmdbId: String?,
    val traktId: String?,
    val tvdbId: String?
) {
    fun isTvShow(): Boolean {
        return seasonNumber != null && episodeNumber != null
    }

    fun getCacheKey(): String {
        return if (isTvShow() && !showName.isNullOrBlank()) {
            val sNum = seasonNumber?.toString()?.padStart(2, '0') ?: "00"
            val eNum = episodeNumber?.toString()?.padStart(2, '0') ?: "00"
            "${showName}_S${sNum}E${eNum}"
        } else {
            title ?: "unknown"
        }
    }

    class Builder {
        private var title: String? = null
        private var showName: String? = null
        private var seasonNumber: Int? = null
        private var episodeNumber: Int? = null
        private var runtimeSeconds: Long = 0
        private var imdbId: String? = null
        private var tmdbId: String? = null
        private var traktId: String? = null
        private var tvdbId: String? = null

        fun setTitle(title: String?): Builder {
            this.title = title
            return this
        }

        fun setShowName(showName: String?): Builder {
            this.showName = showName
            return this
        }

        fun setSeasonNumber(seasonNumber: Int?): Builder {
            this.seasonNumber = seasonNumber
            return this
        }

        fun setEpisodeNumber(episodeNumber: Int?): Builder {
            this.episodeNumber = episodeNumber
            return this
        }

        fun setRuntimeSeconds(runtimeSeconds: Long): Builder {
            this.runtimeSeconds = runtimeSeconds
            return this
        }

        fun setImdbId(imdbId: String?): Builder {
            this.imdbId = imdbId
            return this
        }

        fun setTmdbId(tmdbId: String?): Builder {
            this.tmdbId = tmdbId
            return this
        }

        fun setTraktId(traktId: String?): Builder {
            this.traktId = traktId
            return this
        }

        fun setTvdbId(tvdbId: String?): Builder {
            this.tvdbId = tvdbId
            return this
        }

        fun build(): MediaIdentifier {
            return MediaIdentifier(
                title, showName, seasonNumber, episodeNumber,
                runtimeSeconds, imdbId, tmdbId, traktId, tvdbId
            )
        }
    }
}
