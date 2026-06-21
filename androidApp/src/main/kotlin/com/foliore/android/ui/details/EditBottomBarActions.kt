package com.foliore.android.ui.details

data class EditBottomBarActions(
    val onSave: () -> Unit,
    val onShareExport: () -> Unit,
    val onSendToKindle: () -> Unit,
    val saveEnabled: Boolean,
    val hasPendingSave: Boolean = saveEnabled,
    val compact: Boolean,
)
