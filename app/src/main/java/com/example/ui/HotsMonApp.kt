package com.example.ui

import android.Manifest
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.AppPreferencesRepository
import com.example.ui.components.NotificationCenterSheet
import com.example.ui.components.OfflineErrorView
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldOnline
import com.example.webview.ResponsiveWebView

@Composable
fun HotsMonApp(
    viewModel: HotsMonViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var activeUrlToLoad by remember { mutableStateOf(AppPreferencesRepository.BASE_URL) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(uiState.web.requestNotificationPermissionEvent) {
        if (uiState.web.requestNotificationPermissionEvent > 0 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.web.pendingTargetUrl) {
        val target = uiState.web.pendingTargetUrl
        if (!target.isNullOrBlank()) {
            activeUrlToLoad = target
            webViewRef?.loadUrl(target)
            viewModel.consumePendingTargetUrl()
        }
    }

    LaunchedEffect(uiState.web.snackbarMessage) {
        val msg = uiState.web.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    BackHandler(enabled = showNotificationsSheet || showSettingsSheet || uiState.prefs.fullscreenModeEnabled || uiState.web.canGoBack) {
        when {
            showNotificationsSheet -> showNotificationsSheet = false
            showSettingsSheet -> showSettingsSheet = false
            uiState.prefs.fullscreenModeEnabled -> viewModel.toggleFullscreen(false)
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
        }
    }

    val reloadAction = {
        viewModel.clearErrorForRetry()
        webViewRef?.reload()
        Unit
    }

    val homeAction = {
        viewModel.clearErrorForRetry()
        activeUrlToLoad = AppPreferencesRepository.BASE_URL
        webViewRef?.loadUrl(AppPreferencesRepository.BASE_URL)
        Unit
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 700.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen && !uiState.prefs.fullscreenModeEnabled) {
                    HotsMonNavigationRail(
                        canGoBack = uiState.web.canGoBack,
                        canGoForward = uiState.web.canGoForward,
                        unreadCount = uiState.unreadCount,
                        onBack = { webViewRef?.goBack() },
                        onForward = { webViewRef?.goForward() },
                        onHome = homeAction,
                        onRefresh = reloadAction,
                        onFullscreen = { viewModel.toggleFullscreen(true) },
                        onNotifications = { showNotificationsSheet = true },
                        onSettings = { showSettingsSheet = true }
                    )
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedVisibility(
                        visible = !uiState.prefs.fullscreenModeEnabled && !isWideScreen,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut()
                    ) {
                        HotsMonTopBar(
                            pageTitle = uiState.web.pageTitle,
                            currentUrl = uiState.web.currentUrl,
                            isLoading = uiState.web.isLoading,
                            hasError = uiState.web.hasError,
                            unreadCount = uiState.unreadCount,
                            onNotificationsClick = { showNotificationsSheet = true },
                            onSettingsClick = { showSettingsSheet = true }
                        )
                    }

                    AnimatedVisibility(
                        visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !uiState.web.hasNotificationPermission &&
                            !uiState.prefs.notificationPermissionBannerDismissed &&
                            !uiState.prefs.fullscreenModeEnabled
                    ) {
                        NotificationPermissionBanner(
                            onEnableClick = { viewModel.triggerPermissionRequest() },
                            onDismissClick = { viewModel.dismissPermissionBanner() }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .let { mod ->
                                if (uiState.prefs.fullscreenModeEnabled) mod.statusBarsPadding() else mod
                            }
                    ) {
                        ResponsiveWebView(
                            urlToLoad = activeUrlToLoad,
                            desktopMode = uiState.prefs.desktopModeEnabled,
                            textZoomPercent = uiState.prefs.textZoomPercent,
                            hasNotificationPermission = uiState.web.hasNotificationPermission,
                            onWebViewCreated = { webViewRef = it },
                            onPageStarted = viewModel::onPageStarted,
                            onPageFinished = viewModel::onPageFinished,
                            onProgressChanged = viewModel::onProgressChanged,
                            onNavigationChanged = viewModel::onNavigationStateChanged,
                            onPageError = viewModel::onPageError,
                            onWebNotification = viewModel::onWebNotificationReceived,
                            onRequestNotificationPermission = viewModel::triggerPermissionRequest,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (uiState.web.hasError) {
                            OfflineErrorView(
                                errorMessage = uiState.web.errorMessage,
                                onRetry = reloadAction,
                                onOpenNotifications = { showNotificationsSheet = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (uiState.web.isLoading && uiState.web.progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { uiState.web.progress / 100f },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.TopCenter)
                            )
                        }

                        if (uiState.prefs.fullscreenModeEnabled) {
                            SmallFloatingActionButton(
                                onClick = { viewModel.toggleFullscreen(false) },
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .navigationBarsPadding()
                                    .padding(16.dp)
                                    .testTag("exit_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FullscreenExit,
                                    contentDescription = stringResource(id = R.string.cd_exit_fullscreen)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !uiState.prefs.fullscreenModeEnabled && !isWideScreen,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        HotsMonBottomControls(
                            canGoBack = uiState.web.canGoBack,
                            canGoForward = uiState.web.canGoForward,
                            onBack = { webViewRef?.goBack() },
                            onForward = { webViewRef?.goForward() },
                            onHome = homeAction,
                            onRefresh = reloadAction,
                            onFullscreen = { viewModel.toggleFullscreen(true) }
                        )
                    }
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationCenterSheet(
            prefs = uiState.prefs,
            isCheckingHealth = uiState.web.isCheckingHealth,
            onCheckNow = viewModel::runManualHealthCheck,
            onMarkAllRead = viewModel::markAllNotificationsRead,
            onClearAll = viewModel::clearNotificationHistory,
            onOpenNotificationUrl = { targetUrl ->
                showNotificationsSheet = false
                activeUrlToLoad = targetUrl
                viewModel.clearErrorForRetry()
                webViewRef?.loadUrl(targetUrl)
            },
            onDismiss = { showNotificationsSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            prefs = uiState.prefs,
            hasNotificationPermission = uiState.web.hasNotificationPermission,
            onRequestNotificationPermission = viewModel::triggerPermissionRequest,
            onToggleBackgroundMonitor = viewModel::toggleBackgroundMonitor,
            onSelectMonitorInterval = viewModel::setMonitorInterval,
            onSendTestNotification = viewModel::sendTestPushNotification,
            onToggleDesktopMode = viewModel::toggleDesktopMode,
            onToggleFullscreen = viewModel::toggleFullscreen,
            onTextZoomChange = viewModel::setTextZoom,
            onClearCache = { viewModel.clearWebCache(webViewRef) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun HotsMonTopBar(
    pageTitle: String,
    currentUrl: String,
    isLoading: Boolean,
    hasError: Boolean,
    unreadCount: Int,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.hotsmon_icon_fg),
                contentDescription = stringResource(id = R.string.cd_brand_logo),
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val dotColor = when {
                        hasError -> AmberAccent
                        isLoading -> MaterialTheme.colorScheme.primary
                        else -> EmeraldOnline
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                Text(
                    text = currentUrl.removePrefix("https://"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("notifications_button")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge {
                                Text(text = if (unreadCount > 9) "9+" else unreadCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = stringResource(id = R.string.cd_notifications)
                    )
                }
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(id = R.string.cd_settings)
                )
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(
    onEnableClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Aktifkan push notifications untuk menerima alert HotsMon secara real-time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onEnableClick,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("enable_notifications_banner_button")
            ) {
                Text("Aktifkan", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(
                onClick = onDismissClick,
                modifier = Modifier.testTag("dismiss_notifications_banner_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup banner",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun HotsMonBottomControls(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onFullscreen: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.cd_back)
                )
            }

            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(id = R.string.cd_forward)
                )
            }

            IconButton(
                onClick = onHome,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_home_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = stringResource(id = R.string.cd_home)
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(id = R.string.cd_refresh)
                )
            }

            IconButton(
                onClick = onFullscreen,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("nav_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(id = R.string.cd_fullscreen)
                )
            }
        }
    }
}

@Composable
private fun HotsMonNavigationRail(
    canGoBack: Boolean,
    canGoForward: Boolean,
    unreadCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onFullscreen: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.statusBarsPadding().navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Image(
            painter = painterResource(id = R.drawable.hotsmon_icon_fg),
            contentDescription = stringResource(id = R.string.cd_brand_logo),
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = false,
            onClick = onHome,
            icon = { Icon(Icons.Rounded.Home, contentDescription = stringResource(id = R.string.cd_home)) },
            label = { Text("Beranda") },
            modifier = Modifier.testTag("rail_home_button")
        )
        NavigationRailItem(
            selected = false,
            onClick = onRefresh,
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(id = R.string.cd_refresh)) },
            label = { Text("Muat") },
            modifier = Modifier.testTag("rail_refresh_button")
        )
        NavigationRailItem(
            selected = false,
            onClick = onBack,
            enabled = canGoBack,
            icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(id = R.string.cd_back)) },
            label = { Text("Kembali") },
            modifier = Modifier.testTag("rail_back_button")
        )
        NavigationRailItem(
            selected = false,
            onClick = onForward,
            enabled = canGoForward,
            icon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(id = R.string.cd_forward)) },
            label = { Text("Maju") },
            modifier = Modifier.testTag("rail_forward_button")
        )
        NavigationRailItem(
            selected = false,
            onClick = onNotifications,
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge { Text(if (unreadCount > 9) "9+" else unreadCount.toString()) }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Notifications, contentDescription = stringResource(id = R.string.cd_notifications))
                }
            },
            label = { Text("Alert") },
            modifier = Modifier.testTag("rail_notifications_button")
        )
        NavigationRailItem(
            selected = false,
            onClick = onFullscreen,
            icon = { Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(id = R.string.cd_fullscreen)) },
            label = { Text("Penuh") },
            modifier = Modifier.testTag("rail_fullscreen_button")
        )
        NavigationRailItem(
            selected = false,
            onClick = onSettings,
            icon = { Icon(Icons.Outlined.Settings, contentDescription = stringResource(id = R.string.cd_settings)) },
            label = { Text("Atur") },
            modifier = Modifier.testTag("rail_settings_button")
        )
    }
}
