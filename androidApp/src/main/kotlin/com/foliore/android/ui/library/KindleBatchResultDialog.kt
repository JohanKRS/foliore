package com.foliore.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.storage.KindleBatchReport
import com.foliore.android.storage.KindleBookDelivery
import com.foliore.android.storage.KindleSendResult
import com.foliore.shared.domain.LibraryBook

@Composable
internal fun KindleBatchResultDialog(report: KindleBatchReport, books: List<LibraryBook>, onDismiss: () -> Unit) {
    val titles = books.associate { it.book.id to it.metadata.title }
    FolioreOptionsDialog(title = stringResource(R.string.kindle_batch_send), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.kindle_batch_summary, report.sentCount, report.items.size, report.emailCount),
                style = MaterialTheme.typography.bodyLarge)
            val explanation = when (report.failure) {
                KindleSendResult.UNKNOWN -> R.string.kindle_batch_unknown_hint
                KindleSendResult.AUTH_REQUIRED -> R.string.gmail_notice_auth_required
                KindleSendResult.CONFIGURATION_REQUIRED -> R.string.gmail_notice_configuration_required
                KindleSendResult.RATE_LIMITED -> R.string.gmail_notice_rate_limited
                else -> null
            }
            explanation?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (report.sentCount > 0) Text(stringResource(R.string.kindle_batch_delivery_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            report.items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(titles[item.bookId] ?: item.bookId, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(when (item.delivery) {
                        KindleBookDelivery.SENT -> R.string.kindle_batch_sent
                        KindleBookDelivery.UNKNOWN -> R.string.kindle_batch_unknown
                        KindleBookDelivery.NOT_SENT -> R.string.kindle_batch_not_sent
                        KindleBookDelivery.FAILED -> R.string.kindle_batch_failed
                        KindleBookDelivery.TOO_LARGE -> R.string.kindle_batch_too_large
                        KindleBookDelivery.PREPARATION_FAILED -> R.string.kindle_batch_prepare_failed
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
