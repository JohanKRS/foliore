package com.foliore.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.viewmodel.ImportDuplicateChoice
import com.foliore.android.viewmodel.ImportDuplicateConflict

@Composable
internal fun ImportDuplicatesDialog(
    conflicts: List<ImportDuplicateConflict>,
    onApply: (Map<String, ImportDuplicateChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = conflicts.map { conflict -> conflict.token }
    var choicesByToken by rememberSaveable(tokens) {
        mutableStateOf(
            tokens.associateWith { ImportDuplicateChoice.KEEP_LIBRARY.name },
        )
    }

    FolioreOptionsDialog(
        title = stringResource(R.string.import_duplicates_dialog_title),
        onDismiss = onDismiss,
    ) {
        val dialogWidth = maxWidth
        Column {
            Text(
                text = pluralStringResource(
                    R.plurals.import_duplicates_dialog_description,
                    conflicts.size,
                    conflicts.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (dialogWidth >= 430.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DuplicateBatchChoiceButton(
                        label = stringResource(R.string.import_duplicates_all_library),
                        onClick = {
                            choicesByToken = tokens.associateWith {
                                ImportDuplicateChoice.KEEP_LIBRARY.name
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DuplicateBatchChoiceButton(
                        label = stringResource(R.string.import_duplicates_all_imported),
                        onClick = {
                            choicesByToken = tokens.associateWith {
                                ImportDuplicateChoice.USE_IMPORTED.name
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuplicateBatchChoiceButton(
                        label = stringResource(R.string.import_duplicates_all_library),
                        onClick = {
                            choicesByToken = tokens.associateWith {
                                ImportDuplicateChoice.KEEP_LIBRARY.name
                            }
                        },
                    )
                    DuplicateBatchChoiceButton(
                        label = stringResource(R.string.import_duplicates_all_imported),
                        onClick = {
                            choicesByToken = tokens.associateWith {
                                ImportDuplicateChoice.USE_IMPORTED.name
                            }
                        },
                    )
                }
            }

            conflicts.forEachIndexed { index, conflict ->
                val selectedChoice = choicesByToken[conflict.token]
                    ?.let(ImportDuplicateChoice::valueOf)
                    ?: ImportDuplicateChoice.KEEP_LIBRARY
                FolioreDialogSectionCard(label = "${index + 1}. ${conflict.existingTitle}") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DuplicateCopyDescription(
                            label = stringResource(R.string.import_duplicates_library_copy),
                            title = conflict.existingTitle,
                            authors = conflict.existingAuthors,
                        )
                        DuplicateCopyDescription(
                            label = stringResource(R.string.import_duplicates_imported_copy),
                            title = conflict.incomingTitle,
                            authors = conflict.incomingAuthors,
                        )
                        Text(
                            text = stringResource(R.string.import_duplicates_same_content),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FolioreDialogOptionGrid(
                            columns = if (dialogWidth >= 390.dp) 2 else 1,
                            options = listOf(
                                FolioreDialogOption(
                                    label = stringResource(R.string.import_duplicates_keep_library),
                                    selected = selectedChoice == ImportDuplicateChoice.KEEP_LIBRARY,
                                    onClick = {
                                        choicesByToken = choicesByToken + (
                                            conflict.token to ImportDuplicateChoice.KEEP_LIBRARY.name
                                        )
                                    },
                                ),
                                FolioreDialogOption(
                                    label = stringResource(R.string.import_duplicates_use_imported),
                                    selected = selectedChoice == ImportDuplicateChoice.USE_IMPORTED,
                                    onClick = {
                                        choicesByToken = choicesByToken + (
                                            conflict.token to ImportDuplicateChoice.USE_IMPORTED.name
                                        )
                                    },
                                ),
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    onApply(
                        choicesByToken.mapValues { (_, choice) ->
                            ImportDuplicateChoice.valueOf(choice)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_duplicates_apply))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DuplicateBatchChoiceButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
private fun DuplicateCopyDescription(
    label: String,
    title: String,
    authors: List<String>,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = authors.takeIf { it.isNotEmpty() }?.joinToString() ?: stringResource(
                R.string.book_unknown_author,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
