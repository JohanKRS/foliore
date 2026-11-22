package com.foliore.android.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.viewmodel.SyncProgress
import com.foliore.android.viewmodel.SyncProgressPhase

@Composable
fun SyncProgressDivider(
    isActive: Boolean,
    progress: SyncProgress?,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        if (isActive) {
            val fraction = progress?.navigationFraction
            if (fraction == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = dividerColor.copy(alpha = 0.32f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = dividerColor.copy(alpha = 0.32f),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor),
            )
        }
    }
}

@Composable
fun SyncProgressStatusLine(
    progress: SyncProgress?,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    countColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = progress?.message() ?: stringResource(R.string.sync_progress_preparing),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val countText = progress?.countText()
        if (countText != null) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = countColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SyncProgressStrip(
    progress: SyncProgress,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progress.message(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress.total > 0) {
                Text(
                    text = "${progress.current.coerceIn(0, progress.total)} / ${progress.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val fraction = progress.fraction
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

private val SyncProgress.navigationFraction: Float
    get() {
        val phaseFraction = fraction
        return when (phase) {
            SyncProgressPhase.IMPORTING -> phaseFraction ?: 0.03f
            SyncProgressPhase.PREPARING -> 0.06f
            SyncProgressPhase.DELETING_REMOTE -> phaseFraction ?: 0.08f
            SyncProgressPhase.CHECKING_REMOTE -> 0.12f + ((phaseFraction ?: 0f) * 0.12f)
            SyncProgressPhase.DOWNLOADING -> 0.24f + ((phaseFraction ?: 0f) * 0.28f)
            SyncProgressPhase.UPLOADING -> 0.52f + ((phaseFraction ?: 0f) * 0.32f)
            SyncProgressPhase.WRITING_MANIFEST -> 0.9f
            SyncProgressPhase.APPLYING_STATUS -> 0.96f
            SyncProgressPhase.GENERATING_METADATA -> phaseFraction ?: 0.48f
            SyncProgressPhase.GENERATING_COVERS -> phaseFraction ?: 0.48f
            SyncProgressPhase.FINDING_COVERS -> phaseFraction ?: 0.4f
            SyncProgressPhase.LOOKING_UP_METADATA -> phaseFraction ?: 0.42f
            SyncProgressPhase.SENDING_TO_KINDLE -> phaseFraction ?: 0.64f
        }.coerceIn(0.03f, 0.98f)
    }

private fun SyncProgress.countText(): String? {
    return total.takeIf { it > 0 }?.let { boundedTotal ->
        "${current.coerceIn(0, boundedTotal)} / $boundedTotal"
    }
}

@Composable
private fun SyncProgress.message(): String {
    return when (phase) {
        SyncProgressPhase.IMPORTING -> stringResource(R.string.importing_epubs)
        SyncProgressPhase.PREPARING -> stringResource(R.string.sync_progress_preparing)
        SyncProgressPhase.DELETING_REMOTE -> stringResource(R.string.sync_progress_deleting_remote)
        SyncProgressPhase.CHECKING_REMOTE -> stringResource(R.string.sync_progress_checking_remote)
        SyncProgressPhase.DOWNLOADING -> stringResource(R.string.sync_progress_downloading)
        SyncProgressPhase.UPLOADING -> stringResource(R.string.sync_progress_uploading)
        SyncProgressPhase.WRITING_MANIFEST -> stringResource(R.string.sync_progress_writing_manifest)
        SyncProgressPhase.APPLYING_STATUS -> stringResource(R.string.sync_progress_applying_status)
        SyncProgressPhase.GENERATING_METADATA -> stringResource(R.string.ai_progress_generating_metadata)
        SyncProgressPhase.GENERATING_COVERS -> stringResource(R.string.ai_progress_generating_covers)
        SyncProgressPhase.FINDING_COVERS -> stringResource(R.string.ai_progress_finding_covers)
        SyncProgressPhase.LOOKING_UP_METADATA -> stringResource(R.string.metadata_lookup_progress)
        SyncProgressPhase.SENDING_TO_KINDLE -> stringResource(R.string.kindle_progress_sending)
    }
}
