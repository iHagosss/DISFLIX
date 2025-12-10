package com.tvplayer.app

import android.net.Uri
import android.util.Log

object MediaMetadataParser {

    private const val TAG = "MediaMetadataParser"

    data class ParsedMetadata(
        var showName: String? = null,
        var seasonNumber: Int? = null,
        var episodeNumber: Int? = null,
        var isTvShow: Boolean = false
    )

    private val REGEX_SE = Regex("[Ss](\\d{1,2})[Ee](\\d{1,3})")

    fun parseFromUri(uri: Uri?): ParsedMetadata {
        val metadata = ParsedMetadata()
        if (uri == null) return metadata

        val path = uri.path
        if (path.isNullOrEmpty()) return metadata

        val filenameWithExtension = path.substringAfterLast('/')
        val filename = filenameWithExtension.substringBeforeLast('.', filenameWithExtension)
        Log.d(TAG, "Parsing filename: $filename")

        val matchResult = REGEX_SE.find(filename)

        if (matchResult != null) {
            val (seasonStr, episodeStr) = matchResult.destructured

            metadata.isTvShow = true
            metadata.seasonNumber = seasonStr.toIntOrNull()
            metadata.episodeNumber = episodeStr.toIntOrNull()

            val matchStartIndex = matchResult.range.first
            var namePart = filename.substring(0, matchStartIndex)

            namePart = namePart.replace("[._-]".toRegex(), " ")
                .replace("\\s\\[.*$".toRegex(), "")
                .replace("\\s\\(.*\\)$".toRegex(), "")
                .trim()

            metadata.showName = namePart.ifEmpty { null }
            Log.d(TAG, "Extracted show name: ${metadata.showName}")

        } else {
            metadata.showName = filename
                .replace("[._-]".toRegex(), " ")
                .replace("\\.[a-zA-Z0-9]{2,4}$".toRegex(), "") 
                .trim()
                .ifEmpty { null }
        }

        return metadata
    }

    fun parseFromString(path: String?): ParsedMetadata {
        return if (path.isNullOrEmpty()) {
            ParsedMetadata()
        } else {
            parseFromUri(Uri.parse(path))
        }
    }
}
