package com.m57.hermescontrol.ui.model.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.ModelCapabilities
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.SearchBar

/** Selection/test-tag keys are the shared "provider:model" pair. */
private fun modelKey(
    provider: String,
    model: String,
): String = "$provider:$model"

private fun keyToPinnedModel(key: String): PinnedModel {
    val split = key.indexOf(':')
    return if (split > 0) {
        PinnedModel(key.substring(0, split), key.substring(split + 1))
    } else {
        PinnedModel("", key)
    }
}

/**
 * Reusable model picker used by both the global model screen and the
 * in-session `/model` hot-swap (issue #589). Selecting a provider/model pair
 * invokes [onSelect]; the consumer decides what to do with it (global
 * `config.yaml` assignment vs. a session-scoped `/model` slash command).
 *
 * [pinnedModels] renders a pinned section at the top (mirrors the global model
 * screen's pin section) so frequently-used models are one tap away.
 * [onPinToggle] allows pinning and unpinning models directly inside the dialog.
 * Per-model reasoning capabilities (issue #946) are surfaced as a small hint
 * when `can_disable_reasoning == false` ("reasoning always on") or
 * `reasoning == false` ("no reasoning").
 *
 * Batch-8 F2 (selection parity with sessions): long-pressing a row enters
 * selection mode — the header swaps to an exit-cross, a selection count and
 * bulk Pin/Unpin + Archive/Unarchive actions. Archived models ([archivedModels])
 * are HIDDEN from the normal list; the header's archive-filter toggle (shown
 * when an archive is wired in) switches to an archived-only flat view for
 * unarchiving. Archive state is CLIENT-SIDE ONLY — [onBulkSetArchived] must
 * persist it locally, never via the server's `model_overrides` config.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelPickerDialog(
    providers: List<ModelProvider>,
    title: String,
    isLoading: Boolean = false,
    pinnedModels: List<PinnedModel> = emptyList(),
    archivedModels: List<PinnedModel> = emptyList(),
    onPinToggle: ((provider: String, model: String) -> Unit)? = null,
    onBulkSetPinned: (pins: List<PinnedModel>, pinned: Boolean) -> Unit = { _, _ -> },
    onBulkSetArchived: (models: List<PinnedModel>, archived: Boolean) -> Unit = { _, _ -> },
    onSelect: (provider: String, model: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickerQuery by remember { mutableStateOf("") }
    var expandedProviderSlug by remember { mutableStateOf<String?>(null) }
    // Batch-8 F2 — selection context. Non-empty set = selection mode; leaving
    // the mode is always exactly "clear the set" (X button or deselecting the
    // last row), so there is no separate mode flag to drift out of sync.
    var selectionKeys by remember { mutableStateOf(setOf<String>()) }
    var archivedView by rememberSaveable { mutableStateOf(false) }
    val selectionActive = selectionKeys.isNotEmpty()

    LaunchedEffect(archivedView) {
        // Flipping views invalidates selection meaning (archived ↔ normal rows
        // are not interchangeable), so entering/leaving archived view clears it.
        selectionKeys = emptySet()
    }

    // Full-bleed window (batch-5B B4): the picker fills the screen instead of
    // floating as a partial dialog; everything inside is unchanged.
    AlertDialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            if (selectionActive) {
                ModelPickerSelectionBar(
                    keys = selectionKeys,
                    pinnedSet = pinnedModels.map { modelKey(it.providerSlug, it.modelName) }.toSet(),
                    archivedView = archivedView,
                    onExit = { selectionKeys = emptySet() },
                    onBulkSetPinned = onBulkSetPinned,
                    onBulkSetArchived = onBulkSetArchived,
                    onAfter = { selectionKeys = emptySet() },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (archivedView) stringResource(R.string.model_picker_show_archived) else title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (archivedModels.isNotEmpty() || archivedView) {
                        IconButton(
                            onClick = { archivedView = !archivedView },
                            modifier = Modifier.size(32.dp).testTag("model_picker_archive_toggle"),
                        ) {
                            Icon(
                                imageVector =
                                    if (archivedView) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                contentDescription =
                                    stringResource(
                                        if (archivedView) {
                                            R.string.model_picker_hide_archived
                                        } else {
                                            R.string.model_picker_show_archived
                                        },
                                    ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.content_desc_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        text = {
            Column {
                if (isLoading && providers.isEmpty() && pinnedModels.isEmpty()) {
                    LoadingState(
                        subtitle = stringResource(R.string.model_picker_loading),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (providers.isEmpty() && pinnedModels.isEmpty()) {
                    Text(
                        text =
                            stringResource(
                                if (archivedView) {
                                    R.string.model_picker_archived_empty
                                } else {
                                    R.string.model_picker_no_models
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val pinnedSet =
                        remember(pinnedModels) {
                            pinnedModels.map { modelKey(it.providerSlug, it.modelName) }.toSet()
                        }
                    val archivedSet =
                        remember(archivedModels) {
                            archivedModels.map { modelKey(it.providerSlug, it.modelName) }.toSet()
                        }

                    val filteredPinned =
                        remember(pickerQuery, pinnedModels, archivedSet) {
                            pinnedModels
                                .filter { modelKey(it.providerSlug, it.modelName) !in archivedSet }
                                .filter {
                                    pickerQuery.isBlank() ||
                                        it.modelName.contains(pickerQuery, ignoreCase = true) ||
                                        it.providerSlug.contains(pickerQuery, ignoreCase = true)
                                }
                        }

                    val providerBySlug =
                        remember(providers) {
                            providers.associateBy { it.slug }
                        }

                    val filteredProvidersWithModels =
                        remember(pickerQuery, providers, archivedSet) {
                            providers.mapNotNull { provider ->
                                val matchingModels =
                                    provider.models.orEmpty().filter { model ->
                                        modelKey(provider.slug, model) !in archivedSet &&
                                            (
                                                pickerQuery.isBlank() ||
                                                    provider.name.contains(pickerQuery, ignoreCase = true) ||
                                                    provider.slug.contains(pickerQuery, ignoreCase = true) ||
                                                    model.contains(pickerQuery, ignoreCase = true)
                                            )
                                    }
                                if (matchingModels.isNotEmpty()) {
                                    provider to matchingModels
                                } else {
                                    null
                                }
                            }
                        }

                    val filteredArchived =
                        remember(pickerQuery, archivedModels) {
                            archivedModels.filter {
                                pickerQuery.isBlank() ||
                                    it.modelName.contains(pickerQuery, ignoreCase = true) ||
                                    it.providerSlug.contains(pickerQuery, ignoreCase = true)
                            }
                        }

                    LaunchedEffect(filteredProvidersWithModels) {
                        if (
                            expandedProviderSlug != null &&
                            filteredProvidersWithModels.none { (provider, _) ->
                                provider.slug == expandedProviderSlug
                            }
                        ) {
                            expandedProviderSlug = null
                        }
                    }

                    SearchBar(
                        query = pickerQuery,
                        onQueryChange = { pickerQuery = it },
                        placeholder = stringResource(R.string.model_picker_search),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (archivedView) {
                            // Archived-only flat view (batch-8 F2) — the only
                            // place archived models are visible, for unarchiving.
                            if (filteredArchived.isEmpty()) {
                                item(key = "archived-empty") {
                                    Text(
                                        text = stringResource(R.string.model_picker_archived_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                            items(
                                filteredArchived,
                                key = { "archived:${it.providerSlug}:${it.modelName}" },
                            ) { archived ->
                                val key = modelKey(archived.providerSlug, archived.modelName)
                                val caps = providerBySlug[archived.providerSlug]?.capabilities?.get(archived.modelName)
                                ModelItemCard(
                                    modelName = archived.modelName,
                                    isPinned = key in pinnedSet,
                                    onPinToggle = null,
                                    onClick =
                                        if (selectionActive) {
                                            {
                                                selectionKeys =
                                                    if (key in selectionKeys) {
                                                        selectionKeys - key
                                                    } else {
                                                        selectionKeys + key
                                                    }
                                            }
                                        } else {
                                            { onSelect(archived.providerSlug, archived.modelName) }
                                        },
                                    capabilities = caps,
                                    isSelected = key in selectionKeys,
                                    selectionActive = selectionActive,
                                    onLongSelect = { selectionKeys = selectionKeys + key },
                                )
                            }
                        } else {
                            // ── Pinned section ──
                            if (filteredPinned.isNotEmpty()) {
                                item(key = "pinned-header") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PushPin,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Pinned",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                items(
                                    filteredPinned,
                                    key = { "pinned:${it.providerSlug}:${it.modelName}" },
                                ) { pinned ->
                                    val key = modelKey(pinned.providerSlug, pinned.modelName)
                                    val caps =
                                        providerBySlug[pinned.providerSlug]
                                            ?.capabilities
                                            ?.get(pinned.modelName)
                                    ModelItemCard(
                                        modelName = pinned.modelName,
                                        isPinned = true,
                                        onPinToggle =
                                            if (onPinToggle != null) {
                                                { onPinToggle(pinned.providerSlug, pinned.modelName) }
                                            } else {
                                                null
                                            },
                                        onClick =
                                            if (selectionActive) {
                                                {
                                                    selectionKeys =
                                                        if (key in selectionKeys) {
                                                            selectionKeys - key
                                                        } else {
                                                            selectionKeys + key
                                                        }
                                                }
                                            } else {
                                                { onSelect(pinned.providerSlug, pinned.modelName) }
                                            },
                                        capabilities = caps,
                                        isSelected = key in selectionKeys,
                                        selectionActive = selectionActive,
                                        onLongSelect = { selectionKeys = selectionKeys + key },
                                    )
                                }
                            }

                            // ── All providers / models (lazy item per model) ──
                            filteredProvidersWithModels.forEach { (provider, models) ->
                                val isExpanded = expandedProviderSlug == provider.slug
                                item(key = "header:${provider.slug}") {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedProviderSlug =
                                                        if (isExpanded) {
                                                            null
                                                        } else {
                                                            provider.slug
                                                        }
                                                }.padding(top = 12.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = provider.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            ProviderUsageChip(provider)
                                        }
                                        Text(
                                            text =
                                                pluralStringResource(
                                                    R.plurals.model_provider_count,
                                                    models.size,
                                                    models.size,
                                                ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Icon(
                                            imageVector =
                                                if (isExpanded) {
                                                    Icons.Filled.KeyboardArrowUp
                                                } else {
                                                    Icons.Filled.KeyboardArrowDown
                                                },
                                            contentDescription =
                                                stringResource(
                                                    if (isExpanded) {
                                                        R.string.content_desc_collapse_provider
                                                    } else {
                                                        R.string.content_desc_expand_provider
                                                    },
                                                    provider.name,
                                                ),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    items(
                                        items = models,
                                        key = { model -> modelKey(provider.slug, model) },
                                    ) { model ->
                                        val key = modelKey(provider.slug, model)
                                        val isPinned = key in pinnedSet
                                        val caps = provider.capabilities?.get(model)
                                        ModelItemCard(
                                            modelName = model,
                                            isPinned = isPinned,
                                            onPinToggle =
                                                if (onPinToggle != null) {
                                                    { onPinToggle(provider.slug, model) }
                                                } else {
                                                    null
                                                },
                                            onClick =
                                                if (selectionActive) {
                                                    {
                                                        selectionKeys =
                                                            if (key in selectionKeys) {
                                                                selectionKeys - key
                                                            } else {
                                                                selectionKeys + key
                                                            }
                                                    }
                                                } else {
                                                    { onSelect(provider.slug, model) }
                                                },
                                            capabilities = caps,
                                            isSelected = key in selectionKeys,
                                            selectionActive = selectionActive,
                                            onLongSelect = { selectionKeys = selectionKeys + key },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * Selection-mode header (batch-8 F2, mirrors SessionsSheetSelectionBar):
 * leading cross exits selection mode, then the count, then the bulk actions.
 * Pin/Unpin adapts to the selection (Unpin only when EVERY selected model is
 * pinned); Archive/Unarchive adapts to the current view (archived-only view
 * offers Unarchive).
 */
@Composable
private fun ModelPickerSelectionBar(
    keys: Set<String>,
    pinnedSet: Set<String>,
    archivedView: Boolean,
    onExit: () -> Unit,
    onBulkSetPinned: (List<PinnedModel>, Boolean) -> Unit,
    onBulkSetArchived: (List<PinnedModel>, Boolean) -> Unit,
    onAfter: () -> Unit,
) {
    val allPinned = keys.all { it in pinnedSet }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onExit,
            modifier = Modifier.testTag("model_picker_selection_exit"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.model_picker_selection_exit),
            )
        }
        Text(
            text = stringResource(R.string.model_picker_selection_count, keys.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        IconButton(
            onClick = {
                onBulkSetPinned(keys.map(::keyToPinnedModel), !allPinned)
                onAfter()
            },
            enabled = keys.isNotEmpty(),
            modifier =
                Modifier.testTag(
                    if (allPinned) "model_picker_selection_unpin" else "model_picker_selection_pin",
                ),
        ) {
            Icon(
                imageVector =
                    if (allPinned) {
                        Icons.Filled.PushPin
                    } else {
                        Icons.Outlined.PushPin
                    },
                contentDescription =
                    stringResource(
                        if (allPinned) {
                            R.string.model_picker_selection_unpin
                        } else {
                            R.string.model_picker_selection_pin
                        },
                    ),
                tint =
                    if (allPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        IconButton(
            onClick = {
                onBulkSetArchived(keys.map(::keyToPinnedModel), !archivedView)
                onAfter()
            },
            enabled = keys.isNotEmpty(),
            modifier =
                Modifier.testTag(
                    if (archivedView) "model_picker_selection_unarchive" else "model_picker_selection_archive",
                ),
        ) {
            Icon(
                imageVector =
                    if (archivedView) {
                        Icons.Outlined.Unarchive
                    } else {
                        Icons.Outlined.Inventory2
                    },
                contentDescription =
                    stringResource(
                        if (archivedView) {
                            R.string.model_picker_selection_unarchive
                        } else {
                            R.string.model_picker_selection_archive
                        },
                    ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelItemCard(
    modelName: String,
    isPinned: Boolean,
    onPinToggle: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    capabilities: ModelCapabilities? = null,
    isSelected: Boolean = false,
    selectionActive: Boolean = false,
    onLongSelect: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongSelect,
                ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border =
            BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    // No maxLines/ellipsis: a long model name wraps so the full
                    // name stays readable while the pin button keeps its spot.
                )
                ModelCapabilitySummary(capabilities)
            }
            if (selectionActive) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.model_picker_row_selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else if (onPinToggle != null) {
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin model" else "Pin model",
                        tint =
                            if (isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
