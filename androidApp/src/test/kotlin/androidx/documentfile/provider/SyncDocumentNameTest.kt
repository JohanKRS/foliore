// DocumentFile's constructor is package-private; keep the provider double in its package.
package androidx.documentfile.provider

import android.net.Uri
import com.foliore.android.storage.withExactSyncName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SyncDocumentNameTest {
    @Test fun extensionAddedByProviderIsRemovedBeforePublishing() {
        val file = ProviderFile("foliore-v4-commit-test.txt")
        assertSame(file, file.withExactSyncName("foliore-v4-commit-test"))
        assertEquals("foliore-v4-commit-test", file.name)
        assertEquals(1, file.renames)
        assertFalse(file.deleted)
    }

    @Test fun exactNamesDoNotRequireProviderRenameSupport() {
        val file = ProviderFile("metadata.json", canRename = false)
        assertSame(file, file.withExactSyncName("metadata.json"))
        assertEquals(0, file.renames)
    }

    @Test fun failedOrMisleadingRenamesDoNotLeavePublishableMarkers() {
        for (file in listOf(ProviderFile("commit.txt", canRename = false), ProviderFile("commit.txt", keepSuffix = true))) {
            assertFailsWith<IllegalStateException> { file.withExactSyncName("commit") }
            assertTrue(file.deleted)
        }
    }

    private class ProviderFile(private var displayName: String, private val canRename: Boolean = true, private val keepSuffix: Boolean = false) : DocumentFile(null) {
        var renames = 0
        var deleted = false
        override fun getName() = displayName
        override fun renameTo(name: String): Boolean {
            renames++
            if (canRename) displayName = name + if (keepSuffix) ".txt" else ""
            return canRename
        }
        override fun delete(): Boolean { deleted = true; return true }
        override fun getUri(): Uri = error("Not used by name validation")
        override fun createFile(mimeType: String, displayName: String): DocumentFile? = null
        override fun createDirectory(displayName: String): DocumentFile? = null
        override fun getType() = "text/plain"
        override fun isDirectory() = false
        override fun isFile() = true
        override fun isVirtual() = false
        override fun lastModified() = 0L
        override fun length() = 0L
        override fun canRead() = true
        override fun canWrite() = true
        override fun exists() = !deleted
        override fun listFiles(): Array<DocumentFile> = emptyArray()
    }
}
