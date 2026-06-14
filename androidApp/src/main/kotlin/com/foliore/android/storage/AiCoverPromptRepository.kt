package com.foliore.android.storage

import android.content.Context
import com.foliore.android.ai.AiCoverPromptRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AiCoverPromptRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun records(): List<AiCoverPromptRecord> {
        val raw = preferences.getString(KEY_SELECTED_PROMPTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(AiCoverPromptRecord.serializer()), raw)
        }.getOrDefault(emptyList()).normalized()
    }

    fun save(record: AiCoverPromptRecord): List<AiCoverPromptRecord> {
        return replaceAll(records() + record)
    }

    fun replaceAll(records: List<AiCoverPromptRecord>): List<AiCoverPromptRecord> {
        val nextRecords = records.normalized()
        preferences.edit()
            .putString(
                KEY_SELECTED_PROMPTS,
                json.encodeToString(ListSerializer(AiCoverPromptRecord.serializer()), nextRecords),
            )
            .apply()
        return nextRecords
    }

    private fun List<AiCoverPromptRecord>.normalized(): List<AiCoverPromptRecord> {
        return groupBy { it.id }
            .values
            .map { records -> records.maxBy { it.selectedAtEpochMillis } }
            .sortedWith(compareBy<AiCoverPromptRecord> { it.selectedAtEpochMillis }.thenBy { it.id })
            .takeLast(MAX_RECORDS)
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_ai_cover_prompts"
        const val KEY_SELECTED_PROMPTS = "selected_prompts"
        const val MAX_RECORDS = 250
    }
}
