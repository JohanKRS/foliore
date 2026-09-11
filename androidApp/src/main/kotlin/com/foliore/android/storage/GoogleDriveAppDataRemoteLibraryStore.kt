package com.foliore.android.storage

import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class GoogleDriveAppDataRemoteLibraryStore(
    private val drive: GoogleDriveApiClient,
    override val deletionScope: String? = null,
    override val label: String = SyncSettingsRepository.GOOGLE_DRIVE_DEFAULT_LABEL,
    private val journalCache: DriveJournalCache? = null,
) : RemoteLibraryStore {
    override val currentTimeMillis: Long? get() = drive.serverTimeMillis
    override val maxConcurrentUploads: Int = 3
    override val maxConcurrentDownloads: Int = 3

    private val folderIdByPath = ConcurrentHashMap<String, String>().apply {
        put("", GoogleDriveApiClient.APP_DATA_ROOT_ID)
    }
    private val childrenByParentId = ConcurrentHashMap<String, List<DriveFileMetadata>>()
    private val clearedDirectoryPaths = ConcurrentHashMap.newKeySet<String>()
    private val uploadedFiles = ConcurrentHashMap<String, DriveFileMetadata>()

    override fun listRootEntries(): List<RemoteRootEntry> {
        val children = drive.listChildren(GoogleDriveApiClient.APP_DATA_ROOT_ID)
        childrenByParentId[GoogleDriveApiClient.APP_DATA_ROOT_ID] = children
        check(children.groupBy { it.name }.none { it.value.size > 1 && it.key.startsWith("foliore-v4-") }) {
            "Ambiguous sync objects"
        }
        journalCache?.retain(children)
        return children.map { RemoteRootEntry(it.name, it.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE,
            runCatching { java.time.Instant.parse(it.modifiedTime).toEpochMilli() }.getOrDefault(0L)) }
    }

    override fun readText(path: String): String? {
        val file = findFile(path) ?: return null
        return journalCache?.read(file) { drive.readText(file.id) } ?: drive.readText(file.id)
    }

    override fun writeText(
        path: String,
        text: String,
        mimeType: String,
    ) {
        val parentId = ensureParentFolder(path)
        val existingFiles = existingFiles(path)
        val uploaded = drive.uploadText(
            parentId = parentId,
            name = path.remoteName(),
            mimeType = mimeType,
            text = text,
        )
        deleteReplacedFiles(existingFiles, uploaded)
        invalidateChildren(parentId)
    }

    override fun replaceFile(
        path: String,
        source: File,
        mimeType: String,
    ) {
        check(source.isFile) { "Missing upload source" }
        val parentId = ensureParentFolder(path)
        val existingFiles = existingFiles(path)
        val uploaded = drive.uploadFile(
            parentId = parentId,
            name = path.remoteName(),
            mimeType = mimeType,
            source = source,
        )
        uploadedFiles[path.normalizedRemotePath()] = uploaded
        deleteReplacedFiles(existingFiles, uploaded)
        invalidateChildren(parentId)
    }

    override fun verifiesUpload(path: String, size: Long, sha256: String): Boolean =
        uploadedFiles[path.normalizedRemotePath()]?.verifiesUpload(size, sha256) ?: false

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
            // Drive deletes owned descendants too; listings may briefly lag behind the deletion.
            drive.deleteFile(folderId)
            forgetTree(normalized)
            return
        }
        findFile(normalized)?.let { file ->
            drive.deleteFile(file.id)
            forgetTree(normalized)
        }
    }

    override fun deleteTrees(paths: List<String>) {
        val targets = paths.map { it.normalizedRemotePath() }.filter { it.isNotBlank() }.distinct()
            .mapNotNull { path -> (findFolderId(path) ?: findFile(path)?.id)?.let { path to it } }
        drive.deleteFiles(targets.map { it.second })
        targets.forEach { forgetTree(it.first) }
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

    @Synchronized
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

    private fun existingFiles(path: String): List<DriveFileMetadata> {
        if (path.parentRemotePath().wasRecentlyCleared()) return emptyList()
        val parentId = findFolderId(path.parentRemotePath()) ?: return emptyList()
        return childrenOf(parentId).filter { child ->
            child.name == path.remoteName() &&
                child.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
        }
    }

    private fun deleteReplacedFiles(
        existingFiles: List<DriveFileMetadata>,
        uploaded: DriveFileMetadata,
    ) {
        existingFiles
            .filterNot { existing -> existing.id == uploaded.id }
            .forEach { existing -> drive.deleteFile(existing.id) }
    }

    private fun forgetTree(path: String) {
        folderIdByPath[path.parentRemotePath()]?.let(::invalidateChildren)
        folderIdByPath.keys.filter { it == path || it.startsWith("$path/") }.toList().forEach {
            folderIdByPath.remove(it)?.let(::invalidateChildren)
        }
        clearedDirectoryPaths.removeIf { it == path || it.startsWith("$path/") }
        uploadedFiles.keys.removeIf { it == path || it.startsWith("$path/") }
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
