package com.foliore.android.reader

import android.content.Context
import com.foliore.android.storage.DeviceIdProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ReadingStateRepository(context: Context) {
    private val preferences = context.getSharedPreferences("foliore_reading", Context.MODE_PRIVATE)
    private val deviceId = DeviceIdProvider(context).getDeviceId()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun state(): ReadingState = synchronized(lock) {
        preferences.getString("state", null)?.let { json.decodeFromString<ReadingState>(it) } ?: ReadingState()
    }

    fun merge(incoming: ReadingState): ReadingState = synchronized(lock) {
        val merged = state().merge(incoming)
        preferences.edit().putString("state", json.encodeToString(merged)).apply()
        merged
    }

    fun position(bookId: String, contentHash: String) = state().positions.firstOrNull { it.bookId == bookId && it.contentHash == contentHash }
    fun bookmarks(bookId: String, contentHash: String) = state().bookmarks.filter { it.bookId == bookId && it.contentHash == contentHash && !it.deleted }.sortedBy { it.clock }

    fun savePosition(bookId: String, contentHash: String, locator: ReadingLocator) = synchronized(lock) {
        require(locator.valid())
        val state = state()
        if (state.positions.any { it.bookId == bookId && it.contentHash == contentHash && it.locator == locator }) return@synchronized
        merge(ReadingState(positions = listOf(ReadingPosition(bookId, contentHash, locator, state.nextClock(System.currentTimeMillis()), deviceId))))
    }

    fun addBookmark(bookId: String, contentHash: String, locator: ReadingLocator, title: String) = synchronized(lock) {
        require(locator.valid())
        val current = state()
        if (current.bookmarks.any { !it.deleted && it.bookId == bookId && it.contentHash == contentHash && it.locator.href == locator.href && it.locator.textOffset == locator.textOffset }) return@synchronized
        merge(ReadingState(bookmarks = listOf(ReadingBookmark(UUID.randomUUID().toString(), bookId, contentHash, locator, title.take(240), current.nextClock(System.currentTimeMillis()), deviceId))))
    }

    fun deleteBookmark(id: String) = synchronized(lock) {
        val current = state()
        val bookmark = current.bookmarks.firstOrNull { it.id == id } ?: return@synchronized
        merge(ReadingState(bookmarks = listOf(bookmark.copy(deleted = true, clock = current.nextClock(System.currentTimeMillis()), deviceId = deviceId))))
    }

    fun progress(): Map<String, ReadingProgress> = runCatching {
        preferences.getString("progress", null)?.let { json.decodeFromString<Map<String, ReadingProgress>>(it) }
    }.getOrNull().orEmpty()

    fun saveProgress(bookId: String, contentHash: String, fraction: Double) = synchronized(lock) {
        if (!fraction.isFinite()) return@synchronized
        val next = progress() + (bookId to ReadingProgress(contentHash, fraction.coerceIn(0.0, 1.0), System.currentTimeMillis()))
        preferences.edit().putString("progress", json.encodeToString(next)).apply()
    }

    fun removeBook(bookId: String) = synchronized(lock) {
        val next = state().let { it.copy(positions = it.positions.filterNot { p -> p.bookId == bookId }, bookmarks = it.bookmarks.filterNot { b -> b.bookId == bookId }) }
        preferences.edit().putString("state", json.encodeToString(next)).putString("progress", json.encodeToString(progress() - bookId)).apply()
    }

    fun copyBook(from: String, to: String) = synchronized(lock) {
        val current = state()
        merge(ReadingState(
            positions = current.positions.filter { it.bookId == from }.map { it.copy(bookId = to) },
            bookmarks = current.bookmarks.filter { it.bookId == from }.map { it.copy(bookId = to, id = UUID.nameUUIDFromBytes((to + it.id).toByteArray()).toString()) },
        ))
        val progress = progress()
        progress[from]?.let { preferences.edit().putString("progress", json.encodeToString(progress + (to to it))).apply() }
    }

    fun preferences(): ReaderPreferences = runCatching { preferences.getString("appearance", null)?.let { json.decodeFromString<ReaderPreferences>(it) } }.getOrNull()?.normalized() ?: ReaderPreferences()
    fun savePreferences(value: ReaderPreferences) { preferences.edit().putString("appearance", json.encodeToString(value.normalized())).apply() }

    companion object { private val lock = Any() }
}
