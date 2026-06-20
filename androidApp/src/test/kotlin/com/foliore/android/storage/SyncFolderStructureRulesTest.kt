package com.foliore.android.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncFolderStructureRulesTest {
    @Test
    fun recognizesLibraryRootByName() {
        assertTrue(
            SyncFolderStructureRules.isLibraryRootCandidate(
                name = "Foliore Library",
                hasValidManifest = false,
                fileNames = emptySet(),
                directoryNames = emptySet(),
            ),
        )
        assertFalse(
            SyncFolderStructureRules.hasLibraryArtifacts(
                hasValidManifest = false,
                fileNames = emptySet(),
                directoryNames = emptySet(),
            ),
        )
    }

    @Test
    fun recognizesLibraryRootNameCaseInsensitively() {
        assertTrue(
            SyncFolderStructureRules.isLibraryRootCandidate(
                name = "  foliore library  ",
                hasValidManifest = false,
                fileNames = emptySet(),
                directoryNames = emptySet(),
            ),
        )
    }

    @Test
    fun recognizesExistingLibraryRootByManifest() {
        assertTrue(
            SyncFolderStructureRules.hasLibraryArtifacts(
                hasValidManifest = true,
                fileNames = setOf("library.manifest.json"),
                directoryNames = emptySet(),
            ),
        )
        assertTrue(
            SyncFolderStructureRules.isLibraryRootCandidate(
                name = "My Drive",
                hasValidManifest = true,
                fileNames = setOf("library.manifest.json"),
                directoryNames = emptySet(),
            ),
        )
    }

    @Test
    fun recognizesExistingLibraryRootByStructureCaseInsensitively() {
        assertEquals(
            300,
            SyncFolderStructureRules.libraryRootStructureScore(
                fileNames = emptySet(),
                directoryNames = setOf("Books", "DEVICES", "Snapshots"),
            ),
        )
        assertTrue(
            SyncFolderStructureRules.hasLibraryArtifacts(
                hasValidManifest = false,
                fileNames = setOf("Library.Manifest.Json"),
                directoryNames = emptySet(),
            ),
        )
    }

    @Test
    fun recognizesExistingLibraryRootByStructure() {
        assertEquals(
            300,
            SyncFolderStructureRules.libraryRootStructureScore(
                fileNames = emptySet(),
                directoryNames = setOf("books", "devices", "snapshots"),
            ),
        )
        assertTrue(
            SyncFolderStructureRules.hasLibraryArtifacts(
                hasValidManifest = false,
                fileNames = emptySet(),
                directoryNames = setOf("books", "devices", "snapshots"),
            ),
        )
        assertTrue(
            SyncFolderStructureRules.isLibraryRootCandidate(
                name = "Library Backup",
                hasValidManifest = false,
                fileNames = emptySet(),
                directoryNames = setOf("books", "devices", "snapshots"),
            ),
        )
    }

    @Test
    fun keepsPlainParentFolderAvailableForNewLibraryCreation() {
        assertFalse(
            SyncFolderStructureRules.looksLikeLibrarySubfolder(
                name = "Google Drive",
                childNames = setOf("Foliore Library", "Documents"),
            ),
        )
        assertFalse(
            SyncFolderStructureRules.isLibraryRootCandidate(
                name = "Google Drive",
                hasValidManifest = false,
                fileNames = emptySet(),
                directoryNames = setOf("Documents", "Downloads"),
            ),
        )
    }

    @Test
    fun rejectsInternalLibraryDirectoryNames() {
        listOf("books", "Devices", "SNAPSHOTS").forEach { folderName ->
            assertTrue(
                SyncFolderStructureRules.looksLikeLibrarySubfolder(
                    name = folderName,
                    childNames = emptySet(),
                ),
                "$folderName should be treated as an unsafe library subfolder",
            )
        }
    }

    @Test
    fun rejectsSingleBookDirectorySelection() {
        assertTrue(
            SyncFolderStructureRules.looksLikeLibrarySubfolder(
                name = "2001-a-space-odyssey",
                childNames = setOf("metadata.json", "original.epub", "cover_thumb.jpg"),
            ),
        )
    }

    @Test
    fun rejectsSingleBookDirectorySelectionCaseInsensitively() {
        assertTrue(
            SyncFolderStructureRules.looksLikeLibrarySubfolder(
                name = "2001-a-space-odyssey",
                childNames = setOf("Metadata.Json", "Original.Epub", "Cover_Thumb.JPG"),
            ),
        )
    }

    @Test
    fun doesNotRejectUnrelatedMetadataFolderWithoutEpubArtifacts() {
        assertFalse(
            SyncFolderStructureRules.looksLikeLibrarySubfolder(
                name = "Research",
                childNames = setOf("metadata.json", "notes.txt"),
            ),
        )
    }
}
