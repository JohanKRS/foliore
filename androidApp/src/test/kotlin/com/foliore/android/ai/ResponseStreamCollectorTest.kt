package com.foliore.android.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseStreamCollectorTest {
    private fun event(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    private fun ResponseStreamCollector.feed(vararg raw: String): Boolean = raw.map { onEvent(event(it)) }.last()

    @Test
    fun fillsAnEmptyCompletedOutputFromTheStreamedItems() {
        val collector = ResponseStreamCollector()
        val done = collector.feed(
            """{"type":"response.created","response":{"id":"r1","output":[]}}""",
            """{"type":"response.output_item.done","item":{"type":"web_search_call","id":"ws1","status":"completed"}}""",
            """{"type":"response.output_text.delta","delta":"{\"title\":"}""",
            """{"type":"response.output_text.delta","delta":"\"Dune\"}"}""",
            """{"type":"response.output_text.done","text":"{\"title\":\"Dune\"}"}""",
            """{"type":"response.output_item.done","item":{"type":"message","role":"assistant","content":[{"type":"output_text","text":"{\"title\":\"Dune\"}"}]}}""",
            """{"type":"response.completed","response":{"id":"r1","status":"completed","output":[]}}""",
        )

        assertTrue(done)
        val completed = assertNotNull(collector.completed)
        assertEquals(2, (completed["output"] as JsonArray).size)
        assertEquals("{\"title\":\"Dune\"}", (completed["output_text"] as JsonPrimitive).content)
        assertNull(collector.failure)
    }

    @Test
    fun usesTheDeltasWhenNoItemsOrDoneTextArrive() {
        val collector = ResponseStreamCollector()
        collector.feed(
            """{"type":"response.output_text.delta","delta":"Hel"}""",
            """{"type":"response.output_text.delta","delta":"lo"}""",
            """{"type":"response.completed","response":{"id":"r1"}}""",
        )

        assertEquals("Hello", (collector.completed!!["output_text"] as JsonPrimitive).content)
    }

    @Test
    fun keepsACompletedResponseThatAlreadyHasOutput() {
        val collector = ResponseStreamCollector()
        collector.feed(
            """{"type":"response.output_text.delta","delta":"ignored"}""",
            """{"type":"response.completed","response":{"output":[{"type":"message","content":[{"type":"output_text","text":"kept"}]}]}}""",
        )

        val completed = collector.completed!!
        assertNull(completed["output_text"])
        assertEquals(1, (completed["output"] as JsonArray).size)
    }

    @Test
    fun stopsOnAFailedResponse() {
        val collector = ResponseStreamCollector()
        assertFalse(collector.onEvent(event("""{"type":"response.in_progress"}""")))
        assertTrue(
            collector.onEvent(
                event("""{"type":"response.failed","response":{"error":{"code":"rate_limit_exceeded","message":"slow"}}}"""),
            ),
        )
        assertNull(collector.completed)
        assertEquals(AiErrorCodes.RATE_LIMITED, collector.failure?.code)
    }
}
