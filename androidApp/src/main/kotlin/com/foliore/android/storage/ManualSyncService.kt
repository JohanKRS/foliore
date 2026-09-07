package com.foliore.android.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.foliore.shared.library.LibraryRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ManualSyncService(
    context: Context,
    libraryRepository: LibraryRepository,
    deviceIdProvider: DeviceIdProvider,
    aiCoverPromptRepository: AiCoverPromptRepository,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(SyncPreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val syncSettingsRepository = SyncSettingsRepository(appContext)
    private val syncEngine = PortableLibrarySyncEngine(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
        aiCoverPromptRepository = aiCoverPromptRepository,
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun configureFolder(uri: Uri): ManualSyncResult = synchronized(SyncOperationCoordinator.remoteLock) {
        return runCatching {
            val root = DocumentFile.fromTreeUri(appContext, uri)
                ?.takeIf { it.exists() && it.canWrite() }
                ?: return ManualSyncResult.FolderUnavailable
            val documentCache = DocumentFileRemoteLibraryStore.DocumentFileCache()
            val selection = root.inspectSyncFolderSelection(documentCache)
            if (selection is SyncFolderSelection.LibrarySubfolder) {
                return ManualSyncResult.FolderLooksLikeLibrarySubfolder
            }

            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val readySelection = selection as SyncFolderSelection.Ready
            persistSyncFolder(
                treeUri = uri,
                libraryRoot = readySelection.libraryRoot,
                folderLabel = readySelection.folderLabel,
            )
            syncSettingsRepository.setActiveProvider(SyncProvider.MANUAL_FOLDER)
            ManualSyncResult.Configured(
                folderLabel = readySelection.folderLabel ?: folderLabel(uri) ?: DEFAULT_FOLDER_LABEL,
                hasExistingLibrary = readySelection.hasExistingLibrary,
            )
        }.getOrElse { error ->
            Log.e(TAG, "Failed to configure sync folder", error)
            ManualSyncResult.Failed
        }

    }

    fun disconnect() = synchronized(SyncOperationCoordinator.remoteLock) {
        val uri = syncUri()
        if (uri != null) {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        preferences.edit()
            .remove(KEY_SYNC_TREE_URI)
            .remove(KEY_LIBRARY_ROOT_URI)
            .remove(KEY_LIBRARY_ROOT_INITIALIZED)
            .remove(KEY_LIBRARY_ROOT_LABEL)
            .apply()
        if (syncSettingsRepository.activeProvider() == SyncProvider.MANUAL_FOLDER) {
            syncSettingsRepository.setActiveProvider(SyncProvider.NONE)
        }

    }

    fun hasConfiguredFolder(): Boolean {
        return syncUri() != null
    }

    fun folderLabel(): String? {
        val uri = syncUri() ?: return null
        return preferences.getString(KEY_LIBRARY_ROOT_LABEL, null)
            ?.takeIf { it.isNotBlank() }
            ?: folderLabel(uri)
    }

    fun syncNow(onProgress: (ManualSyncProgress) -> Unit = {}): ManualSyncResult = synchronized(SyncOperationCoordinator.remoteLock) {
        return runCatching {
            val store = remoteStoreOrNull() ?: return ManualSyncResult.NotConfigured
            syncEngine.sync(store, onProgress)
        }.getOrElse { error ->
            Log.e(TAG, "Manual sync failed", error)
            ManualSyncResult.Failed
        }
    }

    private fun remoteStoreOrNull(): RemoteLibraryStore? {
        val treeUri = syncUri() ?: return null
        val root = DocumentFile.fromTreeUri(appContext, treeUri)
            ?.takeIf { it.exists() && it.canWrite() }
            ?: return null
        val documentCache = DocumentFileRemoteLibraryStore.DocumentFileCache()
        val libraryRoot = root.resolveLibraryRoot(documentCache)
        return DocumentFileRemoteLibraryStore(
            context = appContext,
            deletionScope = "folder:$treeUri",
            root = libraryRoot,
            label = folderLabel() ?: libraryRoot.name ?: DEFAULT_FOLDER_LABEL,
        )
    }

    private fun DocumentFile.resolveLibraryRoot(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): DocumentFile {
        val nested = documentCache.directoriesNamed(this, LIBRARY_ROOT_NAME)
        val candidates = (nested + listOfNotNull(takeIf { isLibraryRootCandidate(documentCache) }))
            .distinctBy { it.uri }
        val saved = preferences.getString(KEY_LIBRARY_ROOT_URI, null)
        val selected = candidates.firstOrNull { it.uri.toString() == saved }
            ?: candidates.singleOrNull()
        check(selected != null || candidates.isEmpty()) { "Multiple libraries found; select the intended library folder" }
        val libraryRoot = selected ?: createDirectory(LIBRARY_ROOT_NAME)
            ?: error("Cannot create $LIBRARY_ROOT_NAME")
        markLibraryRootInitialized(libraryRoot)
        return libraryRoot
    }

    private fun DocumentFile.inspectSyncFolderSelection(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): SyncFolderSelection {
        if (looksLikeLibrarySubfolder(documentCache)) {
            return SyncFolderSelection.LibrarySubfolder
        }

        val nestedLibraryRoots = documentCache.directoriesNamed(this, LIBRARY_ROOT_NAME)
        val selfRoot = takeIf { isLibraryRootCandidate(documentCache) }
        val canonicalRoot = when {
            selfRoot != null && nestedLibraryRoots.isNotEmpty() -> {
                (listOf(selfRoot) + nestedLibraryRoots).bestLibraryRoot(documentCache)
            }
            selfRoot != null -> selfRoot
            nestedLibraryRoots.isNotEmpty() -> nestedLibraryRoots.bestLibraryRoot(documentCache)
            else -> null
        }

        return SyncFolderSelection.Ready(
            libraryRoot = canonicalRoot,
            folderLabel = canonicalRoot?.name ?: LIBRARY_ROOT_NAME,
            hasExistingLibrary = canonicalRoot?.hasLibraryArtifacts(documentCache) == true,
        )
    }

    private fun DocumentFile.looksLikeLibrarySubfolder(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): Boolean {
        val childNames = documentCache.childrenOf(this)
            .mapNotNull { child -> child.name }
            .toSet()
        return SyncFolderStructureRules.looksLikeLibrarySubfolder(
            name = name,
            childNames = childNames,
        )
    }

    private fun DocumentFile.isLibraryRootCandidate(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): Boolean {
        val children = documentCache.childrenOf(this)
        return SyncFolderStructureRules.isLibraryRootCandidate(
            name = name,
            hasValidManifest = hasLooseLibraryManifest(documentCache),
            fileNames = children.mapNotNull { child -> child.name.takeUnless { child.isDirectory } }.toSet(),
            directoryNames = children.mapNotNull { child -> child.name.takeIf { child.isDirectory || !child.isFile } }
                .toSet(),
        )
    }

    private fun DocumentFile.hasLibraryArtifacts(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): Boolean {
        val children = documentCache.childrenOf(this)
        return SyncFolderStructureRules.hasLibraryArtifacts(
            hasValidManifest = hasLooseLibraryManifest(documentCache),
            fileNames = children.mapNotNull { child -> child.name.takeUnless { child.isDirectory } }.toSet(),
            directoryNames = children.mapNotNull { child -> child.name.takeIf { child.isDirectory || !child.isFile } }
                .toSet(),
        )
    }

    private fun DocumentFile.libraryRootStructureScore(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): Int {
        val children = documentCache.childrenOf(this)
        return SyncFolderStructureRules.libraryRootStructureScore(
            fileNames = children.mapNotNull { child -> child.name.takeUnless { child.isDirectory } }.toSet(),
            directoryNames = children.mapNotNull { child -> child.name.takeIf { child.isDirectory || !child.isFile } }
                .toSet(),
        )
    }

    private fun DocumentFile.libraryRootScore(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): Int {
        return libraryRootStructureScore(documentCache) + documentCache.childrenOf(this).size.coerceAtMost(40)
    }

    private fun DocumentFile.deleteLooseLibraryArtifacts(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ) {
        if (!hasLooseLibraryManifest(documentCache)) return

        documentCache.filesNamed(this, "library.manifest.json")
            .forEach { manifest ->
                if (!manifest.delete()) error("Cannot delete legacy library manifest")
            }
        documentCache.filesNamed(this, AI_COVER_PROMPTS_FILE)
            .forEach { promptFile ->
                if (!promptFile.delete()) error("Cannot delete legacy AI cover prompt file")
            }
        listOf("books", "devices", "snapshots")
            .flatMap { name -> documentCache.directoriesNamed(this, name) }
            .forEach { directory -> directory.deleteTree(documentCache) }
        documentCache.invalidate(this)
    }

    private fun DocumentFile.hasLooseLibraryManifest(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): Boolean {
        return documentCache.filesNamed(this, "library.manifest.json")
            .any { manifest ->
                runCatching {
                    json.parseToJsonElement(manifest.readText()).jsonObject.containsKey("schemaVersion")
                }.getOrDefault(false)
            }
    }

    private fun List<DocumentFile>.bestLibraryRoot(
        documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache,
    ): DocumentFile? {
        val storedRootUri = preferences.getString(KEY_LIBRARY_ROOT_URI, null)
        return maxWithOrNull(
            compareBy<DocumentFile> { candidate -> candidate.libraryRootScore(documentCache) }
                .thenBy { candidate -> if (candidate.uri.toString() == storedRootUri) 1 else 0 }
                .thenBy { candidate -> candidate.lastModified() }
                .thenBy { candidate -> candidate.uri.toString() },
        )
    }

    private fun markLibraryRootInitialized(libraryRoot: DocumentFile) {
        preferences.edit()
            .putBoolean(KEY_LIBRARY_ROOT_INITIALIZED, true)
            .putString(KEY_LIBRARY_ROOT_URI, libraryRoot.uri.toString())
            .putString(KEY_LIBRARY_ROOT_LABEL, libraryRoot.name ?: DEFAULT_FOLDER_LABEL)
            .apply()
    }

    private fun persistSyncFolder(
        treeUri: Uri,
        libraryRoot: DocumentFile?,
        folderLabel: String?,
    ) {
        val editor = preferences.edit()
            .putString(KEY_SYNC_TREE_URI, treeUri.toString())
            .remove(KEY_LIBRARY_ROOT_URI)
            .remove(KEY_LIBRARY_ROOT_INITIALIZED)
            .remove(KEY_LIBRARY_ROOT_LABEL)
        if (libraryRoot != null) {
            editor
                .putString(KEY_LIBRARY_ROOT_URI, libraryRoot.uri.toString())
                .putString(KEY_LIBRARY_ROOT_LABEL, libraryRoot.name ?: folderLabel ?: DEFAULT_FOLDER_LABEL)
                .putBoolean(KEY_LIBRARY_ROOT_INITIALIZED, true)
        } else {
            editor.putString(KEY_LIBRARY_ROOT_LABEL, folderLabel ?: DEFAULT_FOLDER_LABEL)
        }
        editor.apply()
    }

    private fun DocumentFile.deleteTree(documentCache: DocumentFileRemoteLibraryStore.DocumentFileCache) {
        if (isDirectory || !isFile) {
            documentCache.childrenOf(this).forEach { child ->
                child.deleteTree(documentCache)
            }
        }
        documentCache.invalidate(this)
        if (!delete()) error("Cannot delete duplicate ${name ?: uri}")
    }

    private fun DocumentFile.readText(): String {
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: error("Cannot open ${name ?: uri} for reading")
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun syncUri(): Uri? {
        return preferences.getString(KEY_SYNC_TREE_URI, null)?.let(Uri::parse)
    }

    private fun folderLabel(uri: Uri): String? {
        return DocumentFile.fromTreeUri(appContext, uri)?.name
    }

    private sealed interface SyncFolderSelection {
        data class Ready(
            val libraryRoot: DocumentFile?,
            val folderLabel: String?,
            val hasExistingLibrary: Boolean,
        ) : SyncFolderSelection

        data object LibrarySubfolder : SyncFolderSelection
    }

    private companion object {
        const val KEY_SYNC_TREE_URI = "sync_tree_uri"
        const val KEY_LIBRARY_ROOT_INITIALIZED = "library_root_initialized"
        const val KEY_LIBRARY_ROOT_URI = "library_root_uri"
        const val KEY_LIBRARY_ROOT_LABEL = "library_root_label"
        const val LIBRARY_ROOT_NAME = SyncFolderStructureRules.LIBRARY_ROOT_NAME
        const val DEFAULT_FOLDER_LABEL = "Foliore Library"
        const val AI_COVER_PROMPTS_FILE = "ai-cover-prompts.json"
        const val TAG = "ManualSyncService"
    }
}
