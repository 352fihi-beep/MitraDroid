package com.mitra.android.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mitra.android.ui.screens.HistoryScreen
import com.mitra.android.ui.screens.HomeScreen
import com.mitra.android.ui.screens.InCallScreen
import com.mitra.android.ui.screens.SettingsScreen
import com.mitra.android.ui.screens.SmsThreadsScreen
import com.mitra.android.ui.screens.VoiceTrainingScreen

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val primaryDestinations = listOf(
    NavDestinationItem("home", "Hub", Icons.Filled.Home, Icons.Outlined.Home),
    NavDestinationItem("incall/new", "Call", Icons.Filled.Call, Icons.Outlined.Call),
    NavDestinationItem("sms", "SMS", Icons.Filled.Sms, Icons.Outlined.Sms),
    NavDestinationItem("history", "History", Icons.Filled.History, Icons.Outlined.History),
    NavDestinationItem("voiceTraining", "Voice", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    NavDestinationItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MitraAppShell(
    viewModel: MitraViewModel,
    widthSizeClass: WindowWidthSizeClass
) {
    val navController = rememberNavController()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val callSession by viewModel.callSessionState.collectAsStateWithLifecycle()
    val allMessages by viewModel.allMessagesFlow.collectAsStateWithLifecycle()
    val allCalls by viewModel.allCallsFlow.collectAsStateWithLifecycle()
    val allMemories by viewModel.allMemoriesFlow.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val localModelStatus by viewModel.localModelStatusFlow.collectAsStateWithLifecycle()
    val smsDraft by viewModel.smsDraft.collectAsStateWithLifecycle()
    val isGeneratingDraft by viewModel.isGeneratingDraft.collectAsStateWithLifecycle()
    val keyFeedback by viewModel.keyTestFeedback.collectAsStateWithLifecycle()
    val localDiagnostic by viewModel.localInferenceTestResult.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact

    val screenTitle = when {
        currentRoute.startsWith("incall") -> "In-Call Console"
        currentRoute == "sms" -> "SMS Threads"
        currentRoute == "history" -> "Call & Telemetry History"
        currentRoute == "voiceTraining" -> "Voice & Edge Studio"
        currentRoute == "settings" -> "Capabilities & API Keys"
        else -> "Mitra AI Hub"
    }

    val navigateTopLevel: (String) -> Unit = { route ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useNavigationRail) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight().testTag("adaptive_navigation_rail")
            ) {
                primaryDestinations.forEach { dest ->
                    val selected = backStackEntry?.destination?.hierarchy?.any {
                        it.route == dest.route || (dest.route.startsWith("incall") && it.route?.startsWith("incall") == true)
                    } == true
                    NavigationRailItem(
                        selected = selected,
                        onClick = { navigateTopLevel(dest.route) },
                        icon = {
                            Icon(
                                if (selected) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label
                            )
                        },
                        label = { Text(dest.label) },
                        modifier = Modifier.testTag("nav_rail_${dest.label.lowercase()}")
                    )
                }
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(screenTitle, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        if (currentRoute != "home") {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    navController.popBackStack()
                                },
                                modifier = Modifier.testTag("top_bar_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                        primaryDestinations.take(5).forEach { dest ->
                            val selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route == dest.route || (dest.route.startsWith("incall") && it.route?.startsWith("incall") == true)
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_bar_${dest.label.lowercase()}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute == "home") {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startOrPlaceCall("+1-787-555-0142")
                            navigateTopLevel("incall/new")
                        },
                        modifier = Modifier.testTag("fab_new_call")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "New AI Call")
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    enterTransition = { fadeIn(spring(stiffness = Spring.StiffnessLow)) },
                    exitTransition = { fadeOut(spring(stiffness = Spring.StiffnessLow)) }
                ) {
                    composable("home") {
                        HomeScreen(
                            uiState = homeState,
                            widthSizeClass = widthSizeClass,
                            onNavigate = { route -> navigateTopLevel(route) },
                            onRefreshTelemetry = viewModel::refreshTelemetry,
                            onStartQuickCall = {
                                viewModel.startOrPlaceCall("+1-787-555-0142")
                                navigateTopLevel("incall/new")
                            }
                        )
                    }
                    composable("incall/{callId}") {
                        InCallScreen(
                            sessionState = callSession,
                            onStartCall = viewModel::startOrPlaceCall,
                            onEndCall = viewModel::endActiveCall,
                            onToggleMute = viewModel::toggleMute,
                            onToggleSpeaker = viewModel::toggleSpeaker,
                            onToggleListening = {
                                if (callSession.isListeningStt) viewModel.stopSpeechListening()
                                else viewModel.startSpeechListening()
                            },
                            onSendDtmf = viewModel::sendDtmf,
                            onSendUtterance = viewModel::sendCallUtterance
                        )
                    }
                    composable("sms") {
                        SmsThreadsScreen(
                            messages = allMessages,
                            memories = allMemories,
                            draft = smsDraft,
                            isGeneratingDraft = isGeneratingDraft,
                            widthSizeClass = widthSizeClass,
                            onDraftChange = viewModel::updateSmsDraft,
                            onGenerateAiDraft = viewModel::generateAiSmsReply,
                            onSendSms = viewModel::sendSmsMessage,
                            onSimulateIncomingSms = viewModel::simulateIncomingSms
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            calls = allCalls,
                            onExportCsv = viewModel::exportHistoryCsv,
                            onShowDailyReport = viewModel::showDailyReport,
                            onClearHistory = viewModel::clearAllHistory
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            keyTestFeedback = keyFeedback,
                            onSaveApiKey = viewModel::saveApiKey,
                            onTestApiKey = viewModel::testProviderKey,
                            onSelectPrimaryProvider = viewModel::setPrimaryProvider,
                            onUpdatePowerThresholds = viewModel::updatePowerThresholds,
                            onToggleDarkTheme = viewModel::toggleDarkTheme,
                            onToggleDynamicColor = viewModel::toggleDynamicColor,
                            onSelectAccentTheme = viewModel::setAccentTheme,
                            onToggleAutoReplySms = viewModel::toggleAutoReplySms,
                            onTogglePhishingGuard = viewModel::togglePhishingGuard,
                            onToggleReadAloudSms = viewModel::toggleReadAloudSms,
                            onToggleBlockUnknown = viewModel::toggleBlockUnknown,
                            onToggleAutoMuteSpam = viewModel::toggleAutoMuteSpam,
                            onToggleVibrateOnRing = viewModel::toggleVibrateOnRing,
                            onToggleDnd = viewModel::toggleDnd,
                            onToggleSpeechToText = viewModel::toggleSpeechToText,
                            onAddWhitelist = viewModel::addWhitelistContact,
                            onRemoveWhitelist = viewModel::removeWhitelistContact,
                            onClearAllKeys = viewModel::clearAllApiKeys
                        )
                    }
                    composable("voiceTraining") {
                        VoiceTrainingScreen(
                            settings = settings,
                            modelStatus = localModelStatus,
                            diagnosticResult = localDiagnostic,
                            onImportModelUri = viewModel::importLocalModel,
                            onUnloadModel = viewModel::unloadLocalModel,
                            onUpdateTtsConfig = viewModel::updateTtsConfig,
                            onPreviewVoice = viewModel::previewTtsVoice,
                            onRunLocalDiagnostic = viewModel::runLocalModelDiagnostic
                        )
                    }
                }
            }
        }
    }
}
