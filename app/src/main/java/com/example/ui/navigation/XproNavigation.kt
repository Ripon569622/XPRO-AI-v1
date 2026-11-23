package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.auth.authStateFlow
import com.example.data.model.AiMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.ui.components.XproAmbientBackground
import com.example.ui.components.XproLogoMark
import com.example.ui.screens.AssistantWorkspaceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.LocalXproColors
import com.example.ui.theme.XproAiTheme
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.WorkspaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class XproTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    WORKSPACE("workspace", R.string.nav_workspace, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    STUDIO("studio", R.string.nav_studio, Icons.Filled.Brush, Icons.Outlined.Brush),
    HISTORY("history", R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun XproAppRoot() {
    val context = LocalContext.current
    val prefsRepo = remember(context) { UserPreferencesRepository(context) }
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(prefsRepo)
    )
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    XproAiTheme(themePreference = settings.themePreference) {
        val user = currentUser
        if (user == null) {
            AuthScreen(onAuthSuccess = {})
        } else {
            key(user.uid) {
                AuthenticatedXproShell(
                    user = user,
                    settings = settings,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedXproShell(
    user: FirebaseUser,
    settings: UserSettings,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val firestoreDb = remember(context) {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dash_${user.uid}",
        factory = DashboardViewModel.provideFactory(firestoreDb, user.uid)
    )
    val workspaceViewModel: WorkspaceViewModel = viewModel(
        key = "work_${user.uid}",
        factory = WorkspaceViewModel.provideFactory(firestoreDb, user.uid)
    )
    val imageStudioViewModel: ImageStudioViewModel = viewModel(
        key = "studio_${user.uid}",
        factory = ImageStudioViewModel.provideFactory()
    )

    LaunchedEffect(user.uid) {
        dashboardViewModel.syncProfile(user)
    }

    LaunchedEffect(settings.preferredProvider, settings.geminiModel, settings.openAiModel) {
        workspaceViewModel.applyDefaultPreferences(
            provider = settings.preferredProvider,
            geminiModel = settings.geminiModel,
            openAiModel = settings.openAiModel
        )
        imageStudioViewModel.setProvider(settings.preferredProvider)
    }

    val conversationsState by dashboardViewModel.conversationsState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(XproTab.DASHBOARD) }
    val xproColors = LocalXproColors.current

    BackHandler(enabled = currentTab != XproTab.DASHBOARD) {
        currentTab = XproTab.DASHBOARD
    }

    XproAmbientBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                XproLogoMark(size = 30.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { settingsViewModel.toggleDarkLight() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("quick_theme_toggle")
                            ) {
                                Icon(
                                    imageVector = if (xproColors.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = "Toggle dark or light mode",
                                    tint = xproColors.neonGlow
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            XproTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = xproColors.neonGlow,
                                        selectedTextColor = xproColors.neonGlow,
                                        indicatorColor = xproColors.neonGlow.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            XproTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("rail_${tab.route}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (currentTab) {
                            XproTab.DASHBOARD -> DashboardScreen(
                                userName = user.displayName?.takeIf { it.isNotBlank() } ?: "Explorer",
                                conversationsState = conversationsState,
                                activeProvider = settings.preferredProvider,
                                onLaunchMode = { mode: AiMode ->
                                    workspaceViewModel.startNewSession(mode)
                                    currentTab = XproTab.WORKSPACE
                                },
                                onOpenImageStudio = {
                                    currentTab = XproTab.STUDIO
                                },
                                onOpenConversation = { conv ->
                                    workspaceViewModel.openConversation(
                                        conversationId = conv.id,
                                        mode = conv.aiMode,
                                        provider = conv.aiProvider
                                    )
                                    currentTab = XproTab.WORKSPACE
                                },
                                onViewAllHistory = {
                                    currentTab = XproTab.HISTORY
                                }
                            )

                            XproTab.WORKSPACE -> AssistantWorkspaceScreen(
                                viewModel = workspaceViewModel,
                                autoSpeakVoice = settings.autoSpeakVoice
                            )

                            XproTab.STUDIO -> ImageStudioScreen(
                                viewModel = imageStudioViewModel
                            )

                            XproTab.HISTORY -> HistoryScreen(
                                conversationsState = conversationsState,
                                onOpenConversation = { conv ->
                                    workspaceViewModel.openConversation(
                                        conversationId = conv.id,
                                        mode = conv.aiMode,
                                        provider = conv.aiProvider
                                    )
                                    currentTab = XproTab.WORKSPACE
                                },
                                onTogglePin = dashboardViewModel::togglePin,
                                onRename = dashboardViewModel::renameConversation,
                                onDelete = { convId ->
                                    dashboardViewModel.deleteConversation(convId)
                                    workspaceViewModel.handleDeletedConversation(convId)
                                },
                                onFetchMessagesForExport = dashboardViewModel::fetchMessagesForExport
                            )

                            XproTab.SETTINGS -> SettingsScreen(
                                user = user,
                                settings = settings,
                                settingsViewModel = settingsViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
