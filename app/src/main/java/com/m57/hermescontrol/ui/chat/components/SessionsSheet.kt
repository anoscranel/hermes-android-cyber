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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.sessions.HistorySection
import com.m57.hermescontrol.ui.sessions.SessionsUiState
import com.m57.hermescontrol.ui.sessions.SessionsViewModel
import com.m57.hermescontrol.ui.sessions.activityEpochSeconds
import com.m57.hermescontrol.ui.sessions.cleanSearchSnippet
import com.m57.hermescontrol.ui.sessions.formatPlayedAt
import com.m57.hermescontrol.ui.sessions.toSessionInfo

/**
 * Quick-switcher bottom sheet: floats over the chat UI so tapping a session swaps the
 * active chat in place via [NavigationController.openChatSession] instead of navigating
 * away. Owns its own [SessionsViewModel] (all-default ctor) scoped to the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsSheet(
    onDismiss: () -> Unit,
    initialSection: HistorySection = HistorySection.CONVERSATIONS,
    viewModel: SessionsViewModel = viewModel { SessionsViewModel() },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalSpacing.current

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
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("sessions_sheet"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md),
                placeholder = stringResource(R.string.sessions_search_placeholder),
            )
            Spacer(modifier = Modifier.height(spacing.sm))

            when {
                uiState.isLoading && !uiState.isSearchMode -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null && !uiState.isSearchMode -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(spacing.md),
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
                        onSessionTap = { sessionId ->
                            NavigationController.openChatSession(sessionId)
                            onDismiss()
                        },
                        onLoadMoreSearch = viewModel::loadMoreSearch,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionsSheetList(
    uiState: SessionsUiState,
    onSessionTap: (String) -> Unit,
    onLoadMoreSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val spacing = LocalSpacing.current

    val displaySessions: List<SessionInfo> =
        remember(uiState.isSearchMode, uiState.sessions, uiState.searchResults) {
            if (uiState.isSearchMode) {
                uiState.searchResults.map { it.toSessionInfo() }
            } else {
                uiState.sessions
            }
        }

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
        items(displaySessions, key = { it.id }) { session ->
            SessionSheetRow(
                session = session,
                onClick = { onSessionTap(session.id) },
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

@Composable
private fun SessionSheetRow(
    session: SessionInfo,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val title =
        session.title?.takeIf(String::isNotBlank)
            ?: session.display_name?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.history_untitled)
    val preview = session.preview?.takeIf(String::isNotBlank)?.let(::cleanSearchSnippet)
    val playedAt = formatPlayedAt(session.activityEpochSeconds())

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("sessions_sheet_row_${session.id}")
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
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
}
