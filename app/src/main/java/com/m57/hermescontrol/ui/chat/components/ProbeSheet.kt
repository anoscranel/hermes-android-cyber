package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.chat.ProbeResult
import com.m57.hermescontrol.ui.chat.ProbeStatus
import com.m57.hermescontrol.ui.chat.ProbeViewModel
import com.m57.hermescontrol.ui.common.SearchBar

/**
 * Probe-testing sheet: select models from the loaded catalog, then fire a
 * short real completion at each (session-scoped assignment) and report a
 * status dot, time-to-first-token, total latency, and tokens/sec per model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbeSheet(
    catalog: List<Pair<String, String>>,
    currentSessionId: String?,
    currentModelLabel: String?,
    onDismiss: () -> Unit,
    viewModel: ProbeViewModel = viewModel { ProbeViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(catalog) {
        viewModel.initResults(catalog)
    }

    var query by remember { mutableStateOf("") }
    val filteredCatalog =
        remember(catalog, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) {
                catalog
            } else {
                catalog.filter { (provider, model) ->
                    model.lowercase().contains(q) || provider.lowercase().contains(q)
                }
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("probe_sheet"),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.md)) {
            Text(
                text = stringResource(R.string.probe_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = spacing.sm),
            )
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.probe_search_placeholder),
                modifier = Modifier.testTag("probe_search"),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.selectAll(filteredCatalog) },
                    modifier = Modifier.testTag("probe_select_all"),
                ) {
                    Text(stringResource(R.string.probe_select_all))
                }
                TextButton(
                    onClick = { viewModel.clearSelection() },
                    modifier = Modifier.testTag("probe_select_none"),
                ) {
                    Text(stringResource(R.string.probe_select_none))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.probe_selected_count, uiState.selected.size),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f).testTag("probe_list")) {
                items(filteredCatalog, key = { "${it.first}:${it.second}" }) { (provider, model) ->
                    val k = "$provider:$model"
                    ProbeRow(
                        provider = provider,
                        model = model,
                        result = uiState.results[k],
                        selected = k in uiState.selected,
                        enabled = !uiState.isRunning,
                        onToggle = { viewModel.toggleSelect(provider, model) },
                    )
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.isRunning) {
                    FilledTonalButton(
                        onClick = { viewModel.cancel() },
                        modifier = Modifier.testTag("probe_cancel"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.probe_cancel),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = { viewModel.runProbes(currentSessionId, currentModelLabel) },
                        enabled = uiState.selected.isNotEmpty(),
                        modifier = Modifier.testTag("probe_run"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.probe_run),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (currentSessionId == null) {
                    Text(
                        text = stringResource(R.string.probe_needs_session),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbeRow(
    provider: String,
    model: String,
    result: ProbeResult?,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val statusColors = LocalHermesStatusColors.current
    val dotColor =
        when (result?.status) {
            ProbeStatus.OK -> statusColors.success

            ProbeStatus.FAILED -> statusColors.error

            ProbeStatus.RUNNING -> statusColors.warning

            ProbeStatus.PENDING,
            null,
            -> MaterialTheme.colorScheme.outline
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(vertical = 8.dp)
                .testTag("probe_row_${provider}_$model"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            modifier = Modifier.testTag("probe_check_${provider}_$model"),
        )
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
                    .testTag("probe_dot_${provider}_$model"),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = model,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = provider,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (result != null && result.status == ProbeStatus.OK) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        stringResource(
                            R.string.probe_metrics,
                            result.ttftMs ?: 0L,
                            result.latencyMs ?: 0L,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                )
                result.tps?.let { tps ->
                    Text(
                        text = stringResource(R.string.probe_tps, tps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (result != null && result.status == ProbeStatus.FAILED) {
            Text(
                text = result.error ?: stringResource(R.string.probe_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("probe_error_${provider}_$model"),
            )
        }
    }
}
