package com.foliore.android.storage

import java.io.File
import java.nio.file.Files
import java.util.UUID

/** The app's sync containers share one process; process death clears the active sessions. */
internal object SyncUploadSnapshots {
    private val activeDirectories = mutableSetOf<File>()
    private val sessionName = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun <T> withDirectory(cacheDir: File, block: (File) -> T): T {
        val directory = synchronized(activeDirectories) {
            val root = File(cacheDir, "sync-upload").canonicalFile
            check(root.isDirectory || root.mkdirs()) { "Cannot create sync staging directory" }
            root.listFiles().orEmpty().forEach { old ->
                if (old.isDirectory && sessionName.matches(old.name) &&
                    !Files.isSymbolicLink(old.toPath()) && old !in activeDirectories
                ) {
                    old.deleteRecursively()
                }
            }
            File(root, UUID.randomUUID().toString()).also {
                check(it.mkdir()) { "Cannot create sync snapshot" }
                activeDirectories.add(it)
            }
        }
        return try {
            block(directory)
        } finally {
            synchronized(activeDirectories) {
                directory.deleteRecursively()
                activeDirectories.remove(directory)
            }
        }
    }
}
