package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.chat.MediaModelsViewModel

/**
 * Non-text (auxiliary) model management: the server's aux task routing —
 * vision/image-understanding, compression, titles, and friends — as its own
 * window, separate from the text-model manager. Each row is one aux task;
 * tapping opens a picker over the loaded text-model catalog and reassigns
 * the task via api/model/set (scope=auxiliary).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaModelsSheet(
    onDismiss: () -> Unit,
    viewModel: MediaModelsViewModel = viewModel { MediaModelsViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickerTask by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("media_models_sheet"),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.md)) {
            Text(
                text = stringResource(R.string.media_models_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = spacing.sm),
            )
            Text(
                text = stringResource(R.string.media_models_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
            HorizontalDivider()

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    uiState.errorMessage != null -> {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    uiState.tasks.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.media_models_empty),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize().testTag("media_models_list")) {
                            items(uiState.tasks, key = { it.task }) { assignment ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { pickerTask = assignment.task }
                                            .padding(vertical = 10.dp)
                                            .testTag("media_task_${assignment.task}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = assignment.task,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = assignment.model,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = assignment.provider,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.media_models_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = spacing.sm),
            )
        }
    }

    pickerTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pickerTask = null },
            title = { Text(stringResource(R.string.media_models_pick_title, task)) },
            text = {
                LazyColumn(modifier = Modifier.testTag("media_picker_list")) {
                    items(uiState.catalogPairs, key = { "${it.first}:${it.second}" }) { (provider, model) ->
                        Text(
                            text = "$provider / $model",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.assign(task, provider, model)
                                        pickerTask = null
                                    }
                                    .padding(vertical = 8.dp)
                                    .testTag("media_pick_${provider}_$model"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerTask = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    ToastEffect(toastMessage = uiState.toastMessage, onClearToast = viewModel::toastShown)
}
