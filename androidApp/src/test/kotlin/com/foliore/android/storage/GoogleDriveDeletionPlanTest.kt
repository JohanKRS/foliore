package com.foliore.android.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleDriveDeletionPlanTest {
    @Test
    fun collapsesFolderTreesIntoSingleDeleteRequests() {
        val items = listOf(
            folder(id = "books", parentId = GoogleDriveApiClient.APP_DATA_ROOT_ID),
            folder(id = "book-1", parentId = "books"),
            file(id = "epub-1", parentId = "book-1"),
            file(id = "manifest", parentId = GoogleDriveApiClient.APP_DATA_ROOT_ID),
            file(id = "orphan-file", parentId = "missing-parent"),
            folder(id = "orphan-folder", parentId = "missing-parent"),
            file(id = "orphan-child", parentId = "orphan-folder"),
        )

        val plan = planGoogleDriveDeletionTargets(items)

        assertEquals(
            mapOf(
                "books" to 3,
                "orphan-folder" to 2,
                "manifest" to 1,
                "orphan-file" to 1,
            ),
            plan.associate { target -> target.file.id to target.representedItems },
        )
    }

    @Test
    fun treatsItemsWithoutKnownParentsAsIndependentOrphans() {
        val items = listOf(
            file(id = "one", parentId = "missing-a"),
            file(id = "two", parentId = "missing-b"),
        )

        assertEquals(
            setOf("one", "two"),
            planGoogleDriveDeletionTargets(items).mapTo(mutableSetOf()) { it.file.id },
        )
    }

    private fun folder(id: String, parentId: String): DriveFileMetadata {
        return DriveFileMetadata(
            id = id,
            name = id,
            mimeType = GoogleDriveApiClient.FOLDER_MIME_TYPE,
            parents = listOf(parentId),
        )
    }

    private fun file(id: String, parentId: String): DriveFileMetadata {
        return DriveFileMetadata(
            id = id,
            name = id,
            mimeType = "application/octet-stream",
            parents = listOf(parentId),
        )
    }
}
