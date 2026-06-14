package com.foliore.shared.epub

import com.foliore.shared.domain.publicationYearOrNull

class EpubParser {
    fun parse(archive: EpubArchive): EpubParseResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val mimetype = archive.readText("mimetype")?.trim()
        if (mimetype != null && mimetype != EPUB_MIME_TYPE) {
            warnings += "Unexpected mimetype: $mimetype"
        }

        val containerXml = archive.readText(CONTAINER_PATH)
        if (containerXml == null) {
            errors += "Missing META-INF/container.xml"
            return failed(errors)
        }

        val opfPath = findRootfilePath(containerXml)
        if (opfPath == null) {
            errors += "No OPF rootfile declared in container.xml"
            return failed(errors)
        }

        val opfXml = archive.readText(opfPath)
        if (opfXml == null) {
            errors += "OPF file not found: $opfPath"
            return failed(errors, opfPath = opfPath)
        }

        val packageAttrs = firstTagAttributes(opfXml, "package")
        val manifestItems = parseManifestItems(opfXml, opfPath)
        val spineIds = parseSpineIds(opfXml)
        val coverItem = findCoverItem(opfXml, manifestItems)
        val navItem = manifestItems.firstOrNull { it.properties.containsToken("nav") }
        val ncxItem = manifestItems.firstOrNull {
            it.mediaType == "application/x-dtbncx+xml" || it.href.endsWith(".ncx", ignoreCase = true)
        }

        val identifiers = parseIdentifiers(opfXml)
        val metadata = ParsedEpubMetadata(
            title = tagText(opfXml, "title"),
            authors = tagTexts(opfXml, "creator"),
            language = tagText(opfXml, "language"),
            identifier = identifiers.values.firstOrNull(),
            identifiers = identifiers,
            isbn = identifiers.values.firstNotNullOfOrNull { it.extractIsbn() },
            description = tagText(opfXml, "description"),
            publisher = tagText(opfXml, "publisher"),
            publishedDate = publicationYear(opfXml),
        )

        val technicalInfo = ParsedEpubTechnicalInfo(
            epubVersion = packageAttrs?.let { attr(it, "version") },
            opfPath = opfPath,
            navPath = navItem?.resolvedHref,
            ncxPath = ncxItem?.resolvedHref,
            coverHref = coverItem?.resolvedHref,
            packageIdentifier = metadata.identifier,
            hasNav = navItem != null,
            hasNcx = ncxItem != null,
            manifestItemCount = manifestItems.size.toLong(),
            spineItemCount = spineIds.size.toLong(),
            warnings = warnings,
            errors = errors,
        )

        return EpubParseResult(
            isReadable = errors.isEmpty(),
            metadata = metadata,
            technicalInfo = technicalInfo,
            coverPath = coverItem?.resolvedHref,
        )
    }

    private fun failed(
        errors: List<String>,
        opfPath: String = "",
    ): EpubParseResult {
        return EpubParseResult(
            isReadable = false,
            metadata = ParsedEpubMetadata(
                title = null,
                authors = emptyList(),
                language = null,
                identifier = null,
                identifiers = emptyMap(),
                isbn = null,
                description = null,
                publisher = null,
                publishedDate = null,
            ),
            technicalInfo = ParsedEpubTechnicalInfo(
                epubVersion = null,
                opfPath = opfPath,
                navPath = null,
                ncxPath = null,
                coverHref = null,
                packageIdentifier = null,
                hasNav = false,
                hasNcx = false,
                manifestItemCount = 0,
                spineItemCount = 0,
                warnings = emptyList(),
                errors = errors,
            ),
            coverPath = null,
        )
    }

    private fun findRootfilePath(containerXml: String): String? {
        val rootfileRegex = Regex("<rootfile\\b([^>]*)>", RegexOption.IGNORE_CASE)
        return rootfileRegex.findAll(containerXml)
            .mapNotNull { attr(it.groupValues[1], "full-path") }
            .firstOrNull()
    }

    private fun parseManifestItems(opfXml: String, opfPath: String): List<ManifestItem> {
        val itemRegex = Regex("<item\\b([^>]*)/?>", RegexOption.IGNORE_CASE)
        return itemRegex.findAll(opfXml).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val id = attr(attrs, "id") ?: return@mapNotNull null
            val href = attr(attrs, "href") ?: return@mapNotNull null
            ManifestItem(
                id = id,
                href = href,
                resolvedHref = resolveRelative(opfPath, href),
                mediaType = attr(attrs, "media-type"),
                properties = attr(attrs, "properties").orEmpty(),
            )
        }.toList()
    }

    private fun parseSpineIds(opfXml: String): List<String> {
        val itemRefRegex = Regex("<itemref\\b([^>]*)/?>", RegexOption.IGNORE_CASE)
        return itemRefRegex.findAll(opfXml)
            .mapNotNull { attr(it.groupValues[1], "idref") }
            .toList()
    }

    private fun findCoverItem(opfXml: String, manifestItems: List<ManifestItem>): ManifestItem? {
        manifestItems.firstOrNull { it.properties.containsToken("cover-image") }?.let { return it }

        val metaRegex = Regex("<meta\\b([^>]*)/?>", RegexOption.IGNORE_CASE)
        val legacyCoverId = metaRegex.findAll(opfXml)
            .firstOrNull {
                attr(it.groupValues[1], "name").equals("cover", ignoreCase = true)
            }
            ?.let { attr(it.groupValues[1], "content") }

        if (legacyCoverId != null) {
            manifestItems.firstOrNull { it.id == legacyCoverId }?.let { return it }
        }

        return manifestItems.firstOrNull {
            it.mediaType?.startsWith("image/") == true &&
                it.href.contains("cover", ignoreCase = true)
        }
    }

    private fun firstTagAttributes(xml: String, localName: String): String? {
        return Regex("<(?:[A-Za-z0-9_-]+:)?$localName\\b([^>]*)>", RegexOption.IGNORE_CASE)
            .find(xml)
            ?.groupValues
            ?.get(1)
    }

    private fun tagText(xml: String, localName: String): String? {
        return tagTexts(xml, localName).firstOrNull()
    }

    private fun parseIdentifiers(xml: String): Map<String, String> {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val regex = Regex(
            "<(?:[A-Za-z0-9_-]+:)?identifier\\b([^>]*)>(.*?)</(?:[A-Za-z0-9_-]+:)?identifier>",
            options,
        )
        val identifiers = linkedMapOf<String, String>()
        regex.findAll(xml).forEach { match ->
            val attrs = match.groupValues[1]
            val value = match.groupValues[2].cleanXmlText().takeIf { it.isNotBlank() } ?: return@forEach
            val scheme = attr(attrs, "opf:scheme") ?: attr(attrs, "scheme")
            val id = attr(attrs, "id")
            val baseKey = when {
                value.extractIsbn() != null -> "isbn"
                scheme != null -> scheme.toIdentifierKey()
                id != null -> id.toIdentifierKey()
                else -> "identifier"
            }
            identifiers[baseKey.uniqueIdentifierKey(identifiers)] = value
        }
        return identifiers
    }

    private fun publicationYear(opfXml: String): String? {
        val candidates = dateTagCandidates(opfXml) + metaDateCandidates(opfXml)
        return candidates.firstYear(DateKind.PUBLICATION)
            ?: candidates.firstYear(DateKind.NEUTRAL)
    }

    private fun List<DateCandidate>.firstYear(kind: DateKind): String? {
        return firstNotNullOfOrNull { candidate ->
            if (candidate.kind == kind) candidate.value.publicationYearOrNull() else null
        }
    }

    private fun dateTagCandidates(xml: String): List<DateCandidate> {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val regex = Regex(
            "<(?:[A-Za-z0-9_-]+:)?date\\b([^>]*)>(.*?)</(?:[A-Za-z0-9_-]+:)?date>",
            options,
        )
        return regex.findAll(xml)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                val value = match.groupValues[2].cleanXmlText()
                if (value.isBlank()) return@mapNotNull null
                DateCandidate(value = value, kind = classifyDate(attrs))
            }
            .filterNot { it.kind == DateKind.MODIFICATION }
            .toList()
    }

    private fun metaDateCandidates(xml: String): List<DateCandidate> {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val regex = Regex("<meta\\b([^>]*)>(.*?)</meta>|<meta\\b([^>]*)/>", options)
        return regex.findAll(xml)
            .mapNotNull { match ->
                val attrs = match.groupValues[1].ifBlank { match.groupValues[3] }
                val marker = attr(attrs, "property") ?: attr(attrs, "name") ?: return@mapNotNull null
                val markerKey = marker.lowercase()
                if (!markerKey.contains("date") &&
                    !markerKey.contains("issued") &&
                    !markerKey.contains("modified") &&
                    !markerKey.contains("timestamp")
                ) {
                    return@mapNotNull null
                }

                val value = match.groupValues[2].ifBlank { attr(attrs, "content").orEmpty() }.cleanXmlText()
                if (value.isBlank()) return@mapNotNull null
                DateCandidate(value = value, kind = classifyDate(attrs))
            }
            .filterNot { it.kind == DateKind.MODIFICATION }
            .toList()
    }

    private fun classifyDate(attrs: String): DateKind {
        val marker = listOfNotNull(
            attr(attrs, "opf:event"),
            attr(attrs, "event"),
            attr(attrs, "property"),
            attr(attrs, "name"),
        ).joinToString(" ").lowercase()

        return when {
            marker.contains("modification") ||
                marker.contains("modified") ||
                marker.contains("timestamp") -> DateKind.MODIFICATION
            marker.contains("publication") ||
                marker.contains("published") ||
                marker.contains("issued") -> DateKind.PUBLICATION
            else -> DateKind.NEUTRAL
        }
    }

    private fun tagTexts(xml: String, localName: String): List<String> {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val regex = Regex(
            "<(?:[A-Za-z0-9_-]+:)?$localName\\b[^>]*>(.*?)</(?:[A-Za-z0-9_-]+:)?$localName>",
            options,
        )
        return regex.findAll(xml)
            .map { it.groupValues[1].cleanXmlText() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun attr(attrs: String, name: String): String? {
        val regex = Regex("\\b${Regex.escape(name)}\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        return regex.find(attrs)?.groupValues?.get(1)?.cleanXmlText()
    }

    private fun resolveRelative(opfPath: String, href: String): String {
        val cleanHref = href.substringBefore('#')
        val base = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val combined = if (base.isBlank()) cleanHref else "$base/$cleanHref"
        val parts = mutableListOf<String>()

        combined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }

        return parts.joinToString("/")
    }

    private fun String.containsToken(token: String): Boolean {
        return split(Regex("\\s+")).any { it.equals(token, ignoreCase = true) }
    }

    private fun String.toIdentifierKey(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "identifier" }
    }

    private fun String.uniqueIdentifierKey(existing: Map<String, String>): String {
        if (!existing.containsKey(this)) return this
        var index = 2
        while (existing.containsKey("$this$index")) {
            index += 1
        }
        return "$this$index"
    }

    private fun String.extractIsbn(): String? {
        val compact = filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
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

    private fun String.cleanXmlText(): String {
        return replace(Regex("<!\\[CDATA\\[(.*?)]]>", setOf(RegexOption.DOT_MATCHES_ALL)), "$1")
            .replace(Regex("<[^>]+>"), " ")
            .unescapeXml()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.unescapeXml(): String {
        return replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val resolvedHref: String,
        val mediaType: String?,
        val properties: String,
    )

    private data class DateCandidate(
        val value: String,
        val kind: DateKind,
    )

    private enum class DateKind {
        PUBLICATION,
        NEUTRAL,
        MODIFICATION,
    }

    private companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"
        const val CONTAINER_PATH = "META-INF/container.xml"
    }
}
