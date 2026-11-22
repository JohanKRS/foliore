package com.foliore.android.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Builds the final Responses object from a server-sent event stream.
 *
 * With store=false, the `response.completed` event can carry an empty `output` list: the items
 * and their text only arrive in the earlier `response.output_item.done` and
 * `response.output_text.*` events. The collector keeps those and fills them into the completed
 * response, so callers can read the answer the same way as a non-streamed response.
 */
internal class ResponseStreamCollector {
    private val items = mutableListOf<JsonObject>()
    private val eventCounts = linkedMapOf<String, Int>()
    private val text = StringBuilder()
    private var doneText: String? = null

    var completed: JsonObject? = null
        private set
    var failure: OpenAiResult.Failure? = null
        private set

    /** Handles one event and returns true once the stream has reached its end. */
    fun onEvent(event: JsonObject): Boolean {
        val type = event.string("type") ?: "?"
        eventCounts[type] = (eventCounts[type] ?: 0) + 1
        when (type) {
            "response.output_item.done" -> (event["item"] as? JsonObject)?.let { items += it }
            "response.output_text.delta" -> event.string("delta")?.let { text.append(it) }
            "response.output_text.done" -> event.string("text")?.let { doneText = it }
            "response.completed" -> {
                completed = (event["response"] as? JsonObject)?.withCollectedOutput() ?: JsonObject(emptyMap())
                return true
            }
            "response.failed", "response.incomplete", "error" -> {
                failure = AiErrorCodes.streamFailure(event)
                return true
            }
        }
        return false
    }

    /** Event types seen and how often, for diagnostics (no content). */
    fun eventSummary(): String = eventCounts.entries.joinToString { "${it.key} x${it.value}" }

    private fun JsonObject.withCollectedOutput(): JsonObject {
        val output = this["output"] as? JsonArray
        if (!output.isNullOrEmpty()) return this
        val extra = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        if (items.isNotEmpty()) extra["output"] = JsonArray(items.toList())
        val collectedText = doneText ?: text.toString().takeIf { it.isNotEmpty() }
        if (collectedText != null && this["output_text"] == null) extra["output_text"] = JsonPrimitive(collectedText)
        return if (extra.isEmpty()) this else JsonObject(this + extra)
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
}
