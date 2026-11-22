package com.foliore.android.ai

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Optional AI features. Text requests go to the Responses API, either through the user's
 * ChatGPT plan ("Sign in with ChatGPT") or an OpenAI API key. Cover generation uses the API key
 * until ChatGPT plan usage supports image generation (see [AiFeatureFlags]).
 */
class OpenAiApiService(
    context: Context,
    private val aiPreferenceRepository: AiPreferenceRepository,
    private val chatGptAuthService: ChatGptAuthService,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun generateMetadataPreview(
        provider: AiProvider,
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
    ): OpenAiResult<BookMetadataDraft> {
        val response = createResponse(provider, metadataRequestBody(provider, draft, prompt))
        val responseBody = when (response) {
            is OpenAiResult.Success -> response.value
            is OpenAiResult.Failure -> return response
        }
        val outputText = responseBody.outputText() ?: return OpenAiResult.Failure("Empty metadata response")
        return runCatching {
            val payload = json.decodeFromString(MetadataPayload.serializer(), outputText.cleanJsonPayload())
            OpenAiResult.Success(payload.toDraft(draft))
        }.getOrElse { OpenAiResult.Failure(it.message) }
    }

    /** Uses web search to find existing covers for this book, then downloads and checks each one. */
    suspend fun findCovers(
        provider: AiProvider,
        book: LibraryBook,
        draft: BookMetadataDraft,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): OpenAiResult<List<AiGeneratedCoverOption>> {
        val response = createResponse(provider, coverSearchRequestBody(provider, book, draft))
        val responseBody = when (response) {
            is OpenAiResult.Success -> response.value
            is OpenAiResult.Failure -> return response
        }
        val outputText = responseBody.outputText() ?: return OpenAiResult.Success(emptyList())
        val candidates = runCatching {
            json.decodeFromString(CoverSearchPayload.serializer(), outputText.cleanJsonPayload()).covers
        }.getOrElse { return OpenAiResult.Failure(it.message) }
            .filter { it.imageUrl.startsWith("https://") }
            .distinctBy { it.imageUrl }
            .take(MAX_FOUND_COVERS)

        val searchId = System.currentTimeMillis().toString()
        val outputDir = File(appContext.cacheDir, "generated-covers/${book.book.id}/found-$searchId").apply { mkdirs() }
        val seen = mutableSetOf<String>()
        val options = mutableListOf<AiGeneratedCoverOption>()
        candidates.forEachIndexed { index, candidate ->
            onProgress(index, candidates.size)
            val downloaded = runCatching {
                OpenAiHttp.downloadBytes(candidate.imageUrl, MAX_COVER_BYTES, COVER_ACCEPT)
            }.getOrNull() ?: return@forEachIndexed
            val bytes = downloaded.second
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (!seen.add(digest) || !bytes.looksLikeUsableCover()) return@forEachIndexed
            val file = File(outputDir, "${index + 1}.img").apply { writeBytes(bytes) }
            options += AiGeneratedCoverOption(
                id = "found-$searchId-${index + 1}",
                label = listOfNotNull(candidate.source.cleanOptional(), candidate.edition.cleanOptional())
                    .joinToString(" · ")
                    .ifBlank { candidate.imageUrl.substringAfter("://").substringBefore('/') },
                style = AiGeneratedCoverOption.STYLE_FOUND,
                prompt = candidate.pageUrl ?: candidate.imageUrl,
                imagePath = file.absolutePath,
            )
        }
        onProgress(candidates.size, candidates.size)
        return OpenAiResult.Success(options)
    }

    suspend fun generateCoverOptions(
        provider: AiProvider,
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
        reference: AiCoverPromptRecord?,
        styles: List<AiCoverStyleRequest>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): OpenAiResult<List<AiGeneratedCoverOption>> {
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
            val b64 = when (val image = generateImage(provider, finalPrompt)) {
                is OpenAiResult.Success -> image.value
                is OpenAiResult.Failure -> return image
            }
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

    /** Returns base64 image data for [prompt]. */
    private suspend fun generateImage(provider: AiProvider, prompt: String): OpenAiResult<String> {
        if (provider == AiProvider.CHATGPT) {
            if (!AiFeatureFlags.CHATGPT_IMAGE_GENERATION) return OpenAiResult.Failure(code = AiErrorCodes.PROVIDER_DISABLED)
            val body = buildJsonObject {
                putJsonArray("input") { addMessage(role = "user", text = prompt) }
                putJsonArray("tools") {
                    add(
                        buildJsonObject {
                            put("type", "image_generation")
                            put("size", IMAGE_SIZE)
                        },
                    )
                }
                put("tool_choice", "required")
            }
            val response = when (val result = createResponse(provider, body)) {
                is OpenAiResult.Success -> result.value
                is OpenAiResult.Failure -> return result
            }
            val image = (response["output"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("type") == "image_generation_call" }
                ?.string("result")
            return image?.let { OpenAiResult.Success(it) } ?: OpenAiResult.Failure("Empty cover response")
        }

        val apiKey = aiPreferenceRepository.openAiApiKey()
            ?: return OpenAiResult.Failure(code = AiErrorCodes.PROVIDER_DISABLED)
        val response = runCatching {
            OpenAiHttp.postJson(
                IMAGES_ENDPOINT, apiKey, json.encodeToString(JsonObject.serializer(), imageRequestBody(prompt)),
                maxResponseBytes = OpenAiHttp.MAX_IMAGE_RESPONSE_BYTES,
            )
        }.getOrElse { return OpenAiResult.Failure(it.message) }
        if (!response.isSuccess) return AiErrorCodes.failure(response.status, response.body, AiProvider.OPENAI_API)
        val b64 = runCatching {
            val data = json.parseToJsonElement(response.body).jsonObject["data"] as? JsonArray
            (data?.firstOrNull() as? JsonObject)?.string("b64_json")
        }.getOrNull()
        return b64?.let { OpenAiResult.Success(it) } ?: OpenAiResult.Failure("Empty cover response")
    }

    /** Sends a Responses API request through [provider] and returns the completed response object. */
    private suspend fun createResponse(provider: AiProvider, body: JsonObject): OpenAiResult<JsonObject> {
        return when (provider) {
            AiProvider.OPENAI_API -> {
                val apiKey = aiPreferenceRepository.openAiApiKey()
                    ?: return OpenAiResult.Failure(code = AiErrorCodes.PROVIDER_DISABLED)
                val request = JsonObject(body + ("model" to JsonPrimitive(API_TEXT_MODEL)))
                val response = runCatching {
                    OpenAiHttp.postJson(RESPONSES_ENDPOINT, apiKey, json.encodeToString(JsonObject.serializer(), request))
                }.getOrElse { return OpenAiResult.Failure(it.message) }
                if (!response.isSuccess) return AiErrorCodes.failure(response.status, response.body, AiProvider.OPENAI_API)
                runCatching { OpenAiResult.Success(json.parseToJsonElement(response.body).jsonObject) }
                    .getOrElse { OpenAiResult.Failure(it.message) }
            }
            AiProvider.CHATGPT -> createChatGptResponse(body)
        }
    }

    /**
     * ChatGPT plan usage requires store=false and stream=true, and only accepts a subset of the
     * Responses fields. Fields the route rejects as unsupported are dropped and the request is
     * retried, so new limitations degrade features instead of breaking them.
     */
    private suspend fun createChatGptResponse(body: JsonObject): OpenAiResult<JsonObject> {
        if (chatGptAuthService.account.value?.planUsageGranted == false) {
            return OpenAiResult.Failure(code = AiErrorCodes.CHATGPT_PLAN_NOT_GRANTED)
        }
        var model = chatGptAuthService.account.value?.model
        if (model == null) {
            chatGptAuthService.refreshModels()
            model = chatGptAuthService.account.value?.model
        }
        if (model == null) return OpenAiResult.Failure(code = AiErrorCodes.CHATGPT_UNAVAILABLE)

        var request = JsonObject(
            body + mapOf(
                "model" to JsonPrimitive(model),
                "store" to JsonPrimitive(false),
                "stream" to JsonPrimitive(true),
            ),
        )
        var retriedAuth = false
        repeat(MAX_CHATGPT_ATTEMPTS) {
            val token = when (val access = chatGptAuthService.accessToken(forceRefresh = retriedAuth)) {
                is ChatGptAccessToken.Ready -> access.token
                ChatGptAccessToken.SignInRequired -> return OpenAiResult.Failure(code = AiErrorCodes.CHATGPT_SIGN_IN_REQUIRED)
                ChatGptAccessToken.PlanNotGranted -> return OpenAiResult.Failure(code = AiErrorCodes.CHATGPT_PLAN_NOT_GRANTED)
                ChatGptAccessToken.Unavailable -> return OpenAiResult.Failure(code = AiErrorCodes.CHATGPT_UNAVAILABLE)
            }
            val collector = ResponseStreamCollector()
            val response = runCatching {
                OpenAiHttp.postJsonStream(
                    url = RESPONSES_ENDPOINT,
                    bearer = token,
                    body = json.encodeToString(JsonObject.serializer(), request),
                    maxStreamBytes = MAX_STREAM_BYTES,
                ) { data ->
                    val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@postJsonStream false
                    collector.onEvent(event)
                }
            }.getOrElse { return OpenAiResult.Failure(it.message) }

            if (response.isSuccess) {
                // Event types only (no content), to diagnose streams that end without an answer.
                runCatching { android.util.Log.i(LOG_TAG, "ChatGPT stream: ${collector.eventSummary()}") }
                collector.completed?.let { return OpenAiResult.Success(it) }
                return collector.failure ?: OpenAiResult.Failure("Incomplete response stream")
            }
            val failure = AiErrorCodes.failure(response.status, response.body)
            when {
                failure.code == AiErrorCodes.CHATGPT_SIGN_IN_REQUIRED && !retriedAuth -> retriedAuth = true
                failure.code == AiErrorCodes.UNSUPPORTED_CAPABILITY -> {
                    request = request.withoutUnsupported(failure.param) ?: return failure
                }
                else -> return failure
            }
        }
        return OpenAiResult.Failure("Request failed")
    }

    /** Drops the top-level field named by an unsupported-capability error, when it is optional. */
    private fun JsonObject.withoutUnsupported(param: String?): JsonObject? {
        val field = param?.substringBefore('.')?.substringBefore('[')?.takeIf { it in DROPPABLE_FIELDS } ?: return null
        if (field !in this) return null
        val dropped = if (field == "tools") setOf("tools", "tool_choice") else setOf(field)
        return JsonObject(filterKeys { it !in dropped })
    }

    private fun metadataRequestBody(
        provider: AiProvider,
        draft: BookMetadataDraft,
        prompt: String,
    ): JsonObject {
        val instructions = """
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
            Answer with a single JSON object with these keys: title, subtitle, authors, sortAuthor, language, publisher, publishedDate, description, tags, isbn, seriesName, seriesIndex.
        """.trimIndent()
        val userPrompt = buildString {
            appendLine(prompt)
            appendLine()
            appendLine("Current EPUB metadata:")
            appendLine(draft.describe())
        }
        return responsesBody(provider, instructions, userPrompt, "foliore_metadata", metadataSchema())
    }

    private fun coverSearchRequestBody(
        provider: AiProvider,
        book: LibraryBook,
        draft: BookMetadataDraft,
    ): JsonObject {
        val instructions = """
            You help Foliore find existing front-cover images for one specific book, for the reader's personal library.
            Use web search. Return only JSON matching the schema, with at most $MAX_FOUND_COVERS covers, best first.
            Each imageUrl must be a direct HTTPS link to a JPEG, PNG or WebP image of the front cover, not a web page.
            Prefer large images (at least 600 pixels tall) over thumbnails.
            Prefer the edition that matches the title, author, language, publisher and ISBN; then other editions in the same language; then the original edition.
            Prefer publisher, author, library, Open Library, Wikimedia Commons and established bookseller sources.
            Only return URLs that appeared in your search results or the pages you opened. Never guess or construct URLs.
            source is the site name. edition briefly names the edition, publisher or year when known.
            Answer with a single JSON object: {"covers":[{"imageUrl":"...","pageUrl":"...","source":"...","edition":"..."}]}.
        """.trimIndent()
        val userPrompt = buildString {
            appendLine("Find cover images for this book.")
            appendLine(draft.describe(fallbackAuthors = book.metadata.authors))
        }
        return responsesBody(provider, instructions, userPrompt, "foliore_cover_search", coverSearchSchema())
    }

    private fun responsesBody(
        provider: AiProvider,
        instructions: String,
        userPrompt: String,
        schemaName: String,
        schema: JsonObject,
    ): JsonObject = buildJsonObject {
        put("instructions", instructions)
        putJsonArray("input") { addMessage(role = "user", text = userPrompt) }
        putJsonArray("tools") { add(buildJsonObject { put("type", "web_search") }) }
        put("tool_choice", "auto")
        if (provider == AiProvider.OPENAI_API) {
            putJsonObject("reasoning") { put("effort", "low") }
        }
        putJsonObject("text") {
            putJsonObject("format") {
                put("type", "json_schema")
                put("name", schemaName)
                put("strict", true)
                put("schema", schema)
            }
        }
    }

    private fun BookMetadataDraft.describe(fallbackAuthors: List<String> = emptyList()): String = buildString {
        appendLine("Title: $title")
        appendLine("Subtitle: ${subtitle.orEmpty()}")
        appendLine("Authors: ${authors.ifEmpty { fallbackAuthors }.joinToString(", ")}")
        appendLine("Sort author: ${sortAuthor.orEmpty()}")
        appendLine("Series: ${seriesName.orEmpty()}")
        appendLine("Series number: ${seriesIndex.formatSeriesIndex()}")
        appendLine("Language: ${language.orEmpty()}")
        appendLine("Publisher: ${publisher.orEmpty()}")
        appendLine("Published year/date: ${publishedDate.orEmpty()}")
        appendLine("ISBN: ${isbn.orEmpty()}")
        appendLine("ISBN candidates from identifiers: ${isbnCandidates().joinToString(", ")}")
        appendLine("Tags: ${tags.joinToString(", ")}")
        appendLine("About: ${description.orEmpty()}")
        appendLine("Identifiers: ${identifiers.entries.joinToString { "${it.key}=${it.value}" }}")
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

    /** The first message text in a Responses object (the SDK's output_text is not in raw JSON). */
    private fun JsonObject.outputText(): String? {
        string("output_text")?.let { return it }
        // Only the assistant message's output_text parts: reasoning items can carry text too.
        return (this["output"] as? JsonArray).orEmpty()
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .filter { (it.string("type") ?: "message") == "message" }
            .flatMap { (it["content"] as? JsonArray).orEmpty().asSequence() }
            .mapNotNull { it as? JsonObject }
            .filter { (it.string("type") ?: "output_text") == "output_text" }
            .mapNotNull { it.string("text") }
            .lastOrNull { it.isNotBlank() }
    }

    private fun ByteArray.looksLikeUsableCover(): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(this, 0, size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width < MIN_COVER_WIDTH || height < MIN_COVER_HEIGHT) return false
        val ratio = height.toFloat() / width
        return ratio in 1.15f..2.0f
    }

    private fun coverSearchSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("covers") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        putJsonObject("imageUrl") { put("type", "string") }
                        putJsonObject("pageUrl") { nullable("string") }
                        putJsonObject("source") { nullable("string") }
                        putJsonObject("edition") { nullable("string") }
                    }
                    put(
                        "required",
                        buildJsonArray {
                            add("imageUrl")
                            add("pageUrl")
                            add("source")
                            add("edition")
                        },
                    )
                }
            }
        }
        put("required", buildJsonArray { add("covers") })
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

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


    @Serializable
    private data class CoverSearchPayload(val covers: List<CoverCandidate> = emptyList())

    @Serializable
    private data class CoverCandidate(
        val imageUrl: String,
        val pageUrl: String? = null,
        val source: String? = null,
        val edition: String? = null,
    )

    private companion object {
        const val RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses"
        private const val LOG_TAG = "FolioreAi"
        const val IMAGES_ENDPOINT = "https://api.openai.com/v1/images/generations"
        const val API_TEXT_MODEL = "gpt-5.5"
        const val IMAGE_MODEL = "gpt-image-1.5"
        const val IMAGE_SIZE = "1024x1536"
        const val MAX_CHATGPT_ATTEMPTS = 4
        const val MAX_STREAM_BYTES = 48L * 1024 * 1024
        const val MAX_FOUND_COVERS = 6
        const val MAX_COVER_BYTES = 12L * 1024 * 1024
        const val MIN_COVER_WIDTH = 240
        const val MIN_COVER_HEIGHT = 340
        const val COVER_ACCEPT = "image/jpeg,image/png,image/webp;q=0.9,image/*;q=0.5"
        val DROPPABLE_FIELDS = setOf("reasoning", "text", "tools", "tool_choice", "instructions")
    }
}
