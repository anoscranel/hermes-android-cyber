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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.reasoningSupport
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.ProviderCatalogProbe
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.chat.CustomProviderDraft
import com.m57.hermescontrol.ui.chat.ModelManagerViewModel
import com.m57.hermescontrol.ui.chat.ProbeResult
import com.m57.hermescontrol.ui.chat.ProbeStatus
import com.m57.hermescontrol.ui.chat.ProbeViewModel
import com.m57.hermescontrol.ui.chat.filterProviders
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.common.ToastEffect
import kotlinx.coroutines.launch

/**
 * Model manager (text models): per-provider catalog with keyword filter,
 * expandable rows exposing capability toggles (and set-as-main), swipe-right
 * rename / swipe-left remove-confirm on model rows, provider headers that
 * swipe right into the full edit window and left into remove-confirm (the
 * delete icon stays as the explicit affordance), add-model and add-provider
 * flows (the catalog probe lives inside the add flow, with a per-row Test
 * button that fires one real probe through [ProbeViewModel.probeSingle]), and
 * a bottom toolbar with the probe-testing sheet entry and the Save that
 * writes model_overrides/providers to the server config. Capability toggles
 * are write-through (batch-6 F2): they persist immediately via
 * [ModelManagerViewModel.applyCapabilityOverride], while renames, removals
 * and provider edits remain Save-gated.
 */
private object ModelManagerSheetMemory {
    /** Providers the user explicitly expanded; survives sheet closes. */
    var expandedProviders: Set<String> = emptySet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    onDismiss: () -> Unit,
    onOpenProbe: () -> Unit,
    runtimeSessionId: String? = null,
    isSessionReady: Boolean = false,
    currentModelLabel: String? = null,
    viewModel: ModelManagerViewModel = viewModel { ModelManagerViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current
    // Probe harness for the add-provider dialog's per-row Test button (batch-6
    // P3): same activity-scoped instance ProbeSheet uses.
    val probeViewModel: ProbeViewModel = viewModel { ProbeViewModel() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddProvider by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmRemoveProvider by remember { mutableStateOf<String?>(null) }
    var editProvider by remember { mutableStateOf<ModelProvider?>(null) }
    // Expanded set — providers start COLLAPSED by default (design: only an
    // explicit tap opens one) and the choice survives sheet closes within the
    // app session (design: an expanded provider stays expanded next open).
    var expandedProviders by remember { mutableStateOf(ModelManagerSheetMemory.expandedProviders) }

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

            // Batch-6 F3: Box wraps content width by default, so the center-
            // aligned spinner rendered against the LEFT edge; fillMaxWidth
            // makes Alignment.Center actually land in the screen's middle.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                uiState.hiddenProviders,
                                uiState.query,
                            )
                        LazyColumn(modifier = Modifier.fillMaxSize().testTag("model_manager_list")) {
                            visible.forEach { (provider, models) ->
                                val collapsed = provider.slug !in expandedProviders
                                item(key = "header_${provider.slug}") {
                                    ProviderHeader(
                                        provider = provider,
                                        collapsed = collapsed,
                                        onToggleCollapse = {
                                            expandedProviders =
                                                if (collapsed) {
                                                    expandedProviders + provider.slug
                                                } else {
                                                    expandedProviders - provider.slug
                                                }
                                            ModelManagerSheetMemory.expandedProviders = expandedProviders
                                        },
                                        onAddModel = { viewModel.addCustomModel(provider.slug, it) },
                                        onProbeProvider = { viewModel.probeCatalog() },
                                        onEditProvider = { editProvider = provider },
                                        onRemoveProvider = { confirmRemoveProvider = provider.slug },
                                    )
                                }
                                if (!collapsed) {
                                    items(models, key = { "model_${provider.slug}_$it" }) { model ->
                                        ModelRow(
                                            provider = provider,
                                            model = model,
                                            viewModel = viewModel,
                                            onRemove = { confirmRemove = provider.slug to model },
                                        )
                                    }
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
                        uiState.hiddenProviders.isNotEmpty() ||
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
            runtimeSessionId = runtimeSessionId,
            isSessionReady = isSessionReady,
            currentModelLabel = currentModelLabel,
            probeViewModel = probeViewModel,
        )
    }

    editProvider?.let { provider ->
        ProviderEditDialog(
            provider = provider,
            onDismiss = { editProvider = null },
            onSave = { draft ->
                viewModel.stageCustomProvider(draft)
                editProvider = null
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

    confirmRemoveProvider?.let { slug ->
        AlertDialog(
            onDismissRequest = { confirmRemoveProvider = null },
            title = { Text(stringResource(R.string.model_manager_remove_provider_title)) },
            text = { Text(stringResource(R.string.model_manager_remove_provider_message, slug)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stageRemoveProvider(slug)
                        confirmRemoveProvider = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("model_manager_remove_provider_confirm"),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveProvider = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    ToastEffect(toastMessage = uiState.toastMessage, onClearToast = viewModel::toastShown)
}

/**
 * Provider header row: tap = collapse toggle, swipe right = open the full
 * provider edit window, swipe left = remove (the same confirm popup the
 * delete icon raises — the icon stays as the explicit affordance, per spec).
 * Swipe handling copies the model-row pattern exactly (incl. the deprecated
 * confirmValueChange, accepted as a warning across the house sheets).
 */
@Composable
private fun ProviderHeader(
    provider: ModelProvider,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onAddModel: (String) -> Unit,
    onProbeProvider: () -> Unit,
    onEditProvider: () -> Unit,
    onRemoveProvider: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var adding by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onEditProvider()
                        false
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        onRemoveProvider()
                        false
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        true
                    }
                }
            },
        )
    Column(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm)) {
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.testTag("model_provider_swipe_${provider.slug}"),
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val (alignment, icon, tint) =
                    when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            Triple(Alignment.CenterStart, Icons.Outlined.Edit, MaterialTheme.colorScheme.primary)
                        }

                        SwipeToDismissBoxValue.EndToStart -> {
                            Triple(Alignment.CenterEnd, Icons.Outlined.Delete, MaterialTheme.colorScheme.error)
                        }

                        SwipeToDismissBoxValue.Settled -> {
                            Triple(Alignment.Center, Icons.Outlined.Edit, MaterialTheme.colorScheme.surface)
                        }
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(tint.copy(alpha = 0.15f))
                            .padding(horizontal = spacing.md),
                    contentAlignment = alignment,
                ) {
                    if (direction != SwipeToDismissBoxValue.Settled) {
                        Icon(imageVector = icon, contentDescription = null, tint = tint)
                    }
                }
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClick = onToggleCollapse)
                            .testTag("model_provider_toggle_${provider.slug}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = provider.name ?: provider.slug,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (provider.authenticated == false) {
                    Text(
                        text = stringResource(R.string.model_manager_unauthenticated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(
                    onClick = { adding = !adding },
                    modifier = Modifier.size(32.dp).testTag("model_add_toggle_${provider.slug}"),
                ) {
                    Icon(
                        imageVector = if (adding) Icons.Outlined.Close else Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.model_manager_add_model),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onRemoveProvider,
                    modifier = Modifier.size(32.dp).testTag("model_provider_remove_${provider.slug}"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.model_manager_remove_provider_title),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
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
    var showRename by rememberSaveable(model) { mutableStateOf(false) }
    // Batch-7 W3: bind the row to the StateFlow. effectiveCapabilities reads
    // _uiState.value directly — a plain snapshot read that never observes, so
    // without this collect the override write recomposed nothing and the
    // switch stayed one tap stale. remember(uiState) recomputes immediately.
    val uiState by viewModel.uiState.collectAsState()
    val displayName = remember(uiState) { viewModel.displayNameOverride(provider.slug, model) }
    val caps = remember(uiState) { viewModel.effectiveCapabilities(provider.slug, model) }
    val attachmentsOn = remember(uiState) { viewModel.effectiveAttachments(provider.slug, model) }
    // Swipe gestures mirror the sessions sheet: right = rename (display-name
    // override store), left = remove with the confirmation popup. Set-as-main
    // lives in the expanded capability block now (the rename gesture took the
    // gesture). The row never actually dismisses; the popup flows own both
    // actions.
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        showRename = true
                        false
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        onRemove()
                        false
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        true
                    }
                }
            },
        )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("model_swipe_${provider.slug}_$model"),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val (alignment, icon, tint) =
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Triple(Alignment.CenterStart, Icons.Outlined.Edit, MaterialTheme.colorScheme.primary)
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        Triple(Alignment.CenterEnd, Icons.Outlined.Delete, MaterialTheme.colorScheme.error)
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        Triple(Alignment.Center, Icons.Outlined.Edit, MaterialTheme.colorScheme.surface)
                    }
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(tint.copy(alpha = 0.15f))
                        .padding(horizontal = spacing.md),
                contentAlignment = alignment,
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(imageVector = icon, contentDescription = null, tint = tint)
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
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
                    text = displayName ?: model,
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
            }
            if (expanded) {
                // Set-as-main moved here from the right-swipe gesture when
                // rename took it over (batch 5A).
                TextButton(
                    onClick = { viewModel.setAsMain(provider.slug, model) },
                    modifier = Modifier.padding(start = 10.dp).testTag("model_set_main_${provider.slug}_$model"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.model_manager_set_main),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                // One compact horizontal row (batch-7 W3): letter glyph + a
                // 0.62-scaled switch per capability, equal-width cells, single
                // line. Letters follow the spec's V/T/R/A/O glyph set (R =
                // reasoning, T = tools, V = vision, A = attachments, O = the
                // remaining fast toggle); the full capability name rides on
                // the switch's contentDescription because the glyphs carry
                // no semantics.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Toggles are write-through (batch-6 F2): the tap applies
                    // to server config immediately, no Save step.
                    CapabilityToggle(
                        glyph = "R",
                        name = stringResource(R.string.model_manager_cap_reasoning),
                        checked = caps?.reasoningSupport == true,
                        tag = "cap_reasoning_${provider.slug}_$model",
                        onChange = {
                            viewModel.applyCapabilityOverride(provider.slug, model) { o -> o.copy(reasoning = it) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CapabilityToggle(
                        glyph = "T",
                        name = stringResource(R.string.model_manager_cap_tools),
                        checked = caps?.supports_tools == true,
                        tag = "cap_tools_${provider.slug}_$model",
                        onChange = {
                            viewModel.applyCapabilityOverride(provider.slug, model) { o -> o.copy(tools = it) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CapabilityToggle(
                        glyph = "V",
                        name = stringResource(R.string.model_manager_cap_vision),
                        checked = caps?.supports_vision == true,
                        tag = "cap_vision_${provider.slug}_$model",
                        onChange = {
                            viewModel.applyCapabilityOverride(provider.slug, model) { o -> o.copy(vision = it) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CapabilityToggle(
                        glyph = "A",
                        name = stringResource(R.string.model_manager_cap_attachments),
                        checked = attachmentsOn,
                        tag = "cap_attachments_${provider.slug}_$model",
                        onChange = {
                            viewModel.applyCapabilityOverride(provider.slug, model) { o -> o.copy(attachments = it) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CapabilityToggle(
                        glyph = "O",
                        name = stringResource(R.string.model_manager_cap_fast),
                        checked = caps?.fast == true,
                        tag = "cap_fast_${provider.slug}_$model",
                        onChange = {
                            viewModel.applyCapabilityOverride(provider.slug, model) { o -> o.copy(fast = it) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    if (showRename) {
        // Reset the draft every time the dialog opens (key on showRename),
        // starting from the staged display name when there is one.
        var renameDraft by rememberSaveable(showRename) { mutableStateOf(displayName ?: model) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.model_manager_rename_model_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.model_manager_rename_model_hint)) },
                    modifier = Modifier.testTag("model_rename_field_${provider.slug}_$model"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Blank or unchanged input clears the override: the row
                        // falls back to the catalog id on the next render.
                        val staged = renameDraft.trim()
                        viewModel.setOverride(provider.slug, model) { o ->
                            o.copy(displayName = if (staged.isEmpty() || staged == model) null else staged)
                        }
                        showRename = false
                    },
                    modifier = Modifier.testTag("model_rename_confirm_${provider.slug}_$model"),
                ) {
                    Text(stringResource(R.string.sessions_action_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CapabilityToggle(
    glyph: String,
    name: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier =
                Modifier
                    .scale(0.62f)
                    .semantics { contentDescription = name }
                    .testTag(tag),
        )
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
    runtimeSessionId: String?,
    isSessionReady: Boolean,
    currentModelLabel: String?,
    probeViewModel: ProbeViewModel,
) {
    var slug by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var models by rememberSaveable { mutableStateOf("") }
    // Live catalog probe (batch 5A): hits {baseUrl}/models through the app's
    // HTTP client (see ProviderCatalogProbe — the WS probe harness in
    // ProbeSheet/ProbeViewModel is session-bound and cannot generalize to a
    // not-yet-configured provider). Keyword filters as you type; checked rows
    // merge into the models list on confirm, alongside text-typed ones.
    var probeKeyword by rememberSaveable { mutableStateOf("") }
    var probing by remember { mutableStateOf(false) }
    var probedCatalog by remember { mutableStateOf<List<String>?>(null) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var checkedModels by remember { mutableStateOf(emptySet<String>()) }
    val probeScope = rememberCoroutineScope()
    // Per-row Test (batch-6 P3): a single real WS probe of that provider:model
    // through ProbeViewModel.probeSingle, result rendered inline on the row.
    val probeUiState by probeViewModel.uiState.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_manager_add_provider)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                TextButton(
                    onClick = {
                        probeScope.launch {
                            probing = true
                            probeError = null
                            when (val result = ProviderCatalogProbe.fetchModels(baseUrl, apiKey)) {
                                is NetworkResult.Success -> {
                                    probedCatalog = result.data
                                    checkedModels = emptySet()
                                }

                                is NetworkResult.Failure -> {
                                    probedCatalog = emptyList()
                                    probeError = result.error.message
                                }
                            }
                            probing = false
                        }
                    },
                    enabled = baseUrl.isNotBlank() && !probing,
                    modifier = Modifier.testTag("provider_probe_catalog"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.model_manager_probe_catalog),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (probing) {
                    // Batch-6 F3: center the probing row across the dialog width.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.model_manager_probing),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                probeError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val catalog = probedCatalog
                if (!probing && catalog == null && probeError == null) {
                    Text(
                        text = stringResource(R.string.model_manager_probe_add_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!probing && catalog != null && catalog.isEmpty() && probeError == null) {
                    Text(
                        text = stringResource(R.string.model_manager_probe_empty_result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CatalogChecklist(
                    catalog = catalog ?: emptyList(),
                    probeKeyword = probeKeyword,
                    onKeywordChange = { probeKeyword = it },
                    checkedModels = checkedModels,
                    onToggle = { catalogId ->
                        checkedModels =
                            if (catalogId in checkedModels) {
                                checkedModels - catalogId
                            } else {
                                checkedModels + catalogId
                            }
                    },
                    providerSlug = slug,
                    testResults = probeUiState.results,
                    probesRunning = probeUiState.isRunning,
                    testsEnabled = isSessionReady,
                    onTest = { catalogId ->
                        probeViewModel.probeSingle(
                            runtimeSessionId,
                            currentModelLabel,
                            slug.trim(),
                            catalogId,
                        )
                    },
                )
                OutlinedTextField(
                    value = models,
                    onValueChange = { models = it },
                    label = { Text(stringResource(R.string.model_manager_provider_models)) },
                    modifier = Modifier.fillMaxWidth().testTag("provider_field_models"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val manualModels = models.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(
                        CustomProviderDraft(
                            slug = slug.trim(),
                            displayName = name.trim().ifEmpty { slug.trim() },
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            models = (manualModels + checkedModels).distinct(),
                            discoverModels = true,
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

/**
 * The probed catalog list inside the add-provider dialog: a keyword filter box
 * (case-insensitive contains over the catalog id) and one checkbox row per
 * model. Checked rows merge into the provider's model list on confirm, on top
 * of whatever was typed by hand. Empty (or unprobed) catalogs render nothing —
 * the dialog owns the loading/error/hint rows around this.
 */
@Composable
private fun CatalogChecklist(
    catalog: List<String>,
    probeKeyword: String,
    onKeywordChange: (String) -> Unit,
    checkedModels: Set<String>,
    onToggle: (String) -> Unit,
    providerSlug: String,
    testResults: Map<String, ProbeResult>,
    probesRunning: Boolean,
    testsEnabled: Boolean,
    onTest: (String) -> Unit,
) {
    if (catalog.isEmpty()) return
    OutlinedTextField(
        value = probeKeyword,
        onValueChange = onKeywordChange,
        label = { Text(stringResource(R.string.model_manager_probe_filter)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("provider_probe_filter"),
    )
    val visibleCatalog =
        catalog.filter {
            probeKeyword.isBlank() || it.contains(probeKeyword.trim(), ignoreCase = true)
        }
    if (visibleCatalog.isEmpty()) {
        Text(
            text = stringResource(R.string.model_manager_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        visibleCatalog.forEach { catalogId ->
            val isChecked = catalogId in checkedModels
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(catalogId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggle(catalogId) },
                    modifier = Modifier.testTag("provider_probe_check_$catalogId"),
                )
                Text(
                    text = catalogId,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CatalogRowTest(
                    result = testResults["$providerSlug:$catalogId"],
                    enabled = testsEnabled && !probesRunning && providerSlug.isNotBlank(),
                    onTest = { onTest(catalogId) },
                    tag = "provider_probe_test_$catalogId",
                )
            }
        }
    }
}

/**
 * Per-catalog-row probe test (batch-6 P3): tap runs ONE real probe of that
 * provider:model through the shared probe harness. The row shows a spinner
 * while running, a green latency when ok, and red on failure — same status
 * vocabulary as the probe sheet.
 */
@Composable
private fun CatalogRowTest(
    result: ProbeResult?,
    enabled: Boolean,
    onTest: () -> Unit,
    tag: String,
) {
    val statusColors = LocalHermesStatusColors.current
    when (result?.status) {
        ProbeStatus.RUNNING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp).testTag(tag),
                strokeWidth = 2.dp,
            )
        }

        else -> {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = onTest,
                    enabled = enabled,
                    modifier = Modifier.testTag(tag),
                ) {
                    Text(stringResource(R.string.model_manager_probe_row_test))
                }
                if (result?.status == ProbeStatus.OK) {
                    val latency = result?.latencyMs
                    Text(
                        text =
                            if (latency != null) {
                                stringResource(R.string.probe_row_test_ok, latency)
                            } else {
                                stringResource(R.string.probe_status_ok)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColors.success,
                    )
                } else if (result?.status == ProbeStatus.FAILED) {
                    Text(
                        text = stringResource(R.string.probe_status_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColors.error,
                    )
                }
            }
        }
    }
}

/**
 * Full provider edit window (batch 5A, provider-header right-swipe): a
 * full-screen dialog — not a bottom sheet — editing the provider config. Save
 * stages through [ModelManagerViewModel.stageCustomProvider] (the single
 * `providers.<name>` write path, NOT a second persistence fork); a blank base URL
 * keeps the server's current endpoint, a blank API key keeps the stored
 * secret, and the enabled switch maps to the documented
 * `providers.<name>.enabled` key.
 */
@Composable
private fun ProviderEditDialog(
    provider: ModelProvider,
    onDismiss: () -> Unit,
    onSave: (CustomProviderDraft) -> Unit,
) {
    val spacing = LocalSpacing.current
    var name by rememberSaveable(provider.slug) { mutableStateOf(provider.name) }
    var baseUrl by rememberSaveable(provider.slug) { mutableStateOf("") }
    var apiKey by rememberSaveable(provider.slug) { mutableStateOf("") }
    var modelsDraft by rememberSaveable(provider.slug) { mutableStateOf(provider.models.orEmpty().joinToString(", ")) }
    var enabled by rememberSaveable(provider.slug) { mutableStateOf(true) }
    var revealKey by remember(provider.slug) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("provider_edit_close"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                    Text(
                        text = stringResource(R.string.model_manager_edit_provider),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    TextButton(
                        onClick = {
                            onSave(
                                CustomProviderDraft(
                                    slug = provider.slug,
                                    displayName = name.trim().ifEmpty { provider.slug },
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    models =
                                        modelsDraft
                                            .split(',')
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() },
                                    enabled = enabled,
                                ),
                            )
                        },
                        modifier = Modifier.testTag("provider_edit_save"),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.model_manager_provider_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("provider_edit_name"),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.model_manager_provider_base_url)) },
                        supportingText = { Text(stringResource(R.string.model_manager_provider_base_url_keep)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("provider_edit_base_url"),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.model_manager_provider_api_key_keep)) },
                        singleLine = true,
                        visualTransformation =
                            if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { revealKey = !revealKey },
                                modifier = Modifier.testTag("provider_edit_api_key_reveal"),
                            ) {
                                Icon(
                                    imageVector =
                                        if (revealKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("provider_edit_api_key"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.model_manager_provider_enabled),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            modifier = Modifier.testTag("provider_edit_enabled"),
                        )
                    }
                    OutlinedTextField(
                        value = modelsDraft,
                        onValueChange = { modelsDraft = it },
                        label = { Text(stringResource(R.string.model_manager_provider_models)) },
                        modifier = Modifier.fillMaxWidth().testTag("provider_edit_models"),
                    )
                }
            }
        }
    }
}
