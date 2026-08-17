package com.foliore.android.storage

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleDriveRemoteLibraryGuardTest {
    @Test
    fun pausesWhenPreviouslySyncedLibraryDisappears() {
        assertTrue(
            shouldPauseGoogleDriveSync(
                stats = GoogleDriveBackupStats.Empty,
                hasLocalBooks = true,
                hasPriorLibraryEvidence = true,
            ),
        )
    }

    @Test
    fun pausesWhenDeletionMarkerIsPresent() {
        assertTrue(
            shouldPauseGoogleDriveSync(
                stats = GoogleDriveBackupStats.Deleted,
                hasLocalBooks = true,
                hasPriorLibraryEvidence = false,
            ),
        )
    }

    @Test
    fun pausesWhenManifestIsMissingButPayloadFilesRemain() {
        val orphaned = GoogleDriveBackupStats.Empty.copy(
            remoteFiles = 312,
            remoteItems = 320,
            totalBytes = 400L * 1024L * 1024L,
        )

        assertTrue(orphaned.hasOrphanedFiles)
        assertTrue(
            shouldPauseGoogleDriveSync(
                stats = orphaned,
                hasLocalBooks = true,
                hasPriorLibraryEvidence = false,
            ),
        )
    }

    @Test
    fun allowsFirstUploadToAnUnusedDriveAccount() {
        assertFalse(
            shouldPauseGoogleDriveSync(
                stats = GoogleDriveBackupStats.Empty,
                hasLocalBooks = true,
                hasPriorLibraryEvidence = false,
            ),
        )
    }

    @Test
    fun doesNotPromptWhenThereAreNoLocalBooksToProtect() {
        assertFalse(
            shouldPauseGoogleDriveSync(
                stats = GoogleDriveBackupStats.Deleted,
                hasLocalBooks = false,
                hasPriorLibraryEvidence = true,
            ),
        )
    }
}
