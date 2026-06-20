package com.foliore.android.storage

import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class GoogleDriveAppDataRemoteLibraryStore(
    private val drive: GoogleDriveApiClient,
    override val label: String = SyncSettingsRepository.GOOGLE_DRIVE_DEFAULT_LABEL,
) : RemoteLibraryStore {
    override val maxConcurrentUploads: Int = 3
    override val maxConcurrentDownloads: Int = 3

    private val folderIdByPath = ConcurrentHashMap<String, String>().apply {
        put("", GoogleDriveApiClient.APP_DATA_ROOT_ID)
    }
    private val childrenByParentId = ConcurrentHashMap<String, List<DriveFileMetadata>>()
    private val clearedDirectoryPaths = ConcurrentHashMap.newKeySet<String>()

    override fun readText(path: String): String? {
        val file = findFile(path) ?: return null
        return drive.readText(file.id)
    }

    override fun writeText(
        path: String,
        text: String,
        mimeType: String,
    ) {
        val parentId = ensureParentFolder(path)
        if (!path.parentRemotePath().wasRecentlyCleared()) {
            deleteExistingFile(path)
        }
        drive.uploadText(
            parentId = parentId,
            name = path.remoteName(),
            mimeType = mimeType,
            text = text,
        )
        invalidateChildren(parentId)
    }

    override fun replaceFile(
        path: String,
        source: File,
        mimeType: String,
    ) {
        if (!source.exists()) return
        val parentId = ensureParentFolder(path)
        if (!path.parentRemotePath().wasRecentlyCleared()) {
            deleteExistingFile(path)
        }
        drive.uploadFile(
            parentId = parentId,
            name = path.remoteName(),
            mimeType = mimeType,
            source = source,
        )
        invalidateChildren(parentId)
    }

    override fun copyToLocal(
        path: String,
        destination: File,
    ): File? {
        val file = findFile(path) ?: return null
        drive.downloadFile(file.id, destination)
        return destination.takeIf { it.exists() }
    }

    override fun hasDirectory(path: String): Boolean {
        return findFolderId(path) != null
    }

    override fun ensureDirectory(path: String) {
        ensureFolder(path)
    }

    override fun deleteTree(path: String) {
        val normalized = path.normalizedRemotePath()
        if (normalized.isBlank()) return
        findFolderId(normalized)?.let { folderId ->
            deleteFolderChildren(folderId)
            drive.deleteFile(folderId)
            folderIdByPath.remove(normalized)
            return
        }
        findFile(normalized)?.let { file ->
            drive.deleteFile(file.id)
        }
    }

    override fun deleteUnexpectedChildren(
        directoryPath: String,
        expectedDirectoryNames: Set<String>,
    ): Boolean {
        val normalizedDirectory = directoryPath.normalizedRemotePath()
        val directoryId = findFolderId(normalizedDirectory) ?: return false
        var changed = false
        childrenOf(directoryId)
            .filter { child ->
                child.name !in expectedDirectoryNames || child.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
            }
            .forEach { obsolete ->
                if (obsolete.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) {
                    deleteFolderChildren(obsolete.id)
                }
                drive.deleteFile(obsolete.id)
                changed = true
            }
        if (changed) {
            invalidateChildren(directoryId)
        }
        if (expectedDirectoryNames.isEmpty()) {
            clearedDirectoryPaths += normalizedDirectory
        }
        if (changed) {
            folderIdByPath.keys
                .filter { path ->
                    val normalized = path.normalizedRemotePath()
                    normalized == normalizedDirectory || normalized.startsWith("$normalizedDirectory/")
                }
                .toList()
                .forEach { folderIdByPath.remove(it) }
        }
        return changed
    }

    private fun String.wasRecentlyCleared(): Boolean {
        return normalizedRemotePath() in clearedDirectoryPaths
    }

    private fun ensureParentFolder(path: String): String {
        return ensureFolder(path.parentRemotePath())
    }

    private fun ensureFolder(path: String): String {
        val normalized = path.normalizedRemotePath()
        folderIdByPath[normalized]?.let { return it }
        if (normalized.isBlank()) return GoogleDriveApiClient.APP_DATA_ROOT_ID

        var currentPath = ""
        var parentId = GoogleDriveApiClient.APP_DATA_ROOT_ID
        normalized.split('/').filter { it.isNotBlank() }.forEach { segment ->
            currentPath = listOf(currentPath, segment).filter { it.isNotBlank() }.joinToString("/")
            val cached = folderIdByPath[currentPath]
            if (cached != null) {
                parentId = cached
            } else {
                val existing = findChild(parentId, segment, folder = true)
                val folderId = existing?.id ?: drive.createFolder(parentId, segment).id
                if (existing == null) {
                    invalidateChildren(parentId)
                }
                folderIdByPath[currentPath] = folderId
                parentId = folderId
            }
        }
        return parentId
    }

    private fun findFolderId(path: String): String? {
        val normalized = path.normalizedRemotePath()
        folderIdByPath[normalized]?.let { return it }
        if (normalized.isBlank()) return GoogleDriveApiClient.APP_DATA_ROOT_ID

        var currentPath = ""
        var parentId = GoogleDriveApiClient.APP_DATA_ROOT_ID
        normalized.split('/').filter { it.isNotBlank() }.forEach { segment ->
            currentPath = listOf(currentPath, segment).filter { it.isNotBlank() }.joinToString("/")
            val child = findChild(parentId, segment, folder = true) ?: return null
            folderIdByPath[currentPath] = child.id
            parentId = child.id
        }
        return parentId
    }

    private fun findFile(path: String): DriveFileMetadata? {
        val normalized = path.normalizedRemotePath()
        if (normalized.isBlank()) return null
        val parentId = findFolderId(normalized.parentRemotePath()) ?: return null
        return findChild(parentId, normalized.remoteName(), folder = false)
    }

    private fun deleteExistingFile(path: String) {
        val parentId = findFolderId(path.parentRemotePath()) ?: return
        childrenOf(parentId)
            .filter { child ->
                child.name == path.remoteName() &&
                    child.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
            }
            .forEach { existing -> drive.deleteFile(existing.id) }
        invalidateChildren(parentId)
    }

    private fun deleteFolderChildren(folderId: String) {
        childrenOf(folderId).forEach { child ->
            if (child.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) {
                deleteFolderChildren(child.id)
            }
            drive.deleteFile(child.id)
        }
        invalidateChildren(folderId)
    }

    private fun findChild(
        parentId: String,
        name: String,
        folder: Boolean,
    ): DriveFileMetadata? {
        return childrenOf(parentId)
            .filter { child ->
                child.name == name &&
                    if (folder) {
                        child.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE
                    } else {
                        child.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
                    }
            }
            .sortedWith(compareByDescending<DriveFileMetadata> { it.modifiedTime.orEmpty() }.thenBy { it.id })
            .firstOrNull()
    }

    private fun childrenOf(parentId: String): List<DriveFileMetadata> {
        return childrenByParentId.computeIfAbsent(parentId) {
            drive.listChildren(parentId)
        }
    }

    private fun invalidateChildren(parentId: String) {
        childrenByParentId.remove(parentId)
    }
}
