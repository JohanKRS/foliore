package com.foliore.android.ai

import android.content.Context
import android.util.Base64
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class OpenAiApiService(
    context: Context,
    private val aiPreferenceRepository: AiPreferenceRepository,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun generateMetadataPreview(
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
    ): OpenAiResult<BookMetadataDraft> {
        val apiKey = aiPreferenceRepository.openAiApiKey()
            ?: return OpenAiResult.Failure("Missing API key")
        val response = postJson(
            endpoint = RESPONSES_ENDPOINT,
            apiKey = apiKey,
            body = metadataRequestBody(book, draft, prompt),
        )
        val responseBody = response.body ?: return OpenAiResult.Failure(response.errorMessage)
        val outputText = responseBody.extractOutputText()
            ?: return OpenAiResult.Failure("Empty metadata response")

        return runCatching {
            val payload = json.decodeFromString(MetadataPayload.serializer(), outputText.cleanJsonPayload())
            OpenAiResult.Success(payload.toDraft(draft))
        }.getOrElse { OpenAiResult.Failure(it.message) }
    }

    fun generateCoverOptions(
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
        reference: AiCoverPromptRecord?,
        styles: List<AiCoverStyleRequest>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): OpenAiResult<List<AiGeneratedCoverOption>> {
        val apiKey = aiPreferenceRepository.openAiApiKey()
            ?: return OpenAiResult.Failure("Missing API key")
        val generationId = System.currentTimeMillis().toString()
        val outputDir = File(appContext.cacheDir, "generated-covers/${book.book.id}/$generationId").apply {
            mkdirs()
        }
        val options = mutableListOf<AiGeneratedCoverOption>()

        styles.forEachIndexed { index, style ->
            val finalPrompt = coverPrompt(
                book = book,
                draft = draft,
                basePrompt = prompt,
                reference = reference,
                style = style,
            )
            val response = postJson(
                endpoint = IMAGES_ENDPOINT,
                apiKey = apiKey,
                body = imageRequestBody(finalPrompt),
            )
            val responseBody = response.body ?: return OpenAiResult.Failure(response.errorMessage)
            val imagePayload = runCatching {
                json.decodeFromString(ImageGenerationResponse.serializer(), responseBody)
            }.getOrElse { return OpenAiResult.Failure(it.message) }
            val b64 = imagePayload.data.firstOrNull()?.b64Json
                ?: return OpenAiResult.Failure("Empty cover response")
            val imageBytes = runCatching {
                Base64.decode(b64, Base64.DEFAULT)
            }.getOrElse { return OpenAiResult.Failure(it.message) }
            val imageFile = File(outputDir, "${index + 1}-${style.id}.png")
            imageFile.writeBytes(imageBytes)
            options += AiGeneratedCoverOption(
                id = "${style.id}-$generationId",
                label = style.label,
                style = style.style,
                prompt = finalPrompt,
                imagePath = imageFile.absolutePath,
            )
            onProgress(index + 1, styles.size)
        }

        return OpenAiResult.Success(options)
    }

    private fun metadataRequestBody(
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
    ): JsonObject {
        val systemPrompt = """
            You help Foliore prepare clean EPUB metadata.
            Return only JSON matching the schema.
            Do not invent bibliographic facts. If a field is unknown, preserve the current value or return null.
            The description/about field must be elegant, brief, and spoiler-free: do not reveal endings, twists, deaths, secret identities, or late-story revelations.
            Write the description/about with literary quality in the book's general tone and register, using the title, author, existing description, tags, and language as cues. Avoid generic marketing copy, hype, melodrama, cringe, or embarrassing phrasing.
            If there is not enough reliable context to write a genuinely good description/about, preserve the current description or return null. Never fill the field with vague catalog copy, stock phrases, or a generic "invites the reader" summary.
            When the current EPUB metadata is incomplete, ambiguous, missing ISBN, or lacks enough context for the about field, use web search to verify public bibliographic facts from reliable sources before filling fields.
            Prefer publisher, author, library, ISBN registry, Open Library, WorldCat, or other reputable book pages. Do not copy copyrighted blurbs; synthesize a short original about text.
            publishedDate must be only a four-digit year when known.
            Generate natural-language fields in the EPUB language, especially description/about and tags. If the language field is present, use that language. If it is missing, infer it and return an ISO 639 language code.
            Inspect isbn and every identifier. If any identifier is an ISBN-10 or ISBN-13, return it in isbn.
        """.trimIndent()
        val userPrompt = buildString {
            appendLine(prompt)
            appendLine()
            appendLine("Current EPUB metadata:")
            appendLine("Title: ${draft.title}")
            appendLine("Subtitle: ${draft.subtitle.orEmpty()}")
            appendLine("Authors: ${draft.authors.joinToString(", ")}")
            appendLine("Sort author: ${draft.sortAuthor.orEmpty()}")
            appendLine("Series: ${draft.seriesName.orEmpty()}")
            appendLine("Series number: ${draft.seriesIndex.formatSeriesIndex()}")
            appendLine("Language: ${draft.language.orEmpty()}")
            appendLine("Publisher: ${draft.publisher.orEmpty()}")
            appendLine("Published year/date: ${draft.publishedDate.orEmpty()}")
            appendLine("ISBN: ${draft.isbn.orEmpty()}")
            appendLine("ISBN candidates from identifiers: ${draft.isbnCandidates().joinToString(", ")}")
            appendLine("Tags: ${draft.tags.joinToString(", ")}")
            appendLine("About: ${draft.description.orEmpty()}")
            appendLine("Identifiers: ${draft.identifiers.entries.joinToString { "${it.key}=${it.value}" }}")
        }

        return buildJsonObject {
            put("model", METADATA_MODEL)
            putJsonObject("reasoning") {
                put("effort", "low")
            }
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        put("type", "web_search")
                    },
                )
            }
            put("tool_choice", "auto")
            putJsonArray("input") {
                addMessage(role = "system", text = systemPrompt)
                addMessage(role = "user", text = userPrompt)
            }
            putJsonObject("text") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("name", "foliore_metadata")
                    put("strict", true)
                    put("schema", metadataSchema())
                }
            }
        }
    }

    private fun imageRequestBody(prompt: String): JsonObject {
        return buildJsonObject {
            put("model", IMAGE_MODEL)
            put("prompt", prompt)
            put("size", IMAGE_SIZE)
            put("n", 1)
        }
    }

    private fun coverPrompt(
        book: LibraryBook,
        draft: BookMetadataDraft,
        basePrompt: String,
        reference: AiCoverPromptRecord?,
        style: AiCoverStyleRequest,
    ): String {
        return buildString {
            appendLine(basePrompt)
            appendLine()
            appendLine("Book:")
            appendLine("Title: ${draft.title}")
            appendLine("Author(s): ${draft.authors.joinToString(", ").ifBlank { book.metadata.authors.joinToString(", ") }}")
            appendLine("Series: ${draft.seriesName.orEmpty()}")
            appendLine("About, spoiler-free: ${draft.description.orEmpty()}")
            appendLine("Target style for this option: ${style.style}")
            appendLine("Create one finished ebook cover image in a strict 2:3 vertical ratio.")
            appendLine("Avoid tiny illegible lettering. Prefer a polished cover-ready composition.")
            if (reference != null) {
                appendLine()
                appendLine("Series visual reference to follow in spirit, without copying:")
                appendLine("Reference book: ${reference.title}")
                appendLine("Reference style: ${reference.style}")
                appendLine("Reference prompt: ${reference.prompt}")
            }
        }
    }

    private fun postJson(
        endpoint: String,
        apiKey: String,
        body: JsonObject,
    ): HttpResult {
        return runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { output ->
                output.write(json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()

            if (responseCode in 200..299) {
                HttpResult(body = responseText)
            } else {
                val message = runCatching {
                    json.decodeFromString(OpenAiErrorResponse.serializer(), responseText).error?.message
                }.getOrNull()
                HttpResult(errorMessage = message ?: "OpenAI request failed: HTTP $responseCode")
            }
        }.getOrElse { HttpResult(errorMessage = it.message ?: "OpenAI request failed") }
    }

    private fun String.extractOutputText(): String? {
        return runCatching {
            val parsed = json.decodeFromString(ResponsesApiResponse.serializer(), this)
            parsed.outputText ?: parsed.output
                .asSequence()
                .flatMap { it.content.asSequence() }
                .firstNotNullOfOrNull { it.text }
        }.getOrNull()
    }

    private fun MetadataPayload.toDraft(current: BookMetadataDraft): BookMetadataDraft {
        val cleanAuthors = authors.mapNotNull { it.cleanOptional() }
        val cleanTags = tags.mapNotNull { it.cleanOptional() }.distinctBy { it.lowercase() }
        return BookMetadataDraft(
            title = title.cleanOptional() ?: current.title,
            subtitle = subtitle.cleanOptional(),
            authors = cleanAuthors.ifEmpty { current.authors },
            sortAuthor = sortAuthor.cleanOptional(),
            language = language.cleanOptional() ?: current.language,
            publisher = publisher.cleanOptional(),
            publishedDate = publishedDate.yearOnlyOrNull(),
            description = description.cleanOptional(),
            tags = cleanTags,
            isbn = isbn.extractIsbn() ?: current.bestIsbn(),
            identifiers = current.identifiers,
            seriesName = seriesName.cleanOptional(),
            seriesIndex = seriesIndex,
        )
    }

    private fun metadataSchema(): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            putJsonObject("properties") {
                putJsonObject("title") { put("type", "string") }
                putJsonObject("subtitle") { nullable("string") }
                putJsonObject("authors") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("sortAuthor") { nullable("string") }
                putJsonObject("language") { nullable("string") }
                putJsonObject("publisher") { nullable("string") }
                putJsonObject("publishedDate") { nullable("string") }
                putJsonObject("description") { nullable("string") }
                putJsonObject("tags") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("isbn") { nullable("string") }
                putJsonObject("seriesName") { nullable("string") }
                putJsonObject("seriesIndex") { nullable("number") }
            }
            put(
                "required",
                buildJsonArray {
                    add("title")
                    add("subtitle")
                    add("authors")
                    add("sortAuthor")
                    add("language")
                    add("publisher")
                    add("publishedDate")
                    add("description")
                    add("tags")
                    add("isbn")
                    add("seriesName")
                    add("seriesIndex")
                },
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.nullable(type: String) {
        putJsonArray("type") {
            add(type)
            add("null")
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addMessage(role: String, text: String) {
        add(
            buildJsonObject {
                put("role", role)
                putJsonArray("content") {
                    add(
                        buildJsonObject {
                            put("type", "input_text")
                            put("text", text)
                        },
                    )
                }
            },
        )
    }

    private fun String?.cleanOptional(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.yearOnlyOrNull(): String? {
        val raw = cleanOptional() ?: return null
        return Regex("\\b(\\d{4})\\b").find(raw)?.groupValues?.get(1) ?: raw
    }

    private fun String.cleanJsonPayload(): String {
        val trimmed = trim()
        return if (trimmed.startsWith("```")) {
            trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            trimmed
        }
    }

    private data class HttpResult(
        val body: String? = null,
        val errorMessage: String? = null,
    )

    @Serializable
    private data class ResponsesApiResponse(
        @SerialName("output_text") val outputText: String? = null,
        val output: List<ResponseOutputItem> = emptyList(),
        val error: OpenAiError? = null,
    )

    @Serializable
    private data class ResponseOutputItem(
        val content: List<ResponseContent> = emptyList(),
    )

    @Serializable
    private data class ResponseContent(
        val text: String? = null,
    )

    @Serializable
    private data class ImageGenerationResponse(
        val data: List<ImageData> = emptyList(),
        val error: OpenAiError? = null,
    )

    @Serializable
    private data class ImageData(
        @SerialName("b64_json") val b64Json: String? = null,
    )

    @Serializable
    private data class OpenAiErrorResponse(
        val error: OpenAiError? = null,
    )

    @Serializable
    private data class OpenAiError(
        val message: String? = null,
    )

    @Serializable
    private data class MetadataPayload(
        val title: String,
        val subtitle: String? = null,
        val authors: List<String> = emptyList(),
        val sortAuthor: String? = null,
        val language: String? = null,
        val publisher: String? = null,
        val publishedDate: String? = null,
        val description: String? = null,
        val tags: List<String> = emptyList(),
        val isbn: String? = null,
        val seriesName: String? = null,
        val seriesIndex: Double? = null,
    )

    private fun BookMetadataDraft.bestIsbn(): String? {
        isbn.extractIsbn()?.let { return it }
        return isbnCandidates().firstOrNull()
    }

    private fun BookMetadataDraft.isbnCandidates(): List<String> {
        val values = buildList {
            add(isbn.orEmpty())
            identifiers.forEach { (key, value) ->
                if (key.contains("isbn", ignoreCase = true)) add(value)
                add(value)
            }
        }
        return values.mapNotNull { it.extractIsbn() }.distinct()
    }

    private fun String?.extractIsbn(): String? {
        val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val compact = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        Regex("97[89]\\d{10}").findAll(compact)
            .map { it.value }
            .firstOrNull { it.isValidIsbn13() }
            ?.let { return it }
        Regex("\\d{9}[\\dX]").findAll(compact)
            .map { it.value }
            .firstOrNull { it.isValidIsbn10() }
            ?.let { return it }
        return null
    }

    private fun String.isValidIsbn13(): Boolean {
        if (length != 13 || any { !it.isDigit() }) return false
        val sum = take(12).mapIndexed { index, char ->
            char.digitToInt() * if (index % 2 == 0) 1 else 3
        }.sum()
        val check = (10 - (sum % 10)) % 10
        return check == last().digitToInt()
    }

    private fun String.isValidIsbn10(): Boolean {
        if (length != 10) return false
        val sum = mapIndexed { index, char ->
            val value = if (index == 9 && char == 'X') 10 else char.digitToIntOrNull() ?: return false
            value * (10 - index)
        }.sum()
        return sum % 11 == 0
    }

    private fun Double?.formatSeriesIndex(): String {
        val value = this ?: return ""
        val whole = value.toLong()
        return if (value == whole.toDouble()) {
            whole.toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }
    }

    private companion object {
        const val RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses"
        const val IMAGES_ENDPOINT = "https://api.openai.com/v1/images/generations"
        const val METADATA_MODEL = "gpt-5.5"
        const val IMAGE_MODEL = "gpt-image-1.5"
        const val IMAGE_SIZE = "1024x1536"
    }
}
