package com.foliore.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.foliore.android.R
import com.foliore.android.billing.BillingState
import com.foliore.android.billing.LibraryLimit
import com.foliore.android.billing.UnlimitedReminder
import kotlinx.coroutines.delay

@Composable
internal fun UnlimitedReminderHost(
    canPresent: Boolean,
    bookCount: Int,
    billing: BillingState,
    onRefresh: () -> Unit,
    onRestore: () -> Unit,
    onPurchase: () -> Unit,
) {
    val context = LocalContext.current
    val reminder = remember(context.applicationContext) { UnlimitedReminder(context) }
    val reminderState by reminder.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showReminder by rememberSaveable { mutableStateOf(false) }
    var showPurchase by rememberSaveable { mutableStateOf(false) }
    val eligible = canPresent && UnlimitedReminder.eligible(bookCount, billing)

    LaunchedEffect(eligible, reminderState, lifecycleOwner) {
        if (eligible) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val wait = reminderState.delayUntilDue(System.currentTimeMillis()) ?: return@repeatOnLifecycle
                delay(wait)
                showReminder = true
                reminder.markPresented()
            }
        }
    }
    LaunchedEffect(billing.unlimited) {
        if (billing.unlimited) showReminder = false
    }

    if (showReminder && eligible && !showPurchase) {
        UnlimitedReminderDialog(
            bookCount = bookCount,
            onViewUnlimited = { showReminder = false; showPurchase = true },
            onRemindLater = { reminder.remindInOneWeek(); showReminder = false },
            onDismiss = { reminder.dismiss(); showReminder = false },
        )
    }
    if (showPurchase) {
        UnlimitedPurchaseDialog(billing, bookCount, onRefresh, onRestore, onPurchase,
            onDismiss = { showPurchase = false })
    }
}

@Composable
private fun UnlimitedReminderDialog(
    bookCount: Int,
    onViewUnlimited: () -> Unit,
    onRemindLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    FolioreOptionsDialog(title = stringResource(R.string.billing_reminder_title), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.billing_reminder_count, bookCount, LibraryLimit.FREE_BOOKS),
                style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.billing_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onViewUnlimited, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = unlimitedActionColor())) {
                Text(stringResource(R.string.billing_reminder_view))
            }
            Column {
                TextButton(onClick = onRemindLater, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(stringResource(R.string.billing_reminder_later))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(stringResource(R.string.billing_reminder_dismiss))
                }
            }
        }
    }
}
