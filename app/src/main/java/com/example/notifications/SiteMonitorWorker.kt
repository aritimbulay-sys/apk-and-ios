package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppPreferencesRepository
import com.example.data.NotificationCategory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SiteMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = AppPreferencesRepository(applicationContext)
        val snapshot = repository.getSnapshot()
        if (!snapshot.backgroundMonitorEnabled) {
            return Result.success()
        }

        val checkResult = checkSiteHealth()
        val previousStatus = snapshot.lastKnownStatusCode
        repository.updateServerHealth(
            statusCode = checkResult.statusCode,
            latencyMs = checkResult.latencyMs,
            checkedAt = System.currentTimeMillis()
        )

        val wasHealthy = previousStatus in 200..399
        val isNowHealthy = checkResult.statusCode in 200..399

        if (previousStatus != 0) {
            if (wasHealthy && !isNowHealthy) {
                val reason = if (checkResult.statusCode == -1) {
                    "Server tidak merespons (${checkResult.errorDetail ?: "koneksi gagal"})."
                } else {
                    "Server mengembalikan kode HTTP ${checkResult.statusCode}."
                }
                HotsMonNotificationHelper.dispatchPushNotification(
                    context = applicationContext,
                    repository = repository,
                    title = "Peringatan HotsMon: Gangguan Terdeteksi",
                    message = "$reason Ketuk untuk membuka dashboard HotsMon.",
                    targetUrl = AppPreferencesRepository.BASE_URL,
                    category = NotificationCategory.ALERT,
                    highPriority = true
                )
            } else if (!wasHealthy && isNowHealthy) {
                HotsMonNotificationHelper.dispatchPushNotification(
                    context = applicationContext,
                    repository = repository,
                    title = "HotsMon Kembali Online",
                    message = "Server kembali normal (HTTP ${checkResult.statusCode}, ${checkResult.latencyMs} ms).",
                    targetUrl = AppPreferencesRepository.BASE_URL,
                    category = NotificationCategory.RECOVERY,
                    highPriority = false
                )
            }
        }

        return Result.success()
    }

    data class HealthCheckResult(
        val statusCode: Int,
        val latencyMs: Long,
        val errorDetail: String? = null
    )

    companion object {
        private const val WORK_NAME = "hotsmon_periodic_monitor"

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        fun checkSiteHealth(url: String = AppPreferencesRepository.BASE_URL): HealthCheckResult {
            val startNs = System.nanoTime()
            return try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "HotsMon-Android-Monitor/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                    HealthCheckResult(
                        statusCode = response.code,
                        latencyMs = elapsedMs
                    )
                }
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                HealthCheckResult(
                    statusCode = -1,
                    latencyMs = elapsedMs,
                    errorDetail = e.localizedMessage ?: "Timeout"
                )
            }
        }

        fun schedule(context: Context, enabled: Boolean, intervalMinutes: Long) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SiteMonitorWorker>(
                intervalMinutes.coerceAtLeast(15L),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
