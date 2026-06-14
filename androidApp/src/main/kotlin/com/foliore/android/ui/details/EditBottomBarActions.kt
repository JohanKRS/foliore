package com.foliore.android.ui.details

data class EditBottomBarActions(
    val onSave: () -> Unit,
    val onShareExport: () -> Unit,
    val saveEnabled: Boolean,
    val compact: Boolean,
)
