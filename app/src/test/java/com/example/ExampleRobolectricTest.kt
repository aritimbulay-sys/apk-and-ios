package com.example

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.NotificationChannels
import com.example.webview.WebNotificationBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("HotsMon", appName)
  }

  @Test
  fun `notification channels are registered`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    NotificationChannels.ensureCreated(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      assertNotNull(manager.getNotificationChannel(context.getString(R.string.channel_alerts_id)))
      assertNotNull(manager.getNotificationChannel(context.getString(R.string.channel_updates_id)))
    }
  }

  @Test
  fun `web notification bridge forwards sanitized notification`() {
    var receivedTitle = ""
    var receivedBody = ""
    var receivedUrl = ""
    val bridge = WebNotificationBridge(
      onNotify = { title, body, url ->
        receivedTitle = title
        receivedBody = body
        receivedUrl = url
      },
      onRequestPermission = {},
      checkPermissionGranted = { true }
    )
    bridge.notify("Alert Node A", "CPU 95%", "https://hotsmon.bondanglangit.com/nodes")
    assertEquals("Alert Node A", receivedTitle)
    assertEquals("CPU 95%", receivedBody)
    assertEquals("https://hotsmon.bondanglangit.com/nodes", receivedUrl)
    assertTrue(bridge.isNotificationGranted())
  }
}
