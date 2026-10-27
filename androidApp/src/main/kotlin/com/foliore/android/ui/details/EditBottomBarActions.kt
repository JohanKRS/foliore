package com.foliore.android.ui.details

data class EditBottomBarActions(
    val onSave: () -> Unit,
    val onRead: () -> Unit,
    val onRequestNavigate: (() -> Unit) -> Unit,
    val onShareExport: () -> Unit,
    val onSendToKindle: () -> Unit,
    val saveEnabled: Boolean,
    val hasPendingSave: Boolean = saveEnabled,
    /** Any difference from the stored metadata, valid or not; the primary slot shows Save instead of Read. */
    val hasUnsavedChanges: Boolean = hasPendingSave,
    /** The book was opened before on this device; Read becomes Continue. */
    val readingStarted: Boolean = false,
    val compact: Boolean,
)
