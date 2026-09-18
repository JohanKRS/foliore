package com.foliore.android.ui.details

import android.app.Activity
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.storage.AndroidOriginalFilesService
import com.foliore.android.storage.OriginalBookFile
import com.foliore.android.ui.library.FolioreOptionsDialog
import com.foliore.android.viewmodel.LibraryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OriginalFilesFooter(onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(
                text = stringResource(R.string.original_files_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun OriginalFilesDialog(bookId: String, viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember(bookId) { mutableStateOf<List<OriginalBookFile>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var deleteId by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var saveId by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    val deleting = files?.firstOrNull { it.id == deleteId }

    fun perform(failureMessage: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = failureMessage }
            finally { busy = false }
        }
    }

    fun load() = perform(R.string.original_files_load_failed) { files = viewModel.originalFiles(bookId) }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = saveId
        saveId = null
        if (result.resultCode == Activity.RESULT_OK && id != null) {
            val uri = result.data?.data
            if (uri == null) message = R.string.original_file_save_failed
            else perform(R.string.original_file_save_failed) {
                viewModel.saveOriginalFile(bookId, id, uri)
                message = R.string.original_file_saved
            }
        }
    }

    LaunchedEffect(bookId) { load() }

    FolioreOptionsDialog(
        title = stringResource(if (deleting == null) R.string.original_files_title else R.string.original_file_delete_title),
        onDismiss = { if (!busy) { if (deleteId != null) deleteId = null else onDismiss() } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (deleting != null) {
                Text(stringResource(R.string.original_file_delete_body, deleting.name), style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { deleteId = null; message = null }, enabled = !busy) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = {
                        perform(R.string.original_file_delete_failed) {
                            files = viewModel.deleteOriginalFile(bookId, deleting.id)
                            deleteId = null
                        }
                    }, enabled = !busy) {
                        Text(stringResource(R.string.delete_book_confirm), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (files != null) {
                if (files!!.isEmpty()) {
                    Text(stringResource(R.string.original_files_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.original_files_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    files!!.forEach { file ->
                        Column {
                            Text(file.name, style = MaterialTheme.typography.titleSmall)
                            Text("${file.format} · ${Formatter.formatShortFileSize(context, file.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    saveId = file.id
                                    savePicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = AndroidOriginalFilesService.mimeType(file)
                                        putExtra(Intent.EXTRA_TITLE, file.name)
                                    })
                                }, enabled = !busy) { Text(stringResource(R.string.original_file_save)) }
                                TextButton(onClick = {
                                    perform(R.string.original_file_share_failed) {
                                        val intent = viewModel.shareOriginalFile(bookId, file.id)
                                        context.startActivity(Intent.createChooser(intent, file.name))
                                    }
                                }, enabled = !busy) { Text(stringResource(R.string.original_file_share)) }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { deleteId = file.id; message = null }, enabled = !busy) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.original_file_delete_title),
                                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            if (files == null && !busy && message != null) {
                TextButton(onClick = { load() }) { Text(stringResource(R.string.original_files_retry)) }
            }
        }
    }
}
