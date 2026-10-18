package com.m57.hermescontrol

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.update.WhatsNewNotice
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.ui.common.DisableDrawerGestures
import com.m57.hermescontrol.ui.common.DrawerGestureController
import com.m57.hermescontrol.ui.common.LocalDrawerGestureController
import com.m57.hermescontrol.ui.common.WhatsNewDialog
import com.m57.hermescontrol.ui.plugins.MemoryProviderDetailScreen
import com.m57.hermescontrol.ui.settings.ClientCertificatesPage
import com.m57.hermescontrol.ui.settings.SettingsAboutPage
import com.m57.hermescontrol.ui.settings.SettingsAppearancePage
import com.m57.hermescontrol.ui.settings.SettingsBehaviorPage
import com.m57.hermescontrol.ui.settings.SettingsChatPage
import com.m57.hermescontrol.ui.settings.SettingsConnectionPage
import com.m57.hermescontrol.ui.settings.SettingsLanguagePage
import com.m57.hermescontrol.ui.settings.SettingsVaultPage
import com.m57.hermescontrol.ui.settings.SettingsViewModel
import com.m57.hermescontrol.ui.settings.components.KO_FI_URL
import com.m57.hermescontrol.ui.toolsets.ToolsetDetailScreen
import kotlinx.coroutines.launch
import com.m57.hermescontrol.ui.authlogin.AuthLoginScreen as AuthLoginScreenContent
import com.m57.hermescontrol.ui.landing.LandingScreen as LandingScreenContent

private fun appEntryProvider(
    sessionId: String?,
    openDrawer: () -> Unit,
) = entryProvider {
    entry<LandingScreen> {
        // B7 (Jun 30 2026, kanban t_424): route landing screen buttons through navigateTo to prevent duplicate screens
        LandingScreenContent(
            onConnections = { NavigationController.navigateTo(ConnectionsScreen) },
            onAuthLogin = {
                NavigationController.navigateTo(AuthLoginScreen)
            },
        )
        // Landing doesn't use HermesScaffold — opt out of drawer gestures explicitly (issue #619).
        DisableDrawerGestures()
    }

    entry<AuthLoginScreen> {
        AuthLoginScreenContent(
            onConnected = {
                NavigationController.resetTo(ChatScreen)
            },
            onBack = {
                NavigationController.goBack()
            },
        )
        DisableDrawerGestures()
    }

    ScreenRegistry.ALL_SCREENS.forEach { screen ->
        addEntryProvider(clazz = screen.key::class) {
            screen.content(sessionId, openDrawer)
        }
    }

    // ── Settings drill-down sub-pages ───────────────────────────────────
    // Each passes drawerGesturesEnabled = false to HermesScaffold — this is the
    // single source of truth that prevents the drawer-scrim stuck-open bug
    // (issue #619). No global gesture set, no closeDrawer callback, no
    // LaunchedEffect(snapTo(Closed)) — the scaffold reconciles automatically.
    entry<ClientCertificatesScreen> {
        ClientCertificatesPage(onBack = { NavigationController.goBack() })
    }
    entry<SettingsConnection> {
        SettingsConnectionPage(
            onBack = { NavigationController.goBack() },
            onLogout = { /* handled by caller via goBack fallback */ },
            viewModel = viewModel { SettingsViewModel() },
        )
    }
    entry<SettingsAppearance> {
        SettingsAppearancePage(
            onBack = { NavigationController.goBack() },
            viewModel = viewModel { SettingsViewModel() },
        )
    }
    entry<SettingsLanguage> {
        SettingsLanguagePage(
            onBack = { NavigationController.goBack() },
            viewModel = viewModel { SettingsViewModel() },
        )
    }
    entry<SettingsChat> {
        SettingsChatPage(
            onBack = { NavigationController.goBack() },
            viewModel = viewModel { SettingsViewModel() },
        )
    }
    entry<SettingsBehavior> {
        SettingsBehaviorPage(
            onBack = { NavigationController.goBack() },
            viewModel = viewModel { SettingsViewModel() },
        )
    }
    entry<SettingsVault> {
        SettingsVaultPage(
            onBack = { NavigationController.goBack() },
        )
    }
    entry<SettingsAbout> {
        SettingsAboutPage(
            onBack = { NavigationController.goBack() },
        )
    }

    // ── Toolset detail drill-down (issue #782) ────────────────────────
    entry<ToolsetDetailKey> { key ->
        ToolsetDetailScreen(
            name = key.name,
            label = key.label,
            onBack = { NavigationController.goBack() },
        )
    }

    // ── Memory provider detail drill-down (issue #783) ─────────────────
    entry<MemoryProviderDetailKey> { key ->
        MemoryProviderDetailScreen(
            name = key.name,
            label = key.label,
            onBack = { NavigationController.goBack() },
        )
    }

    // ── Multi-Agent Group Chat Room ─────────────────────────────────────
    entry<GroupChatKey> { key ->
        com.m57.hermescontrol.ui.bots.group.GroupChatScreen(
            groupName = key.groupName,
            onBack = { NavigationController.goBack() },
        )
    }

    // ── Kanban Task Detail ─────────────────────────────────────────────
    entry<KanbanTaskDetailKey> { key ->
        com.m57.hermescontrol.ui.kanban.KanbanTaskScreen(
            boardSlug = key.boardSlug,
            taskId = key.taskId,
            onBack = { NavigationController.goBack() },
        )
    }
}

@Composable
fun MainNavigation(sessionId: String? = null) {
    val token by AuthManager.tokenFlow.collectAsState()
    val hasToken = !token.isNullOrBlank()
    val startScreen: NavKey = if (hasToken) ChatScreen else LandingScreen

    val backStack = remember(startScreen) { NavBackStack(startScreen) }
    NavigationController.backStack = backStack

    val pendingKanban = NavigationController.pendingKanbanNavigation
    LaunchedEffect(pendingKanban, hasToken) {
        if (hasToken && pendingKanban != null) {
            NavigationController.navigateTo(KanbanScreen)
            NavigationController.navigateTo(KanbanTaskDetailKey(pendingKanban.boardSlug, pendingKanban.taskId))
            NavigationController.consumePendingKanbanNavigation()
        }
    }

    val currentScreen = backStack.lastOrNull() ?: startScreen
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var endDrawerOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Single source of truth for drawer gestures (issue #619).
    // HermesScaffold / DisableDrawerGestures reconcile each screen's preference
    // into this controller via SideEffect; ModalNavigationDrawer reads .enabled
    // below. No DRAWER_GESTURE_SCREENS set, no LaunchedEffect(snapTo(Closed)),
    // no closeDrawer callback — the controller closes the drawer itself when a
    // screen opts out.
    val gestureController =
        remember(drawerState, scope) {
            DrawerGestureController(drawerState, scope)
        }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val backToastText = stringResource(R.string.press_back_again_to_exit)

    // Double-back to exit on root ChatScreen — unless the chat was opened from
    // a list (History, Bots): then the first back returns to that list (Telegram
    // chat-list parity) and only a further double-back exits.
    BackHandler(enabled = currentScreen == ChatScreen && backStack.size == 1) {
        val pendingReturn = NavigationController.chatReturnScreen
        if (pendingReturn != null) {
            NavigationController.consumeChatReturnScreen()
            NavigationController.navigateTo(pendingReturn)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, backToastText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Safety fallback: if a non-chat screen somehow sits alone on stack, swipe back returns to ChatScreen
    BackHandler(
        enabled = currentScreen != ChatScreen && currentScreen != LandingScreen && backStack.size == 1,
    ) {
        NavigationController.goBack()
    }

    // Manual end drawer: back button closes it before any navigation back.
    BackHandler(enabled = endDrawerOpen) {
        endDrawerOpen = false
    }

    CompositionLocalProvider(LocalDrawerGestureController provides gestureController) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = gestureController.enabled,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    HermesDrawerContent(
                        sections = listOf(DrawerSection.CONVERSE, DrawerSection.AUTOMATE, DrawerSection.INSPECT),
                        currentScreen = currentScreen,
                        hasToken = hasToken,
                        showSupportButton = true,
                        closeDrawer = { scope.launch { drawerState.close() } },
                    )
                }
            },
        ) {
            // Right-hand drawer: the configure/inspect half of the registry,
            // mirrored to the end side so swiping left from the right edge
            // opens it. This material3 build has no end-side ModalNavigationDrawer,
            // so the end drawer is manual: an edge drag zone flips endDrawerOpen
            // and an AnimatedVisibility slides the sheet in from the right over
            // a scrim. The start drawer above stays untouched.
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.navigationBars,
                ) { paddingValues ->
                    NavDisplay(
                        backStack = backStack,
                        onBack = { NavigationController.goBack() },
                        entryProvider =
                            appEntryProvider(sessionId, openDrawer),
                        modifier =
                            Modifier
                                .padding(paddingValues)
                                .consumeWindowInsets(paddingValues)
                                .fillMaxSize(),
                    )
                }

                if (gestureController.enabled && !endDrawerOpen) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(24.dp)
                                .pointerInput(Unit) {
                                    var drag = 0f
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, dragAmount ->
                                            drag += dragAmount
                                        },
                                        onDragEnd = {
                                            if (drag < -60f) endDrawerOpen = true
                                            drag = 0f
                                        },
                                        onDragCancel = { drag = 0f },
                                    )
                                },
                    )
                }

                if (endDrawerOpen) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { endDrawerOpen = false })
                                },
                    )
                }

                AnimatedVisibility(
                    visible = endDrawerOpen,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(320.dp)
                                .verticalScroll(rememberScrollState()),
                        tonalElevation = 1.dp,
                    ) {
                        HermesDrawerContent(
                            sections = listOf(DrawerSection.CONFIGURE),
                            currentScreen = currentScreen,
                            hasToken = hasToken,
                            showSupportButton = false,
                            closeDrawer = { endDrawerOpen = false },
                        )
                    }
                }
            }
        }

        WhatsNewNotice.release?.let { release ->
            WhatsNewDialog(
                tag = release.tagName,
                releaseNotes = release.body,
                onDismiss = { WhatsNewNotice.dismiss() },
            )
        }
    }
}

/**
 * Shared drawer body for both sides: header with the live connection dot, the
 * requested registry sections in order, and (left drawer only) the support
 * button. Items navigate through the singleton [NavigationController] and then
 * close whichever drawer they came from via [closeDrawer].
 */
@Composable
private fun HermesDrawerContent(
    sections: List<DrawerSection>,
    currentScreen: NavKey,
    hasToken: Boolean,
    showSupportButton: Boolean,
    closeDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val connectionStatus by HermesWsClient.connectionStatus.collectAsState()
    val statusColor =
        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> LocalHermesStatusColors.current.success

            ConnectionStatus.CONNECTING,
            ConnectionStatus.RECONNECTING,
            -> LocalHermesStatusColors.current.warning

            ConnectionStatus.DISCONNECTED,
            ConnectionStatus.NO_NETWORK,
            ConnectionStatus.AUTH_EXPIRED,
            -> LocalHermesStatusColors.current.error
        }
    // The left host (ModalDrawerSheet) supplies a Column; the manual end-side
    // drawer (Surface) does not. Owning the Column here keeps the body correct
    // in either host instead of stacking every child at the top.
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_drawer_title),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(color = statusColor, shape = CircleShape),
            )
        }
        Text(
            text = stringResource(R.string.nav_drawer_subtitle),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, end = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        for (section in sections) {
            Text(
                text = stringResource(section.titleRes).uppercase(),
                modifier =
                    Modifier.padding(
                        start = 16.dp,
                        top = 8.dp,
                        bottom = 4.dp,
                        end = 16.dp,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            ScreenRegistry.ALL_SCREENS
                .filter { it.drawerSection == section && hasToken }
                .forEach { entry ->
                    NavigationDrawerItem(
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.labelRes)) },
                        selected = currentScreen == entry.key,
                        onClick = {
                            closeDrawer()
                            NavigationController.navigateTo(entry.key)
                        },
                        colors =
                            NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
        }

        if (showSupportButton) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    closeDrawer()
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KO_FI_URL)))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.nav_drawer_support))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
