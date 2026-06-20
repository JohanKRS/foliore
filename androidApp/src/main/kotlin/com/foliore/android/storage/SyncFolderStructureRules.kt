package com.foliore.android.storage

import java.util.Locale

internal object SyncFolderStructureRules {
    const val LIBRARY_ROOT_NAME = "Foliore Library"
    const val MIN_LIBRARY_ROOT_STRUCTURE_SCORE = 250

    private val internalLibraryDirectoryNames = setOf("books", "devices", "snapshots")
    private val bookFolderFileNames = setOf(
        "metadata.json",
        "original.epub",
        "prepared.epub",
        "technical.json",
        "cover_original.jpg",
        "cover_thumb.jpg",
    )
    private val bookFolderCompanionFileNames = bookFolderFileNames - "metadata.json"

    fun looksLikeLibrarySubfolder(
        name: String?,
        childNames: Set<String>,
    ): Boolean {
        val normalizedName = name.normalizedName()
        val normalizedChildNames = childNames.normalizedNames()

        if (normalizedName != null && normalizedName in internalLibraryDirectoryNames) {
            return true
        }

        return "metadata.json" in normalizedChildNames &&
            normalizedChildNames.any { it in bookFolderCompanionFileNames }
    }

    fun isLibraryRootCandidate(
        name: String?,
        hasValidManifest: Boolean,
        fileNames: Set<String>,
        directoryNames: Set<String>,
    ): Boolean {
        return name.normalizedName() == LIBRARY_ROOT_NAME.normalizedName() ||
            hasLibraryArtifacts(
                hasValidManifest = hasValidManifest,
                fileNames = fileNames,
                directoryNames = directoryNames,
            )
    }

    fun hasLibraryArtifacts(
        hasValidManifest: Boolean,
        fileNames: Set<String>,
        directoryNames: Set<String>,
    ): Boolean {
        return hasValidManifest ||
            libraryRootStructureScore(fileNames, directoryNames) >= MIN_LIBRARY_ROOT_STRUCTURE_SCORE
    }

    fun libraryRootStructureScore(
        fileNames: Set<String>,
        directoryNames: Set<String>,
    ): Int {
        val normalizedFileNames = fileNames.normalizedNames()
        val normalizedDirectoryNames = directoryNames.normalizedNames()
        var score = 0
        if ("library.manifest.json" in normalizedFileNames) score += 1_000
        if ("books" in normalizedDirectoryNames) score += 200
        if ("devices" in normalizedDirectoryNames) score += 50
        if ("snapshots" in normalizedDirectoryNames) score += 50
        return score
    }

    private fun String?.normalizedName(): String? {
        return this
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
    }

    private fun Set<String>.normalizedNames(): Set<String> {
        return mapNotNull { it.normalizedName() }.toSet()
    }
}
