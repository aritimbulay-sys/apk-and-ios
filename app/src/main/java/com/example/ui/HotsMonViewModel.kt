package com.example.ui

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppPreferencesRepository
import com.example.data.HotsMonPreferences
import com.example.data.NotificationCategory
import com.example.notifications.HotsMonNotificationHelper
import com.example.notifications.NotificationChannels
import com.example.notifications.SiteMonitorWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WebSessionState(
    val currentUrl: String = AppPreferencesRepository.BASE_URL,
    val pageTitle: String = "HotsMon Dashboard",
    val progress: Int = 0,
    val isLoading: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val isCheckingHealth: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val pendingTargetUrl: String? = null,
    val requestNotificationPermissionEvent: Int = 0,
    val snackbarMessage: String? = null
)

data class HotsMonUiState(
    val web: WebSessionState = WebSessionState(),
    val prefs: HotsMonPreferences = HotsMonPreferences()
) {
    val unreadCount: Int
        get() = prefs.notificationHistory.count { !it.isRead }
}

class HotsMonViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = AppPreferencesRepository(appContext)
    private val webState = MutableStateFlow(
        WebSessionState(
            hasNotificationPermission = HotsMonNotificationHelper.hasNotificationPermission(appContext)
        )
    )

    val uiState: StateFlow<HotsMonUiState> = combine(
        webState,
        repository.preferencesFlow
    ) { web, prefs ->
        HotsMonUiState(web = web, prefs = prefs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HotsMonUiState(web = webState.value)
    )

    init {
        NotificationChannels.ensureCreated(appContext)
        viewModelScope.launch {
            val snapshot = repository.getSnapshot()
            SiteMonitorWorker.schedule(
                context = appContext,
                enabled = snapshot.backgroundMonitorEnabled,
                intervalMinutes = snapshot.monitorIntervalMinutes
            )
        }
    }

    fun refreshPermissionState() {
        val granted = HotsMonNotificationHelper.hasNotificationPermission(appContext)
        webState.update { it.copy(hasNotificationPermission = granted) }
    }

    fun onPermissionResult(granted: Boolean) {
        webState.update {
            it.copy(
                hasNotificationPermission = granted,
                snackbarMessage = if (granted) {
                    "Notifikasi push aktif."
                } else {
                    "Izin notifikasi ditolak. Aktifkan di Pengaturan sistem jika diperlukan."
                }
            )
        }
    }

    fun triggerPermissionRequest() {
        webState.update {
            it.copy(requestNotificationPermissionEvent = it.requestNotificationPermissionEvent + 1)
        }
    }

    fun dismissPermissionBanner() {
        viewModelScope.launch {
            repository.setPermissionBannerDismissed(true)
        }
    }

    fun handleIncomingDeepLink(url: String?) {
        val cleanUrl = url?.takeIf { it.startsWith("https://") } ?: return
        webState.update { it.copy(pendingTargetUrl = cleanUrl, hasError = false, errorMessage = null) }
    }

    fun consumePendingTargetUrl() {
        webState.update { it.copy(pendingTargetUrl = null) }
    }

    fun onPageStarted(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        webState.update {
            it.copy(
                currentUrl = url ?: it.currentUrl,
                isLoading = true,
                hasError = false,
                errorMessage = null,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onPageFinished(url: String?, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        webState.update {
            it.copy(
                currentUrl = url ?: it.currentUrl,
                pageTitle = title?.takeIf { t -> t.isNotBlank() } ?: "HotsMon Dashboard",
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onProgressChanged(newProgress: Int) {
        webState.update {
            it.copy(
                progress = newProgress,
                isLoading = newProgress < 100
            )
        }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        webState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun onPageError(description: String?) {
        webState.update {
            it.copy(
                isLoading = false,
                hasError = true,
                errorMessage = description?.takeIf { d -> d.isNotBlank() }
                    ?: "Tidak dapat terhubung ke server HotsMon."
            )
        }
    }

    fun clearErrorForRetry() {
        webState.update { it.copy(hasError = false, errorMessage = null, isLoading = true) }
    }

    fun toggleDesktopMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDesktopModeEnabled(enabled)
        }
    }

    fun toggleFullscreen(enabled: Boolean) {
        viewModelScope.launch {
            repository.setFullscreenModeEnabled(enabled)
        }
    }

    fun setTextZoom(percent: Int) {
        viewModelScope.launch {
            repository.setTextZoomPercent(percent)
        }
    }

    fun toggleBackgroundMonitor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackgroundMonitorEnabled(enabled)
            val interval = uiState.value.prefs.monitorIntervalMinutes
            SiteMonitorWorker.schedule(appContext, enabled, interval)
            webState.update {
                it.copy(
                    snackbarMessage = if (enabled) {
                        "Pemantauan latar belakang aktif setiap $interval menit."
                    } else {
                        "Pemantauan latar belakang dinonaktifkan."
                    }
                )
            }
        }
    }

    fun setMonitorInterval(minutes: Long) {
        viewModelScope.launch {
            repository.setMonitorIntervalMinutes(minutes)
            val enabled = uiState.value.prefs.backgroundMonitorEnabled
            if (enabled) {
                SiteMonitorWorker.schedule(appContext, true, minutes)
            }
        }
    }

    fun onWebNotificationReceived(title: String, body: String, url: String) {
        viewModelScope.launch {
            HotsMonNotificationHelper.dispatchPushNotification(
                context = appContext,
                repository = repository,
                title = title,
                message = body,
                targetUrl = url,
                category = NotificationCategory.WEB_PUSH,
                highPriority = true
            )
        }
    }

    fun sendTestPushNotification() {
        viewModelScope.launch {
            val delivered = HotsMonNotificationHelper.dispatchPushNotification(
                context = appContext,
                repository = repository,
                title = "Tes Notifikasi HotsMon",
                message = "Push notifications aktif! Ketuk notifikasi ini untuk membuka dashboard hotsmon.bondanglangit.com.",
                targetUrl = AppPreferencesRepository.BASE_URL,
                category = NotificationCategory.TEST,
                highPriority = true
            )
            webState.update {
                it.copy(
                    snackbarMessage = if (delivered) {
                        "Notifikasi tes berhasil dikirim."
                    } else {
                        "Notifikasi dicatat di Pusat Notifikasi. Aktifkan izin notifikasi sistem agar muncul di status bar."
                    }
                )
            }
        }
    }

    fun runManualHealthCheck() {
        if (webState.value.isCheckingHealth) return
        viewModelScope.launch {
            webState.update { it.copy(isCheckingHealth = true) }
            val result = withContext(Dispatchers.IO) {
                SiteMonitorWorker.checkSiteHealth()
            }
            repository.updateServerHealth(
                statusCode = result.statusCode,
                latencyMs = result.latencyMs,
                checkedAt = System.currentTimeMillis()
            )
            val isHealthy = result.statusCode in 200..399
            val message = if (isHealthy) {
                "Server online (HTTP ${result.statusCode} • ${result.latencyMs} ms)"
            } else {
                "Gagal terhubung (${result.errorDetail ?: "HTTP ${result.statusCode}"})"
            }
            if (!isHealthy) {
                HotsMonNotificationHelper.dispatchPushNotification(
                    context = appContext,
                    repository = repository,
                    title = "Status Server HotsMon Bermasalah",
                    message = message,
                    targetUrl = AppPreferencesRepository.BASE_URL,
                    category = NotificationCategory.ALERT,
                    highPriority = true
                )
            }
            webState.update {
                it.copy(
                    isCheckingHealth = false,
                    snackbarMessage = message
                )
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
        }
    }

    fun clearNotificationHistory() {
        viewModelScope.launch {
            repository.clearNotificationHistory()
        }
    }

    fun clearWebCache(webView: WebView?) {
        webView?.clearCache(true)
        webState.update { it.copy(snackbarMessage = "Cache WebView dibersihkan.") }
    }

    fun clearSnackbar() {
        webState.update { it.copy(snackbarMessage = null) }
    }
}
