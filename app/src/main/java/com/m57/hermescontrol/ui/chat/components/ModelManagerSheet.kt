package com.m57.hermescontrol.ui.chat.components

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.m57.hermescontrol.data.model.ModelCapabilities
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.reasoningSupport
import com.m57.hermescontrol.ui.chat.CapabilityOverride
import com.m57.hermescontrol.ui.chat.ModelManagerUiState
import com.m57.hermescontrol.ui.chat.ModelManagerViewModel
import com.m57.hermescontrol.ui.chat.filterProviders
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.common.ToastEffect

/**
 * Model manager sheet: keyword-filtered provider/model catalog with per-model
 * capability toggles (reasoning / tools / vision / fast), staging of custom
 * models, "probe" = server-side capability refresh, and save-to-config.
 * Opened from the composer toolbar Tune button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    onDismiss: () -> Unit,
    viewModel: ModelManagerViewModel = viewModel { ModelManagerViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("model_manager_sheet"),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.model_manager_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Keyword filter over the whole catalog.
            SearchBar(
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
                placeholder = stringResource(R.string.model_manager_search_placeholder),
                modifier = Modifier.testTag("model_manager_search"),
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.errorMessage != null && uiState.providers.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    else -> {
                        ModelManagerList(
                            uiState = uiState,
                            viewModel = viewModel,
                        )
                    }
                }
            }

            HorizontalDivider()
            ModelManagerToolbar(
                uiState = uiState,
                onProbe = viewModel::probeCatalog,
                onSave = viewModel::save,
            )
        }
    }

    ToastEffect(
        toastMessage = uiState.toastMessage,
        onClearToast = viewModel::toastShown,
    )
}

@Composable
private fun ModelManagerList(
    uiState: ModelManagerUiState,
    viewModel: ModelManagerViewModel,
) {
    val visible = filterProviders(uiState.providers, uiState.customModels, uiState.query)
    if (visible.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.model_manager_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("model_manager_list")) {
        visible.forEach { (provider, models) ->
            item(key = "header_${provider.slug}") {
                ProviderHeader(provider = provider)
            }
            items(models, key = { "model_${provider.slug}_$it" }) { model ->
                ModelRow(
                    provider = provider,
                    model = model,
                    capabilities = viewModel.effectiveCapabilities(provider.slug, model),
                    isCustom = uiState.customModels.contains("${provider.slug}:$model"),
                    onToggle = { transform -> viewModel.setOverride(provider.slug, model, transform) },
                    onRemove = { viewModel.removeCustomModel(provider.slug, model) },
                )
            }
            item(key = "add_${provider.slug}") {
                AddModelRow(providerSlug = provider.slug, onAdd = viewModel::addCustomModel)
            }
        }
    }
}

@Composable
private fun ProviderHeader(provider: ModelProvider) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = provider.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (provider.authenticated == false) {
            Text(
                text = stringResource(R.string.model_manager_unauthenticated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ModelRow(
    provider: ModelProvider,
    model: String,
    capabilities: ModelCapabilities?,
    isCustom: Boolean,
    onToggle: ((CapabilityOverride) -> CapabilityOverride) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isCustom) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp).testTag("model_remove_${provider.slug}_$model"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.model_manager_remove_model),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CapabilityToggle(
                label = stringResource(R.string.model_manager_cap_reasoning),
                checked = capabilities?.reasoningSupport == true,
                onCheckedChange = { onToggle { o -> o.copy(reasoning = it) } },
                testTag = "cap_reasoning_${provider.slug}_$model",
            )
            CapabilityToggle(
                label = stringResource(R.string.model_manager_cap_tools),
                checked = capabilities?.supports_tools == true,
                onCheckedChange = { onToggle { o -> o.copy(tools = it) } },
                testTag = "cap_tools_${provider.slug}_$model",
            )
            CapabilityToggle(
                label = stringResource(R.string.model_manager_cap_vision),
                checked = capabilities?.supports_vision == true,
                onCheckedChange = { onToggle { o -> o.copy(vision = it) } },
                testTag = "cap_vision_${provider.slug}_$model",
            )
            CapabilityToggle(
                label = stringResource(R.string.model_manager_cap_fast),
                checked = capabilities?.fast == true,
                onCheckedChange = { onToggle { o -> o.copy(fast = it) } },
                testTag = "cap_fast_${provider.slug}_$model",
            )
        }
        capabilities?.context_window?.let { ctx ->
            Text(
                text = stringResource(R.string.model_manager_context_window, ctx),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CapabilityToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun AddModelRow(
    providerSlug: String,
    onAdd: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    if (!expanded) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(32.dp).testTag("model_add_expand_$providerSlug"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.model_manager_add_model),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.model_manager_add_model),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).testTag("model_add_field_$providerSlug"),
                placeholder = { Text(stringResource(R.string.model_manager_add_hint)) },
                singleLine = true,
            )
            IconButton(
                onClick = {
                    onAdd(providerSlug, draft)
                    draft = ""
                    expanded = false
                },
                modifier = Modifier.testTag("model_add_confirm_$providerSlug"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.model_manager_add_confirm),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = {
                draft = ""
                expanded = false
            }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
        }
    }
}

@Composable
private fun ModelManagerToolbar(
    uiState: ModelManagerUiState,
    onProbe: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onProbe,
            enabled = !uiState.isProbing,
            modifier = Modifier.testTag("model_manager_probe"),
        ) {
            if (uiState.isProbing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(R.string.model_manager_probe),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        val dirty = uiState.overrides.isNotEmpty() || uiState.customModels.isNotEmpty()
        FilledTonalButton(
            onClick = onSave,
            enabled = dirty && !uiState.isSaving,
            modifier = Modifier.testTag("model_manager_save"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.model_manager_save),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
