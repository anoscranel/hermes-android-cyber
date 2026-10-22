package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.common.ConfirmDialog
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.sessions.HistorySection
import com.m57.hermescontrol.ui.sessions.SessionsUiState
import com.m57.hermescontrol.ui.sessions.SessionsViewModel
import com.m57.hermescontrol.ui.sessions.activityEpochSeconds
import com.m57.hermescontrol.ui.sessions.cleanSearchSnippet
import com.m57.hermescontrol.ui.sessions.formatPlayedAt
import com.m57.hermescontrol.ui.sessions.toSessionInfo

/**
 * Remembers where the sheet left off across opens within the process: the search
 * query and the list scroll position. A plain object is fine here — the sheet is
 * transient UI state, not data that must survive process death.
 */
private object SessionsSheetMemory {
    var query: String = ""
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0
    var previousSessionId: String? = null
}

/**
 * Quick-switcher bottom sheet: floats over the chat UI so tapping a session swaps the
 * active chat in place via [NavigationController.openChatSession] instead of navigating
 * away. Owns its own [SessionsViewModel] (all-default ctor) scoped to the sheet.
 *
 * Row gestures: swipe right = quick actions (rename / pin / archive), swipe left =
 * delete with confirmation, long-press = selection mode. The archived-only toggle
 * filters server-side via the archivedQuery-backed reload (see the ViewModel).
 * Selection mode swaps the toolbar for a cross-exit + "<n> selected" + bulk
 * action bar (delete via the backend bulk op; archive/unarchive per-id PATCH).
 * Pinned sessions sort first with a divider.
 * Bottom toolbar: back-to-last, refresh, pinned-only filter, archived-only
 * filter, new chat, and a search button that swaps the toolbar for the search
 * field (focus + keyboard only then).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsSheet(
    onDismiss: () -> Unit,
    initialSection: HistorySection = HistorySection.CONVERSATIONS,
    currentSessionId: String? = null,
    onOpenAutomations: () -> Unit = {},
    viewModel: SessionsViewModel = viewModel { SessionsViewModel() },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current

    var searchVisible by remember { mutableStateOf(false) }
    var pinnedOnly by remember { mutableStateOf(false) }
    // Archived view is server-side (backend archived=only), so the toggle rides
    // on the ViewModel's showArchived and triggers a reload keyed by
    // archivedQuery — bulk unarchive from selection mode relies on the same
    // refresh contract to pop rows back into the main list.
    val archivedOnly = uiState.showArchived
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // The automations composer button opens the same sheet pre-filtered to
    // cron-sourced runs; selectSection must run before any load so the first
    // page is fetched with the right source filter. selectSection already
    // calls loadSessions() internally when the section changes (fresh VM +
    // AUTOMATIONS), so only load explicitly when it early-returned (the
    // CONVERSATIONS default no-op) — avoids a redundant page-0 fetch.
    LaunchedEffect(Unit) {
        val wasDefault = viewModel.uiState.value.section == initialSection
        viewModel.selectSection(initialSection)
        if (wasDefault) viewModel.loadSessions()
        // Restore a remembered query without popping the keyboard: the field
        // only takes focus when the user explicitly taps the search button.
        if (SessionsSheetMemory.query.isNotBlank()) {
            viewModel.setSearchQuery(SessionsSheetMemory.query)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("sessions_sheet"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading && !uiState.isSearchMode -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.errorMessage != null && !uiState.isSearchMode -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    else -> {
                        SessionsSheetList(
                            uiState = uiState,
                            currentSessionId = currentSessionId,
                            pinnedOnly = pinnedOnly,
                            archivedOnly = archivedOnly,
                            onSessionTap = { sessionId ->
                                // Row taps toggle while selecting; navigation only
                                // when no selection is in progress.
                                if (uiState.isSelecting) {
                                    viewModel.toggleSessionSelection(sessionId)
                                } else {
                                    // Remember the chat we are leaving so the toolbar can
                                    // offer a one-tap jump back.
                                    if (currentSessionId != null && currentSessionId != sessionId) {
                                        SessionsSheetMemory.previousSessionId = currentSessionId
                                    }
                                    NavigationController.openChatSession(sessionId)
                                    onDismiss()
                                }
                            },
                            onRename = { session ->
                                viewModel.openRenameDialog(session.id, sheetDisplayTitle(session))
                            },
                            onTogglePin = { session -> viewModel.togglePin(session.id) },
                            onToggleArchive = { session -> viewModel.toggleArchive(session.id) },
                            onDelete = { session -> viewModel.requestDeleteSession(session.id) },
                            onStartSelection = { session ->
                                // Long-press enters selection mode with this row
                                // checked (mirrors the Sessions screen's onSelect).
                                viewModel.toggleSelecting()
                                viewModel.toggleSessionSelection(session.id)
                            },
                            onLoadMoreSearch = viewModel::loadMoreSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (uiState.isSelecting) {
                // Selection mode replaces the toolbar wholesale (by design):
                // leading cross exits, then "<n> selected", then bulk actions.
                SessionsSheetSelectionBar(
                    selectedCount = uiState.selectedIds.size,
                    archivedView = archivedOnly,
                    onExit = { viewModel.toggleSelecting() },
                    onArchive = { viewModel.setArchivedSelected(true) },
                    onUnarchive = { viewModel.setArchivedSelected(false) },
                    onDelete = { viewModel.requestBulkDelete() },
                )
            } else if (searchVisible) {
                SheetSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = {
                        SessionsSheetMemory.query = it
                        viewModel.setSearchQuery(it)
                    },
                    onClose = {
                        searchVisible = false
                        keyboardController?.hide()
                        if (uiState.searchQuery.isNotBlank()) {
                            SessionsSheetMemory.query = ""
                            viewModel.setSearchQuery("")
                        }
                    },
                    focusRequester = searchFocusRequester,
                )
            } else {
                SessionsSheetToolbar(
                    backTarget = sheetBackTarget(uiState, currentSessionId),
                    pinnedOnly = pinnedOnly,
                    archivedOnly = archivedOnly,
                    showAutomationsShortcut = initialSection == HistorySection.CONVERSATIONS,
                    currentSession =
                        uiState.sessions.firstOrNull { it.id == currentSessionId },
                    onBackToLast = { target ->
                        NavigationController.openChatSession(target.id)
                        onDismiss()
                    },
                    onRefresh = { viewModel.loadSessions(forceRefresh = true) },
                    onTogglePinnedOnly = { pinnedOnly = !pinnedOnly },
                    onToggleArchivedOnly = { viewModel.toggleShowArchived() },
                    onAutomationsTap = onOpenAutomations,
                    onRenameCurrent = { session ->
                        viewModel.openRenameDialog(session.id, sheetDisplayTitle(session))
                    },
                    onPinCurrent = { session -> viewModel.togglePin(session.id) },
                    onArchiveCurrent = { session -> viewModel.toggleArchive(session.id) },
                    onNewChat = {
                        NavigationController.openNewChat()
                        onDismiss()
                    },
                    onSearchTap = { searchVisible = true },
                )
            }
        }

        SessionsSheetDialogs(viewModel = viewModel, uiState = uiState)
    }
}

/**
 * Bottom action row: back-to-last (left), utilities (middle), search (right).
 * Includes an automations shortcut (since the composer button became a hold
 * gesture), a current-chat quick-actions menu, and an archived-only filter.
 */
@Composable
private fun SessionsSheetToolbar(
    backTarget: SessionInfo?,
    pinnedOnly: Boolean,
    archivedOnly: Boolean,
    showAutomationsShortcut: Boolean,
    currentSession: SessionInfo?,
    onBackToLast: (SessionInfo) -> Unit,
    onRefresh: () -> Unit,
    onTogglePinnedOnly: () -> Unit,
    onToggleArchivedOnly: () -> Unit,
    onAutomationsTap: () -> Unit,
    onRenameCurrent: (SessionInfo) -> Unit,
    onPinCurrent: (SessionInfo) -> Unit,
    onArchiveCurrent: (SessionInfo) -> Unit,
    onNewChat: () -> Unit,
    onSearchTap: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (backTarget != null) {
            IconButton(
                onClick = { onBackToLast(backTarget) },
                modifier = Modifier.testTag("sessions_sheet_back_to_last"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = stringResource(R.string.sessions_sheet_back_to_last),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.sessions_sheet_refresh),
            )
        }
        IconButton(onClick = onTogglePinnedOnly) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = stringResource(R.string.sessions_sheet_pinned_only),
                tint =
                    if (pinnedOnly) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        IconButton(onClick = onToggleArchivedOnly) {
            Icon(
                imageVector = Icons.Outlined.Inventory2,
                contentDescription = stringResource(R.string.sessions_sheet_archived_only),
                tint =
                    if (archivedOnly) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        if (showAutomationsShortcut) {
            IconButton(
                onClick = onAutomationsTap,
                modifier = Modifier.testTag("sessions_sheet_automations"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.EventRepeat,
                    contentDescription = stringResource(R.string.chat_open_automations),
                )
            }
        }
        if (currentSession != null) {
            var showCurrentActions by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showCurrentActions = true },
                    modifier = Modifier.testTag("sessions_sheet_current_actions"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.sessions_sheet_current_actions),
                    )
                }
                DropdownMenu(
                    expanded = showCurrentActions,
                    onDismissRequest = { showCurrentActions = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sessions_rename_title)) },
                        onClick = {
                            showCurrentActions = false
                            onRenameCurrent(currentSession)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (currentSession.pinned == true) {
                                        R.string.sessions_action_unpin
                                    } else {
                                        R.string.sessions_action_pin
                                    },
                                ),
                            )
                        },
                        onClick = {
                            showCurrentActions = false
                            onPinCurrent(currentSession)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (currentSession.archived == true) {
                                        R.string.sessions_action_unarchive
                                    } else {
                                        R.string.sessions_action_archive
                                    },
                                ),
                            )
                        },
                        onClick = {
                            showCurrentActions = false
                            onArchiveCurrent(currentSession)
                        },
                    )
                }
            }
        }
        IconButton(onClick = onNewChat) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.sessions_sheet_new_chat),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onSearchTap,
            modifier = Modifier.testTag("sessions_sheet_search_button"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.sessions_search_placeholder),
            )
        }
    }
}

/**
 * Selection-mode toolbar (replaces the normal toolbar and the search bar while
 * selecting): leading cross exits mode, then the "<n> selected" count, then the
 * bulk actions. Bulk ops route through the same ViewModel paths as the row
 * gestures — delete goes through the backend bulk-delete op (with the confirm
 * dialog); archive/unarchive apply the per-id PATCH to every selected id.
 */
@Composable
private fun SessionsSheetSelectionBar(
    selectedCount: Int,
    archivedView: Boolean,
    onExit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val hasSelection = selectedCount > 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onExit,
            modifier = Modifier.testTag("sessions_sheet_selection_exit"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.sessions_sheet_selection_exit),
            )
        }
        Text(
            text = stringResource(R.string.sessions_sheet_selection_count, selectedCount),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (archivedView) {
            IconButton(
                onClick = onUnarchive,
                enabled = hasSelection,
                modifier = Modifier.testTag("sessions_sheet_selection_unarchive"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Unarchive,
                    contentDescription = stringResource(R.string.sessions_sheet_selection_unarchive),
                )
            }
        } else {
            IconButton(
                onClick = onArchive,
                enabled = hasSelection,
                modifier = Modifier.testTag("sessions_sheet_selection_archive"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = stringResource(R.string.sessions_sheet_selection_archive),
                )
            }
        }
        IconButton(
            onClick = onDelete,
            enabled = hasSelection,
            modifier = Modifier.testTag("sessions_sheet_selection_delete"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.sessions_sheet_selection_delete),
                tint =
                    if (hasSelection) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/** The search field that replaces the toolbar; grabs focus + keyboard on show. */
@Composable
private fun SheetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
) {
    val spacing = LocalSpacing.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            placeholder = stringResource(R.string.sessions_search_placeholder),
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_cancel),
            )
        }
    }
}

@Composable
private fun SessionsSheetList(
    uiState: SessionsUiState,
    currentSessionId: String?,
    pinnedOnly: Boolean,
    archivedOnly: Boolean,
    onSessionTap: (String) -> Unit,
    onRename: (SessionInfo) -> Unit,
    onTogglePin: (SessionInfo) -> Unit,
    onToggleArchive: (SessionInfo) -> Unit,
    onDelete: (SessionInfo) -> Unit,
    onStartSelection: (SessionInfo) -> Unit,
    onLoadMoreSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = SessionsSheetMemory.scrollIndex,
            initialFirstVisibleItemScrollOffset = SessionsSheetMemory.scrollOffset,
        )
    val spacing = LocalSpacing.current

    // Persist scroll position continuously so a dismiss-by-tap-outside keeps it too.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                SessionsSheetMemory.scrollIndex = index
                SessionsSheetMemory.scrollOffset = offset
            }
    }

    val displaySessions: List<SessionInfo> =
        remember(uiState.isSearchMode, uiState.sessions, uiState.searchResults, pinnedOnly, archivedOnly) {
            val base =
                if (uiState.isSearchMode) {
                    uiState.searchResults.map { it.toSessionInfo() }
                } else {
                    uiState.sessions
                }
            // Archived sessions stay out of the normal list entirely; the
            // archived-only toggle flips the list to just them.
            val visible =
                if (archivedOnly) {
                    base.filter { it.archived == true }
                } else {
                    base.filter { it.archived != true }
                }
            if (pinnedOnly) visible.filter { it.pinned == true } else visible
        }

    // Pinned first, then the rest; both groups keep their server order.
    val pinnedSessions = remember(displaySessions) { displaySessions.filter { it.pinned == true } }
    val unpinnedSessions = remember(displaySessions) { displaySessions.filter { it.pinned != true } }

    // Infinite scroll for search results only; the raw list pages itself internally.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            uiState.isSearchMode &&
                uiState.searchHasMore &&
                !uiState.isLoadingMoreSearch &&
                lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore }.collect { if (it) onLoadMoreSearch() }
    }

    if (displaySessions.isEmpty() && !uiState.isSearching) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.history_untitled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
    ) {
        items(pinnedSessions, key = { it.id }) { session ->
            SessionSheetRow(
                session = session,
                isSelecting = uiState.isSelecting,
                isSelected = session.id in uiState.selectedIds,
                onClick = { onSessionTap(session.id) },
                onRename = { onRename(session) },
                onTogglePin = { onTogglePin(session) },
                onToggleArchive = { onToggleArchive(session) },
                onDelete = { onDelete(session) },
                onStartSelection = { onStartSelection(session) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = spacing.md),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        // Divider between the pinned group and everything else. Batch-7 W5:
        // doubled thickness (2dp → 4dp) at the stronger `outline` tier so the
        // pinned block visibly detaches from the unpinned list.
        if (pinnedSessions.isNotEmpty() && unpinnedSessions.isNotEmpty()) {
            item(key = "pinned_divider") {
                HorizontalDivider(
                    thickness = 4.dp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("sessions_sheet_pinned_divider"),
                )
            }
        }

        items(unpinnedSessions, key = { it.id }) { session ->
            SessionSheetRow(
                session = session,
                isSelecting = uiState.isSelecting,
                isSelected = session.id in uiState.selectedIds,
                onClick = { onSessionTap(session.id) },
                onRename = { onRename(session) },
                onTogglePin = { onTogglePin(session) },
                onToggleArchive = { onToggleArchive(session) },
                onDelete = { onDelete(session) },
                onStartSelection = { onStartSelection(session) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = spacing.md),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        if (uiState.isSearching || uiState.isLoadingMoreSearch) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(spacing.md),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSheetRow(
    session: SessionInfo,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onStartSelection: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val title = sheetDisplayTitle(session).ifBlank { stringResource(R.string.history_untitled) }
    val preview = session.preview?.takeIf(String::isNotBlank)?.let(::cleanSearchSnippet)
    val playedAt = formatPlayedAt(session.activityEpochSeconds())
    var showActions by remember { mutableStateOf(false) }

    // Swipe right opens the actions menu (rename / pin / archive), swipe left
    // asks to delete. Both snap back without removing the row — the follow-up
    // happens via the menu or the dialogs. Selection mode makes swipes inert:
    // taps (and the checkbox) toggle membership, long-press does nothing more.
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        showActions = true
                        false
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        onDelete()
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
        gesturesEnabled = !isSelecting,
        modifier = Modifier.testTag("sessions_sheet_row_${session.id}"),
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
        Box {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).combinedClickable(
                            onClick = onClick,
                            // Long-press starts selection mode with this row
                            // checked (by design); the quick-actions menu stays
                            // reachable via the swipe-right gesture.
                            onLongClick = { if (!isSelecting) onStartSelection() },
                        ).padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    if (isSelecting) {
                        // Same action as the row tap in selection mode (the
                        // sheet routes tap → toggle while isSelecting).
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.testTag("sessions_sheet_checkbox_${session.id}"),
                        )
                    }
                    if (session.pinned == true) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    playedAt?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                preview?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            DropdownMenu(
                expanded = showActions,
                onDismissRequest = { showActions = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (session.pinned == true) {
                                    R.string.sessions_action_unpin
                                } else {
                                    R.string.sessions_action_pin
                                },
                            ),
                        )
                    },
                    onClick = {
                        showActions = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (session.archived == true) {
                                    R.string.sessions_action_unarchive
                                } else {
                                    R.string.sessions_action_archive
                                },
                            ),
                        )
                    },
                    onClick = {
                        showActions = false
                        onToggleArchive()
                    },
                )
            }
        }
    }
}

/** Rename + delete-confirm dialogs, mirroring the ones on the full Sessions screen. */
@Composable
private fun SessionsSheetDialogs(
    viewModel: SessionsViewModel,
    uiState: SessionsUiState,
) {
    if (uiState.showBulkDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.sessions_bulk_delete_title),
            message = stringResource(R.string.sessions_bulk_delete_message, uiState.selectedIds.size),
            onConfirm = { viewModel.confirmBulkDelete() },
            onDismiss = { viewModel.cancelBulkDelete() },
            confirmText = stringResource(R.string.action_delete),
            isDestructive = true,
        )
    }

    val renamingId = uiState.renamingSessionId
    if (renamingId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeRenameDialog() },
            title = { Text(stringResource(R.string.sessions_rename_title)) },
            text = {
                OutlinedTextField(
                    value = uiState.renameDraft,
                    onValueChange = { viewModel.updateRenameDraft(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sessions_rename_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.renameSession(renamingId, uiState.renameDraft) },
                    enabled = uiState.renameDraft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.sessions_action_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeRenameDialog() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val deleteId = uiState.sessionToDeleteConfirm
    if (deleteId != null) {
        val sessionTitle =
            uiState.sessions
                .find { it.id == deleteId }
                ?.let(::sheetDisplayTitle)
                ?.ifBlank { null }
                ?: stringResource(R.string.history_untitled)
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteSession() },
            title = { Text(stringResource(R.string.sessions_delete_title)) },
            text = { Text(stringResource(R.string.sessions_delete_message, sessionTitle)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteSession() },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteSession() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The session the toolbar's back button jumps to: the chat the last swap left,
 * if it is still visible in the current list and is not the active chat.
 */
private fun sheetBackTarget(
    uiState: SessionsUiState,
    currentSessionId: String?,
): SessionInfo? {
    val previousId = SessionsSheetMemory.previousSessionId ?: return null
    if (previousId == currentSessionId) return null
    return uiState.sessions.find { it.id == previousId }
}

/** Raw title fields only — callers add the localized "Untitled" fallback. */
private fun sheetDisplayTitle(session: SessionInfo): String =
    session.title?.takeIf(String::isNotBlank)
        ?: session.display_name?.takeIf(String::isNotBlank)
        ?: ""
