package com.foliore.android.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.foliore.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun ContentReportDialog(onDismiss: () -> Unit) {
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.report_ai_content)) },
        text = { Column {
            Text(stringResource(R.string.report_content_description))
            OutlinedTextField(value = message, onValueChange = { if (it.length <= 4000) message = it },
                enabled = !sending && outcome != true, minLines = 4,
                label = { Text(stringResource(R.string.report_content_message)) })
            outcome?.let { Text(stringResource(if (it) R.string.report_content_success else R.string.report_content_failure)) }
        } },
        confirmButton = { TextButton(enabled = message.isNotBlank() && !sending && outcome != true, onClick = {
            sending = true
            scope.launch {
                outcome = withContext(Dispatchers.IO) { submitContentReport(message) }
                sending = false
            }
        }) { Text(stringResource(R.string.report_content_send)) } },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun submitContentReport(message: String): Boolean = runCatching {
    val connection = URL("https://foliore.johankrs.com/api/content-reports").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val bytes = buildJsonObject { put("message", message) }.toString().toByteArray()
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        connection.responseCode == 201
    } finally { connection.disconnect() }
}.getOrDefault(false)
