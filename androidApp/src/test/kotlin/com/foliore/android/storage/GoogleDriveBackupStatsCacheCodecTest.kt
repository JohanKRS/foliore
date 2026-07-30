package com.foliore.android.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoogleDriveBackupStatsCacheCodecTest {
    @Test
    fun restoresPersistedStats() {
        val stats = GoogleDriveBackupStats(
            hasBackup = true,
            syncedBooks = 42,
            remoteFiles = 137,
            totalBytes = 98_765_432L,
            rootItems = 5,
            recognizedRootItems = 5,
            unknownRootItems = 0,
            updatedAt = "2026-07-30T12:34:56Z",
        )

        assertEquals(
            stats,
            GoogleDriveBackupStatsCacheCodec.decode(
                GoogleDriveBackupStatsCacheCodec.encode(stats),
            ),
        )
    }

    @Test
    fun ignoresUnknownFieldsFromNewerCacheVersions() {
        val encoded = """
            {
              "hasBackup": true,
              "syncedBooks": 7,
              "remoteFiles": 21,
              "totalBytes": 1234,
              "rootItems": 4,
              "recognizedRootItems": 4,
              "unknownRootItems": 0,
              "updatedAt": null,
              "futureField": "safe to ignore"
            }
        """.trimIndent()

        assertEquals(7, GoogleDriveBackupStatsCacheCodec.decode(encoded)?.syncedBooks)
    }

    @Test
    fun discardsCorruptCache() {
        assertNull(GoogleDriveBackupStatsCacheCodec.decode("not-json"))
    }
}
