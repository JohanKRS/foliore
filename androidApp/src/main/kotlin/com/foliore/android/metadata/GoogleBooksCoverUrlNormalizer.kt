package com.foliore.android.metadata

internal object GoogleBooksCoverUrlNormalizer {
    fun orderedCoverUrls(
        extraLarge: String?,
        large: String?,
        medium: String?,
        small: String?,
        thumbnail: String?,
        smallThumbnail: String?,
    ): List<String> {
        return listOfNotNull(extraLarge, large, medium, small, thumbnail, smallThumbnail)
            .flatMap(::candidatesFor)
            .distinct()
    }

    fun candidatesFor(rawUrl: String): List<String> {
        val normalized = rawUrl
            .replace("http://", "https://")
            .removeQueryParameter("edge")
        return listOf(
            normalized.withQueryParameter("zoom", "0"),
            normalized,
        ).distinct()
    }

    private fun String.removeQueryParameter(name: String): String {
        return replace(Regex("([?&])$name=[^&]*&?")) { match ->
            val prefix = match.groupValues[1]
            if (prefix == "?") "?" else ""
        }
            .replace("?&", "?")
            .trimEnd('?', '&')
    }

    private fun String.withQueryParameter(name: String, value: String): String {
        return if (contains(Regex("([?&])$name="))) {
            replace(Regex("([?&])$name=[^&]*")) { match ->
                "${match.groupValues[1]}$name=$value"
            }
        } else {
            val separator = if (contains("?")) "&" else "?"
            "$this$separator$name=$value"
        }
    }
}
