package com.foliore.android.storage

import java.io.IOException

internal data class DriveDeletePart(val index: Int, val status: Int, val body: String) {
    val succeeded: Boolean get() = status in 200..299 || status == 404
}

/** A 200 on the batch envelope does not mean every individual deletion succeeded. */
internal fun parseDriveDeleteBatch(contentType: String, body: String, expectedParts: Int): List<DriveDeletePart> {
    fun invalid(): Nothing = throw IOException("Incomplete or invalid Drive batch response")
    if (!contentType.startsWith("multipart/mixed", ignoreCase = true)) invalid()
    val boundaryMatch = Regex("boundary=\\s*(?:\"([^\"]+)\"|([^;\\s]+))", RegexOption.IGNORE_CASE).find(contentType) ?: invalid()
    val boundary = boundaryMatch.groupValues[1].ifEmpty { boundaryMatch.groupValues[2] }
    val normalized = body.replace("\r\n", "\n")
    if (!normalized.trimEnd().endsWith("--$boundary--")) invalid()
    val parts = normalized.split("--$boundary").drop(1).dropLast(1).map { part ->
        val sections = part.trimStart('\n').split("\n\n", limit = 2)
        if (sections.size != 2) invalid()
        val id = Regex("(?im)^Content-ID:\\s*<?response-([0-9]+)>?\\s*$").find(sections[0])
            ?.groupValues?.get(1)?.toIntOrNull() ?: invalid()
        val status = Regex("^HTTP/1\\.[01] ([0-9]{3})(?: |\\n|$)").find(sections[1])
            ?.groupValues?.get(1)?.toIntOrNull() ?: invalid()
        DriveDeletePart(id, status, sections[1].substringAfter("\n\n", ""))
    }
    if (parts.size != expectedParts || parts.map { it.index }.toSet() != (0 until expectedParts).toSet()) invalid()
    return parts.sortedBy { it.index }
}
