package com.foliore.android.ui.library

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.billing.BillingMessage
import com.foliore.android.billing.BillingState
import com.foliore.android.billing.LibraryLimit
import com.foliore.android.viewmodel.LibraryUiState
import com.foliore.android.ui.theme.FolioreBrassInk

@Composable
internal fun unlimitedActionColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) FolioreBrassInk
    else MaterialTheme.colorScheme.primary

@Composable
internal fun UnlimitedSettingsEntry(state: BillingState, bookCount: Int,
    onRefresh: () -> Unit, onRestore: () -> Unit, onPurchase: () -> Unit,
    highlighted: Boolean = false) {
    var show by rememberSaveable { mutableStateOf(false) }
    if (highlighted) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.billing_settings_limit_title),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.billing_settings_limit_body, LibraryLimit.FREE_BOOKS),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, unlimitedActionColor().copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = unlimitedActionColor())) {
                    Text(stringResource(R.string.billing_unlock_title))
                }
            }
        }
    } else {
        TextButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.unlimited) R.string.billing_active else R.string.billing_unlock_title))
        }
    }
    if (show) {
        UnlimitedPurchaseDialog(state, bookCount, onRefresh, onRestore, onPurchase, onDismiss = { show = false })
    }
}

@Composable
internal fun UnlimitedPurchaseDialog(state: BillingState, bookCount: Int,
    onRefresh: () -> Unit, onRestore: () -> Unit, onPurchase: () -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { onRefresh() }
    var terms by rememberSaveable { mutableStateOf(false) }
    if (terms) PurchaseTermsDialog { terms = false }
    else FolioreOptionsDialog(title = stringResource(R.string.billing_title), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (state.unlimited) R.string.billing_owned else R.string.billing_description), style = MaterialTheme.typography.bodyLarge)
            if (!state.unlimited) {
                Text(stringResource(R.string.billing_catalog_count, bookCount, LibraryLimit.FREE_BOOKS), style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let { Text(stringResource(it.resource()), style = MaterialTheme.typography.bodyMedium) }
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant)
            if (!state.unlimited) Button(onClick = onPurchase,
                enabled = !state.busy && !state.pending && state.price != null,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (state.price != null) stringResource(R.string.billing_buy_price, state.price) else stringResource(R.string.billing_price_unavailable))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRestore, enabled = !state.busy) { Text(stringResource(R.string.billing_restore)) }
                if (state.price == null && !state.unlimited) TextButton(onClick = onRefresh, enabled = !state.busy) { Text(stringResource(R.string.billing_retry)) }
                TextButton(onClick = { terms = true }) { Text(stringResource(R.string.billing_terms)) }
            }
        }
    }
}

@Composable
internal fun PurchaseTermsDialog(onDismiss: () -> Unit) {
    FolioreOptionsDialog(title = stringResource(R.string.billing_terms), onDismiss = onDismiss) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(stringResource(R.string.billing_terms_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ImportLimitDialogs(state: LibraryUiState, onContinue: () -> Unit, onDismiss: () -> Unit, onSettings: () -> Unit) {
    val available = state.importLimitAvailable
    if (available != null || state.importLimitSkipped > 0) {
        FolioreOptionsDialog(title = stringResource(R.string.billing_limit_title), onDismiss = onDismiss) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (available != null) stringResource(R.string.billing_import_available, available)
                    else stringResource(R.string.billing_import_skipped, state.importLimitSkipped))
                Text(stringResource(R.string.billing_import_safety), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (available != null) OutlinedButton(onClick = onContinue) { Text(stringResource(R.string.billing_import_continue)) }
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.settings_title)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.billing_close)) }
                }
            }
        }
    }
}

private fun BillingMessage.resource(): Int = when (this) {
    BillingMessage.UNAVAILABLE -> R.string.billing_unavailable
    BillingMessage.PENDING -> R.string.billing_pending
    BillingMessage.RESTORED -> R.string.billing_restored
    BillingMessage.NO_PURCHASE -> R.string.billing_no_purchase
    BillingMessage.VERIFY_FAILED -> R.string.billing_verify_failed
    BillingMessage.RETRY_ACKNOWLEDGEMENT -> R.string.billing_ack_retry
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
