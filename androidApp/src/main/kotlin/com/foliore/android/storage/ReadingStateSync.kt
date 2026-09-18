package com.foliore.android.storage

import com.foliore.android.reader.ReadingState
import kotlinx.serialization.json.*

internal object ReadingStateSync {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun read(manifest: JsonObject): ReadingState = manifest["readingState"]?.let { json.decodeFromJsonElement<ReadingState>(it) } ?: ReadingState()
    fun encode(state: ReadingState) = json.encodeToJsonElement(state)
    fun merge(manifests: List<JsonObject>, liveBookIds: Set<String>) = manifests.fold(ReadingState()) { state, manifest -> state.merge(read(manifest)) }.forBooks(liveBookIds)
    fun covers(previous: JsonObject, next: JsonObject, liveBookIds: Set<String>): Boolean {
        val before = read(previous).forBooks(liveBookIds)
        val after = read(next).forBooks(liveBookIds)
        return after.merge(before) == ReadingState().merge(after)
    }
}
