package com.foliore.android.reader

import kotlinx.serialization.Serializable

/** Text offsets survive font, viewport and pagination changes; progression is a fallback. */
@Serializable
data class ReadingLocator(
    val href: String,
    val progression: Double = 0.0,
    val textOffset: Int = 0,
    val quote: String = "",
) {
    fun valid() = href.length in 1..2048 && !href.contains('\\') &&
        !href.startsWith('/') && !href.contains(":") && href.split('/').none { it == ".." } &&
        progression.isFinite() && progression in 0.0..1.0 && textOffset in 0..32_000_000 && quote.length <= 200
}

@Serializable
data class ReadingPosition(
    val bookId: String,
    val contentHash: String,
    val locator: ReadingLocator,
    val clock: Long,
    val deviceId: String,
)

@Serializable
data class ReadingBookmark(
    val id: String,
    val bookId: String,
    val contentHash: String,
    val locator: ReadingLocator,
    val title: String,
    val clock: Long,
    val deviceId: String,
    val deleted: Boolean = false,
)

/** How far into the whole book the last position is; kept per device, outside [ReadingState], so it never syncs. */
@Serializable
data class ReadingProgress(
    val contentHash: String,
    val fraction: Double,
    val readAt: Long,
)

@Serializable
data class ReadingState(
    val positions: List<ReadingPosition> = emptyList(),
    val bookmarks: List<ReadingBookmark> = emptyList(),
) {
    /** Independent registers keep adding/deleting a bookmark from moving the reading position. */
    fun merge(other: ReadingState): ReadingState = ReadingState(
        positions = (positions + other.positions).filter {
            validId(it.bookId) && validHash(it.contentHash) && it.locator.valid() && it.clock > 0 && it.deviceId.length <= 128
        }.groupBy { it.bookId }.values.map { records ->
            records.maxWith(compareBy<ReadingPosition> { it.clock }.thenBy { it.deviceId }.thenBy { it.toString() })
        }.sortedBy { it.bookId },
        bookmarks = (bookmarks + other.bookmarks).filter {
            validId(it.id) && validId(it.bookId) && validHash(it.contentHash) && it.locator.valid() &&
                it.title.length <= 240 && it.clock > 0 && it.deviceId.length <= 128
        }.groupBy { it.id }.values.map { records ->
            records.maxWith(compareBy<ReadingBookmark> { it.clock }.thenBy { it.deviceId }.thenBy { it.deleted }.thenBy { it.toString() })
        }.sortedBy { it.id },
    )

    fun forBooks(ids: Set<String>) = copy(positions = positions.filter { it.bookId in ids }, bookmarks = bookmarks.filter { it.bookId in ids })
    fun nextClock(now: Long) = maxOf(now, maxOf(positions.maxOfOrNull { it.clock } ?: 0, bookmarks.maxOfOrNull { it.clock } ?: 0) + 1)

    private fun validId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,128}"))
    private fun validHash(value: String) = value.matches(Regex("[a-fA-F0-9]{64}"))
}

@Serializable
data class ReaderPreferences(
    val theme: String = "paper",
    val font: String = "serif",
    val fontSize: Int = 20,
    val lineHeight: Double = 1.6,
    val margin: Int = 24,
    val mode: String = "pages",
    val publisherStyles: Boolean = false,
    val justify: Boolean = false,
    val keepScreenOn: Boolean = false,
    val brightness: Float = -1f,
    val adaptIllustrations: Boolean = true,
) {
    fun normalized() = copy(
        theme = theme.takeIf { it in setOf("paper", "light", "dark") } ?: "paper",
        font = font.takeIf { it in setOf("serif", "sans-serif", "monospace") } ?: "serif",
        fontSize = fontSize.coerceIn(12, 36), lineHeight = lineHeight.takeIf { it.isFinite() }?.coerceIn(1.2, 2.2) ?: 1.6,
        margin = margin.coerceIn(8, 64), mode = mode.takeIf { it in setOf("pages", "scroll") } ?: "pages",
        brightness = brightness.takeIf { it.isFinite() && (it == -1f || it in 0.05f..1f) } ?: -1f,
    )
}
