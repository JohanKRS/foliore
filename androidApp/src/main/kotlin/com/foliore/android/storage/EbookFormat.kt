package com.foliore.android.storage

enum class EbookFormat(val extension: String, val mimeTypes: Set<String>) {
    EPUB("epub", setOf("application/epub+zip", "application/x-epub+zip")),
    MOBI("mobi", setOf("application/x-mobipocket-ebook")),
    AZW3("azw3", setOf("application/vnd.amazon.ebook", "application/x-mobi8-ebook", "application/x-azw3"));

    companion object {
        val pickerMimeTypes: Array<String>
            get() = (entries.flatMap { it.mimeTypes } + "application/octet-stream").toTypedArray()

        fun from(displayName: String, mimeType: String?): EbookFormat? =
            entries.firstOrNull { displayName.endsWith(".${it.extension}", ignoreCase = true) }
                ?: entries.firstOrNull { mimeType?.lowercase()?.substringBefore(';')?.trim() in it.mimeTypes }
    }
}
