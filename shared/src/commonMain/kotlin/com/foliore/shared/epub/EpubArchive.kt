package com.foliore.shared.epub

interface EpubArchive {
    fun listEntries(): List<String>
    fun readText(path: String): String?
    fun readBytes(path: String): ByteArray?
}

