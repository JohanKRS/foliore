package com.foliore.android.ai

import android.content.Context
import android.util.Base64
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class CodexBridgeService(
    context: Context,
    private val aiPreferenceRepository: AiPreferenceRepository,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun healthCheck(): OpenAiResult<Unit> {
        val config = aiPreferenceRepository.codexBridgeConfig()
            ?: return OpenAiResult.Failure(code = "missing_bridge_config")
        val response = request(
            config = config,
            method = "GET",
            path = "/v1/health",
            body = null,
        )
        return if (response.body != null) {
            OpenAiResult.Success(Unit)
        } else {
            OpenAiResult.Failure(response.errorMessage, response.errorCode)
        }
    }

    fun generateMetadataPreview(
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
    ): OpenAiResult<BookMetadataDraft> {
        val config = aiPreferenceRepository.codexBridgeConfig()
            ?: return OpenAiResult.Failure(code = "missing_bridge_config")
        val response = request(
            config = config,
            method = "POST",
            path = "/v1/metadata-preview",
            body = metadataRequestBody(book, draft, prompt),
        )
        val responseBody = response.body ?: return OpenAiResult.Failure(response.errorMessage, response.errorCode)

        return runCatching {
            val payload = json.decodeFromString(BridgeMetadataResponse.serializer(), responseBody)
            OpenAiResult.Success(payload.metadata.toDraft(draft))
        }.getOrElse { OpenAiResult.Failure(it.message) }
    }

    fun generateCoverOptions(
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
        reference: AiCoverPromptRecord?,
        styles: List<AiCoverStyleRequest>,
    ): OpenAiResult<List<AiGeneratedCoverOption>> {
        val config = aiPreferenceRepository.codexBridgeConfig()
            ?: return OpenAiResult.Failure(code = "missing_bridge_config")
        val response = request(
            config = config,
            method = "POST",
            path = "/v1/cover-options",
            body = coverRequestBody(book, draft, prompt, reference, styles),
        )
        val responseBody = response.body ?: return OpenAiResult.Failure(response.errorMessage, response.errorCode)

        return runCatching {
            val payload = json.decodeFromString(BridgeCoverResponse.serializer(), responseBody)
            OpenAiResult.Success(payload.options.toGeneratedOptions(book.book.id))
        }.getOrElse { OpenAiResult.Failure(it.message) }
    }

    private fun request(
        config: CodexBridgeConfig,
        method: String,
        path: String,
        body: JsonObject?,
    ): HttpResult {
        return runCatching {
            val connection = (URL("${config.baseUrl}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8_000
                readTimeout = 180_000
                setRequestProperty("Authorization", "Bearer ${config.token}")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8))
                }
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
                val error = responseText.bridgeError()
                HttpResult(
                    errorMessage = error?.message ?: "Codex Bridge request failed: HTTP $responseCode",
                    errorCode = error?.code,
                )
            }
        }.getOrElse {
            HttpResult(
                errorMessage = it.message ?: "Codex Bridge request failed",
                errorCode = it.bridgeRequestErrorCode(),
            )
        }
    }

    private fun Throwable.bridgeRequestErrorCode(): String {
        return when (this) {
            is ConnectException,
            is UnknownHostException -> "bridge_unreachable"
            is SocketTimeoutException -> "bridge_timeout"
            else -> "bridge_request_failed"
        }
    }

    private fun metadataRequestBody(
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
    ): JsonObject {
        return buildJsonObject {
            put("prompt", prompt)
            put("draft", draft.toJson())
            putJsonObject("book") {
                put("id", book.book.id)
                put("originalFilePath", book.book.originalFilePath)
                put("coverImagePath", book.book.coverImagePath ?: book.book.coverThumbPath)
            }
        }
    }

    private fun coverRequestBody(
        book: LibraryBook,
        draft: BookMetadataDraft,
        prompt: String,
        reference: AiCoverPromptRecord?,
        styles: List<AiCoverStyleRequest>,
    ): JsonObject {
        return buildJsonObject {
            put("prompt", prompt)
            put("draft", draft.toJson())
            putJsonObject("book") {
                put("id", book.book.id)
                put("originalFilePath", book.book.originalFilePath)
                put("coverImagePath", book.book.coverImagePath ?: book.book.coverThumbPath)
            }
            if (reference != null) {
                putJsonObject("reference") {
                    put("bookId", reference.bookId)
                    put("title", reference.title)
                    put("style", reference.style)
                    put("prompt", reference.prompt)
                    put("coverImagePath", reference.coverImagePath)
                }
            }
            putJsonArray("styles") {
                styles.forEach { style ->
                    add(
                        buildJsonObject {
                            put("id", style.id)
                            put("label", style.label)
                            put("style", style.style)
                        },
                    )
                }
            }
        }
    }

    private fun BookMetadataDraft.toJson(): JsonObject {
        return buildJsonObject {
            put("title", title)
            put("subtitle", subtitle)
            putJsonArray("authors") { authors.forEach { add(it) } }
            put("sortAuthor", sortAuthor)
            put("language", language)
            put("publisher", publisher)
            put("publishedDate", publishedDate)
            put("description", description)
            putJsonArray("tags") { tags.forEach { add(it) } }
            put("isbn", isbn)
            putJsonObject("identifiers") {
                identifiers.forEach { (key, value) -> put(key, value) }
            }
            put("seriesName", seriesName)
            put("seriesIndex", seriesIndex)
        }
    }

    private fun String.bridgeError(): BridgeError? {
        return runCatching {
            json.decodeFromString(BridgeErrorResponse.serializer(), this).error
        }.getOrNull()
    }

    private fun BridgeMetadataPayload.toDraft(current: BookMetadataDraft): BookMetadataDraft {
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

    private fun BookMetadataDraft.bestIsbn(): String? {
        isbn.extractIsbn()?.let { return it }
        return identifiers.values.mapNotNull { it.extractIsbn() }.firstOrNull()
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

    private fun List<BridgeCoverOptionPayload>.toGeneratedOptions(bookId: String): List<AiGeneratedCoverOption> {
        val generationId = System.currentTimeMillis().toString()
        val outputDir = File(appContext.cacheDir, "generated-covers/$bookId/bridge-$generationId").apply {
            mkdirs()
        }
        return mapIndexed { index, option ->
            val imagePath = option.imageBase64?.let { encoded ->
                runCatching {
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    val file = File(outputDir, "${index + 1}-${option.id ?: "cover"}.png")
                    file.writeBytes(bytes)
                    file.absolutePath
                }.getOrNull()
            }
            AiGeneratedCoverOption(
                id = option.id ?: "bridge-$index-$generationId",
                label = option.label ?: "Opcao ${index + 1}",
                style = option.style.orEmpty(),
                prompt = option.prompt.orEmpty(),
                imagePath = imagePath,
            )
        }
    }

    private fun String?.cleanOptional(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.yearOnlyOrNull(): String? {
        val raw = cleanOptional() ?: return null
        return Regex("\\b(\\d{4})\\b").find(raw)?.groupValues?.get(1) ?: raw
    }

    private data class HttpResult(
        val body: String? = null,
        val errorMessage: String? = null,
        val errorCode: String? = null,
    )

    @Serializable
    private data class BridgeMetadataResponse(
        val metadata: BridgeMetadataPayload,
    )

    @Serializable
    private data class BridgeMetadataPayload(
        val title: String? = null,
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

    @Serializable
    private data class BridgeCoverResponse(
        val options: List<BridgeCoverOptionPayload> = emptyList(),
    )

    @Serializable
    private data class BridgeCoverOptionPayload(
        val id: String? = null,
        val label: String? = null,
        val style: String? = null,
        val prompt: String? = null,
        @SerialName("imageBase64") val imageBase64: String? = null,
    )

    @Serializable
    private data class BridgeErrorResponse(
        val error: BridgeError? = null,
    )

    @Serializable
    private data class BridgeError(
        val code: String? = null,
        val message: String? = null,
    )
}
