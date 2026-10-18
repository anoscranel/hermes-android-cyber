package com.m57.hermescontrol.ui.chat.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.chat.CustomProviderDraft
import com.m57.hermescontrol.ui.chat.ModelManagerViewModel
import com.m57.hermescontrol.ui.chat.filterProviders
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.common.ToastEffect

/**
 * Model manager (text models): per-provider catalog with keyword filter,
 * expandable rows exposing capability toggles, per-row remove with confirm,
 * add-model and add-provider flows (the catalog probe lives inside the add
 * flows only), and a bottom toolbar with the probe-testing sheet entry and
 * the Save that writes model_overrides/providers to the server config.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    onDismiss: () -> Unit,
    onOpenProbe: () -> Unit,
    viewModel: ModelManagerViewModel = viewModel { ModelManagerViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddProvider by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("model_manager_sheet"),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.md)) {
            Text(
                text = stringResource(R.string.model_manager_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = spacing.sm),
            )
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
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    uiState.errorMessage != null -> {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        val visible =
                            filterProviders(
                                uiState.providers,
                                uiState.customModels,
                                uiState.hiddenModels,
                                uiState.query,
                            )
                        LazyColumn(modifier = Modifier.fillMaxSize().testTag("model_manager_list")) {
                            visible.forEach { (provider, models) ->
                                item(key = "header_${provider.slug}") {
                                    ProviderHeader(
                                        provider = provider,
                                        onAddModel = { viewModel.addCustomModel(provider.slug, it) },
                                        onProbeProvider = { viewModel.probeCatalog() },
                                    )
                                }
                                items(models, key = { "model_${provider.slug}_$it" }) { model ->
                                    ModelRow(
                                        provider = provider,
                                        model = model,
                                        viewModel = viewModel,
                                        onRemove = { confirmRemove = provider.slug to model },
                                    )
                                }
                            }
                            item(key = "add_provider") {
                                AddProviderRow(onClick = { showAddProvider = true })
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onOpenProbe,
                    modifier = Modifier.testTag("model_manager_open_probe"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.model_manager_probe),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val dirty =
                    uiState.overrides.isNotEmpty() ||
                        uiState.customModels.isNotEmpty() ||
                        uiState.hiddenModels.isNotEmpty() ||
                        uiState.customProviders.isNotEmpty()
                FilledTonalButton(
                    onClick = { viewModel.save() },
                    enabled = dirty && !uiState.isSaving,
                    modifier = Modifier.testTag("model_manager_save"),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.model_manager_save),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }

    if (showAddProvider) {
        AddProviderDialog(
            onDismiss = { showAddProvider = false },
            onConfirm = { draft ->
                viewModel.stageCustomProvider(draft)
                showAddProvider = false
            },
        )
    }

    confirmRemove?.let { (slug, model) ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.model_manager_remove_title)) },
            text = { Text(stringResource(R.string.model_manager_remove_message, model)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stageRemoveModel(slug, model)
                        confirmRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("model_manager_remove_confirm"),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    ToastEffect(toastMessage = uiState.toastMessage, onClearToast = viewModel::toastShown)
}

@Composable
private fun ProviderHeader(
    provider: ModelProvider,
    onAddModel: (String) -> Unit,
    onProbeProvider: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var adding by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = provider.name ?: provider.slug,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (provider.authenticated == false) {
                Text(
                    text = stringResource(R.string.model_manager_unauthenticated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(
                onClick = { adding = !adding },
                modifier = Modifier.testTag("model_add_toggle_${provider.slug}"),
            ) {
                Icon(
                    imageVector = if (adding) Icons.Outlined.Close else Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.model_manager_add_model),
                )
            }
        }
        if (adding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).testTag("model_add_field_${provider.slug}"),
                    placeholder = { Text(stringResource(R.string.model_manager_add_hint)) },
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onAddModel(draft.trim())
                            draft = ""
                            adding = false
                        }
                    },
                    modifier = Modifier.testTag("model_add_confirm_${provider.slug}"),
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                }
                IconButton(onClick = onProbeProvider, modifier = Modifier.testTag("model_add_probe_${provider.slug}")) {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = stringResource(R.string.model_manager_probe_catalog),
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ModelRow(
    provider: ModelProvider,
    model: String,
    viewModel: ModelManagerViewModel,
    onRemove: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var expanded by rememberSaveable(model) { mutableStateOf(false) }
    val caps = viewModel.effectiveCapabilities(provider.slug, model)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp)
                    .testTag("model_row_${provider.slug}_$model"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = model,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            caps?.context_window?.let { ctx ->
                Text(
                    text = stringResource(R.string.model_manager_context_window, ctx),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp).testTag("model_remove_${provider.slug}_$model"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.model_manager_remove_title),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 22.dp, bottom = 6.dp)) {
                CapabilityToggle(
                    label = stringResource(R.string.model_manager_cap_reasoning),
                    checked = caps?.reasoningSupport == true,
                    tag = "cap_reasoning_${provider.slug}_$model",
                    onChange = { viewModel.setOverride(provider.slug, model) { o -> o.copy(reasoning = it) } },
                )
                CapabilityToggle(
                    label = stringResource(R.string.model_manager_cap_tools),
                    checked = caps?.supports_tools == true,
                    tag = "cap_tools_${provider.slug}_$model",
                    onChange = { viewModel.setOverride(provider.slug, model) { o -> o.copy(tools = it) } },
                )
                CapabilityToggle(
                    label = stringResource(R.string.model_manager_cap_vision),
                    checked = caps?.supports_vision == true,
                    tag = "cap_vision_${provider.slug}_$model",
                    onChange = { viewModel.setOverride(provider.slug, model) { o -> o.copy(vision = it) } },
                )
                CapabilityToggle(
                    label = stringResource(R.string.model_manager_cap_attachments),
                    checked = viewModel.effectiveAttachments(provider.slug, model),
                    tag = "cap_attachments_${provider.slug}_$model",
                    onChange = { viewModel.setOverride(provider.slug, model) { o -> o.copy(attachments = it) } },
                )
                CapabilityToggle(
                    label = stringResource(R.string.model_manager_cap_fast),
                    checked = caps?.fast == true,
                    tag = "cap_fast_${provider.slug}_$model",
                    onChange = { viewModel.setOverride(provider.slug, model) { o -> o.copy(fast = it) } },
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun CapabilityToggle(
    label: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun AddProviderRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp)
                .testTag("model_manager_add_provider"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.model_manager_add_provider),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (CustomProviderDraft) -> Unit,
) {
    var slug by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var models by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_manager_add_provider)) },
        text = {
            Column {
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it },
                    label = { Text(stringResource(R.string.model_manager_provider_slug)) },
                    singleLine = true,
                    modifier = Modifier.testTag("provider_field_slug"),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.model_manager_provider_name)) },
                    singleLine = true,
                    modifier = Modifier.testTag("provider_field_name"),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.model_manager_provider_base_url)) },
                    singleLine = true,
                    modifier = Modifier.testTag("provider_field_base_url"),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.model_manager_provider_api_key)) },
                    singleLine = true,
                    modifier = Modifier.testTag("provider_field_api_key"),
                )
                OutlinedTextField(
                    value = models,
                    onValueChange = { models = it },
                    label = { Text(stringResource(R.string.model_manager_provider_models)) },
                    modifier = Modifier.testTag("provider_field_models"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        CustomProviderDraft(
                            slug = slug.trim(),
                            displayName = name.trim().ifEmpty { slug.trim() },
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            models = models.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        ),
                    )
                },
                enabled = slug.isNotBlank() && baseUrl.isNotBlank(),
                modifier = Modifier.testTag("provider_add_confirm"),
            ) {
                Text(stringResource(R.string.model_manager_add_provider))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
