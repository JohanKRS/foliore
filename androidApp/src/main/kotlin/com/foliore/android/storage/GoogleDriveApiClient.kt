package com.foliore.android.storage

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class GoogleDriveApiClient(
    private val accessToken: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun listChildren(parentId: String): List<DriveFileMetadata> {
        val query = "'${parentId.escapeDriveQueryValue()}' in parents and trashed = false"
        return listFiles(query)
    }

    fun findChild(
        parentId: String,
        name: String,
        folder: Boolean? = null,
    ): DriveFileMetadata? {
        val query = buildString {
            append("'${parentId.escapeDriveQueryValue()}' in parents and ")
            append("name = '${name.escapeDriveQueryValue()}' and trashed = false")
            if (folder == true) {
                append(" and mimeType = '$FOLDER_MIME_TYPE'")
            } else if (folder == false) {
                append(" and mimeType != '$FOLDER_MIME_TYPE'")
            }
        }
        return listFiles(query)
            .sortedWith(compareByDescending<DriveFileMetadata> { it.modifiedTime.orEmpty() }.thenBy { it.id })
            .firstOrNull()
    }

    fun createFolder(
        parentId: String,
        name: String,
    ): DriveFileMetadata {
        val metadata = DriveCreateMetadata(
            name = name,
            mimeType = FOLDER_MIME_TYPE,
            parents = listOf(parentId),
        )
        return postJson(
            url = "$FILES_ENDPOINT?fields=id,name,mimeType,modifiedTime",
            body = metadata,
        )
    }

    fun deleteFile(fileId: String) {
        request(
            url = "$FILES_ENDPOINT/${fileId.urlPathEncoded()}",
            method = "DELETE",
        ).use { response ->
            if (response.responseCode !in 200..299 && response.responseCode != HTTP_NOT_FOUND) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
        }
    }

    fun downloadFile(
        fileId: String,
        destination: File,
    ) {
        destination.parentFile?.mkdirs()
        request(
            url = "$FILES_ENDPOINT/${fileId.urlPathEncoded()}?alt=media",
            method = "GET",
        ).use { response ->
            if (response.responseCode !in 200..299) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
            response.connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    fun readText(fileId: String): String {
        return request(
            url = "$FILES_ENDPOINT/${fileId.urlPathEncoded()}?alt=media",
            method = "GET",
        ).use { response ->
            if (response.responseCode !in 200..299) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
            response.connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    fun uploadFile(
        parentId: String,
        name: String,
        mimeType: String,
        source: File,
    ): DriveFileMetadata {
        return if (source.length() >= RESUMABLE_UPLOAD_THRESHOLD_BYTES) {
            uploadResumable(parentId, name, mimeType, source)
        } else {
            uploadMultipart(parentId, name, mimeType, source.readBytes())
        }
    }

    fun uploadText(
        parentId: String,
        name: String,
        mimeType: String,
        text: String,
    ): DriveFileMetadata {
        return uploadMultipart(parentId, name, mimeType, text.toByteArray(Charsets.UTF_8))
    }

    private fun uploadMultipart(
        parentId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
    ): DriveFileMetadata {
        val boundary = "foliore-${UUID.randomUUID()}"
        val metadata = DriveCreateMetadata(
            name = name,
            mimeType = mimeType,
            parents = listOf(parentId),
        )
        val prefix = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json.encodeToString(metadata))
            append("\r\n--$boundary\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val body = ByteArray(prefix.size + bytes.size + suffix.size)
        System.arraycopy(prefix, 0, body, 0, prefix.size)
        System.arraycopy(bytes, 0, body, prefix.size, bytes.size)
        System.arraycopy(suffix, 0, body, prefix.size + bytes.size, suffix.size)

        return request(
            url = "$UPLOAD_FILES_ENDPOINT?uploadType=multipart&fields=id,name,mimeType,modifiedTime,size",
            method = "POST",
            contentType = "multipart/related; boundary=$boundary",
            body = body,
        ).use { response ->
            if (response.responseCode !in 200..299) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
            json.decodeFromString(response.text)
        }
    }

    private fun uploadResumable(
        parentId: String,
        name: String,
        mimeType: String,
        source: File,
    ): DriveFileMetadata {
        val metadata = DriveCreateMetadata(
            name = name,
            mimeType = mimeType,
            parents = listOf(parentId),
        )
        val uploadUrl = request(
            url = "$UPLOAD_FILES_ENDPOINT?uploadType=resumable&fields=id,name,mimeType,modifiedTime,size",
            method = "POST",
            contentType = "application/json; charset=UTF-8",
            body = json.encodeToString(metadata).toByteArray(Charsets.UTF_8),
            extraHeaders = mapOf(
                "X-Upload-Content-Type" to mimeType,
                "X-Upload-Content-Length" to source.length().toString(),
            ),
        ).use { response ->
            if (response.responseCode !in 200..299) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
            response.connection.getHeaderField("Location")
                ?: error("Missing Drive resumable upload location")
        }

        return request(
            url = uploadUrl,
            method = "PUT",
            contentType = mimeType,
            bodyFile = source,
        ).use { response ->
            if (response.responseCode !in 200..299) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
            json.decodeFromString(response.text)
        }
    }

    private inline fun <reified T> getJson(url: String): T {
        return request(url = url, method = "GET").use { response ->
            if (response.responseCode !in 200..299) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
            json.decodeFromString(response.text)
        }
    }

    private fun listFiles(query: String): List<DriveFileMetadata> {
        val files = mutableListOf<DriveFileMetadata>()
        var pageToken: String? = null
        do {
            val pageTokenParameter = pageToken
                ?.let { token -> "&pageToken=${token.urlEncoded()}" }
                .orEmpty()
            val url = "$FILES_ENDPOINT?spaces=appDataFolder&pageSize=1000" +
                "&fields=nextPageToken,files(id,name,mimeType,modifiedTime,size)" +
                "&q=${query.urlEncoded()}$pageTokenParameter"
            val page = getJson<DriveFileList>(url)
            files += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return files
    }

    private inline fun <reified T> postJson(
        url: String,
        body: T,
    ): DriveFileMetadata {
        val bytes = json.encodeToString(body).toByteArray(Charsets.UTF_8)
        return request(
            url = url,
            method = "POST",
            contentType = "application/json; charset=UTF-8",
            body = bytes,
        ).use { response ->
            if (response.responseCode !in 200..299) {
                throw GoogleDriveHttpException(response.responseCode, response.errorText)
            }
            json.decodeFromString(response.text)
        }
    }

    private fun request(
        url: String,
        method: String,
        contentType: String? = null,
        body: ByteArray? = null,
        bodyFile: File? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): DriveHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            contentType?.let { setRequestProperty("Content-Type", it) }
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null || bodyFile != null) {
                doOutput = true
            }
            if (bodyFile != null) {
                setFixedLengthStreamingMode(bodyFile.length())
            } else if (body != null) {
                setFixedLengthStreamingMode(body.size)
            }
        }

        if (body != null) {
            connection.outputStream.use { output ->
                output.write(body)
            }
        } else if (bodyFile != null) {
            connection.outputStream.use { output ->
                bodyFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }

        return DriveHttpResponse(connection)
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name())
    }

    private fun String.urlPathEncoded(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun String.escapeDriveQueryValue(): String {
        return replace("\\", "\\\\").replace("'", "\\'")
    }

    @Serializable
    private data class DriveFileList(
        val nextPageToken: String? = null,
        val files: List<DriveFileMetadata> = emptyList(),
    )

    @Serializable
    private data class DriveCreateMetadata(
        val name: String,
        val mimeType: String,
        val parents: List<String>,
    )

    private class DriveHttpResponse(
        val connection: HttpURLConnection,
    ) : AutoCloseable {
        val responseCode: Int by lazy { connection.responseCode }

        val text: String by lazy {
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                errorText
            }
        }

        val errorText: String by lazy {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }

        override fun close() {
            connection.disconnect()
        }
    }

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val APP_DATA_ROOT_ID = "appDataFolder"
        private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_FILES_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
        private const val USER_AGENT = "Foliore/0.1 drive-sync"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val HTTP_NOT_FOUND = 404
        private const val RESUMABLE_UPLOAD_THRESHOLD_BYTES = 5L * 1024L * 1024L
    }
}

@Serializable
internal data class DriveFileMetadata(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String? = null,
    @SerialName("size") val sizeText: String? = null,
)

internal class GoogleDriveHttpException(
    val statusCode: Int,
    message: String?,
) : RuntimeException(message ?: "Google Drive request failed: HTTP $statusCode") {
    val isAuthorizationError: Boolean
        get() = statusCode == 401 || statusCode == 403 && message?.contains("insufficient", ignoreCase = true) == true

    val isQuotaExceeded: Boolean
        get() = statusCode == 403 && message?.contains("storageQuotaExceeded", ignoreCase = true) == true

    val isRetryable: Boolean
        get() = statusCode == 429 || statusCode in 500..599
}
