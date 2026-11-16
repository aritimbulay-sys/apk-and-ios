package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val Context.hotsMonDataStore: DataStore<Preferences> by preferencesDataStore(name = "hotsmon_prefs")

enum class NotificationCategory {
    ALERT,
    RECOVERY,
    WEB_PUSH,
    TEST
}

data class NotificationRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val targetUrl: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val category: NotificationCategory = NotificationCategory.WEB_PUSH,
    val isRead: Boolean = false
)

data class HotsMonPreferences(
    val backgroundMonitorEnabled: Boolean = true,
    val monitorIntervalMinutes: Long = 15L,
    val desktopModeEnabled: Boolean = false,
    val fullscreenModeEnabled: Boolean = false,
    val textZoomPercent: Int = 100,
    val lastKnownStatusCode: Int = 0,
    val lastCheckedTimestamp: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val notificationPermissionBannerDismissed: Boolean = false,
    val notificationHistory: List<NotificationRecord> = emptyList()
)

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val BACKGROUND_MONITOR_ENABLED = booleanPreferencesKey("background_monitor_enabled")
        val MONITOR_INTERVAL_MINUTES = longPreferencesKey("monitor_interval_minutes")
        val DESKTOP_MODE_ENABLED = booleanPreferencesKey("desktop_mode_enabled")
        val FULLSCREEN_MODE_ENABLED = booleanPreferencesKey("fullscreen_mode_enabled")
        val TEXT_ZOOM_PERCENT = intPreferencesKey("text_zoom_percent")
        val LAST_KNOWN_STATUS_CODE = intPreferencesKey("last_known_status_code")
        val LAST_CHECKED_TIMESTAMP = longPreferencesKey("last_checked_timestamp")
        val LAST_LATENCY_MS = longPreferencesKey("last_latency_ms")
        val PERMISSION_BANNER_DISMISSED = booleanPreferencesKey("permission_banner_dismissed")
        val NOTIFICATION_HISTORY_JSON = stringPreferencesKey("notification_history_json")
    }

    val preferencesFlow: Flow<HotsMonPreferences> = context.hotsMonDataStore.data.map { prefs ->
        HotsMonPreferences(
            backgroundMonitorEnabled = prefs[Keys.BACKGROUND_MONITOR_ENABLED] ?: true,
            monitorIntervalMinutes = prefs[Keys.MONITOR_INTERVAL_MINUTES] ?: 15L,
            desktopModeEnabled = prefs[Keys.DESKTOP_MODE_ENABLED] ?: false,
            fullscreenModeEnabled = prefs[Keys.FULLSCREEN_MODE_ENABLED] ?: false,
            textZoomPercent = prefs[Keys.TEXT_ZOOM_PERCENT] ?: 100,
            lastKnownStatusCode = prefs[Keys.LAST_KNOWN_STATUS_CODE] ?: 0,
            lastCheckedTimestamp = prefs[Keys.LAST_CHECKED_TIMESTAMP] ?: 0L,
            lastLatencyMs = prefs[Keys.LAST_LATENCY_MS] ?: 0L,
            notificationPermissionBannerDismissed = prefs[Keys.PERMISSION_BANNER_DISMISSED] ?: false,
            notificationHistory = parseHistory(prefs[Keys.NOTIFICATION_HISTORY_JSON])
        )
    }

    suspend fun getSnapshot(): HotsMonPreferences = preferencesFlow.first()

    suspend fun setBackgroundMonitorEnabled(enabled: Boolean) {
        context.hotsMonDataStore.edit { it[Keys.BACKGROUND_MONITOR_ENABLED] = enabled }
    }

    suspend fun setMonitorIntervalMinutes(minutes: Long) {
        context.hotsMonDataStore.edit { it[Keys.MONITOR_INTERVAL_MINUTES] = minutes.coerceAtLeast(15L) }
    }

    suspend fun setDesktopModeEnabled(enabled: Boolean) {
        context.hotsMonDataStore.edit { it[Keys.DESKTOP_MODE_ENABLED] = enabled }
    }

    suspend fun setFullscreenModeEnabled(enabled: Boolean) {
        context.hotsMonDataStore.edit { it[Keys.FULLSCREEN_MODE_ENABLED] = enabled }
    }

    suspend fun setTextZoomPercent(zoom: Int) {
        context.hotsMonDataStore.edit { it[Keys.TEXT_ZOOM_PERCENT] = zoom.coerceIn(80, 140) }
    }

    suspend fun setPermissionBannerDismissed(dismissed: Boolean) {
        context.hotsMonDataStore.edit { it[Keys.PERMISSION_BANNER_DISMISSED] = dismissed }
    }

    suspend fun updateServerHealth(statusCode: Int, latencyMs: Long, checkedAt: Long) {
        context.hotsMonDataStore.edit { prefs ->
            prefs[Keys.LAST_KNOWN_STATUS_CODE] = statusCode
            prefs[Keys.LAST_LATENCY_MS] = latencyMs
            prefs[Keys.LAST_CHECKED_TIMESTAMP] = checkedAt
        }
    }

    suspend fun addNotificationRecord(record: NotificationRecord) {
        context.hotsMonDataStore.edit { prefs ->
            val current = parseHistory(prefs[Keys.NOTIFICATION_HISTORY_JSON]).toMutableList()
            current.add(0, record)
            val trimmed = current.take(50)
            prefs[Keys.NOTIFICATION_HISTORY_JSON] = serializeHistory(trimmed)
        }
    }

    suspend fun markAllNotificationsRead() {
        context.hotsMonDataStore.edit { prefs ->
            val updated = parseHistory(prefs[Keys.NOTIFICATION_HISTORY_JSON]).map {
                it.copy(isRead = true)
            }
            prefs[Keys.NOTIFICATION_HISTORY_JSON] = serializeHistory(updated)
        }
    }

    suspend fun clearNotificationHistory() {
        context.hotsMonDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_HISTORY_JSON] = "[]"
        }
    }

    private fun parseHistory(jsonString: String?): List<NotificationRecord> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonString)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val categoryName = obj.optString("category", NotificationCategory.WEB_PUSH.name)
                    val category = NotificationCategory.entries.firstOrNull { it.name == categoryName }
                        ?: NotificationCategory.WEB_PUSH
                    add(
                        NotificationRecord(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", "HotsMon"),
                            message = obj.optString("message", ""),
                            targetUrl = obj.optString("targetUrl", BASE_URL),
                            timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                            category = category,
                            isRead = obj.optBoolean("isRead", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeHistory(records: List<NotificationRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            val obj = JSONObject().apply {
                put("id", record.id)
                put("title", record.title)
                put("message", record.message)
                put("targetUrl", record.targetUrl)
                put("timestampMillis", record.timestampMillis)
                put("category", record.category.name)
                put("isRead", record.isRead)
            }
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        const val BASE_URL = "https://hotsmon.bondanglangit.com"
    }
}
