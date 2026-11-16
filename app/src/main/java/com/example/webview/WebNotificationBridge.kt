package com.example.webview

import android.webkit.JavascriptInterface
import com.example.data.AppPreferencesRepository

/**
 * Exposes native push notification capabilities to web pages loaded in the WebView
 * via window.HotsMonBridge and shims standard browser window.Notification calls.
 */
class WebNotificationBridge(
    private val onNotify: (title: String, body: String, url: String) -> Unit,
    private val onRequestPermission: () -> Unit,
    private val checkPermissionGranted: () -> Boolean
) {

    @JavascriptInterface
    fun notify(title: String?, body: String?, url: String?) {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: "HotsMon"
        val safeBody = body?.takeIf { it.isNotBlank() } ?: "Ada pembaruan status baru."
        val safeUrl = url?.takeIf { it.startsWith("https://") } ?: AppPreferencesRepository.BASE_URL
        onNotify(safeTitle, safeBody, safeUrl)
    }

    @JavascriptInterface
    fun isNotificationGranted(): Boolean = checkPermissionGranted()

    @JavascriptInterface
    fun requestNotificationPermission() {
        onRequestPermission()
    }

    companion object {
        const val BRIDGE_NAME = "HotsMonBridge"

        /**
         * Injects:
         * 1. Mobile responsive viewport meta tag if missing, plus overflow/table safeguards.
         * 2. Web Notification API polyfill bridging `new Notification(title, options)` to native Android push notifications.
         */
        val RESPONSIVE_AND_NOTIFICATION_JS: String = """
            (function() {
              try {
                var head = document.head || document.getElementsByTagName('head')[0];
                if (head) {
                  var viewport = document.querySelector('meta[name="viewport"]');
                  if (!viewport) {
                    viewport = document.createElement('meta');
                    viewport.name = 'viewport';
                    viewport.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, viewport-fit=cover';
                    head.appendChild(viewport);
                  }
                  if (!document.getElementById('hotsmon-mobile-responsive-style')) {
                    var style = document.createElement('style');
                    style.id = 'hotsmon-mobile-responsive-style';
                    style.textContent = [
                      'html, body { max-width: 100vw !important; overflow-x: hidden !important; -webkit-tap-highlight-color: transparent; }',
                      'img, svg, video, canvas { max-width: 100%; height: auto; }',
                      'table { display: block; max-width: 100%; overflow-x: auto; -webkit-overflow-scrolling: touch; }',
                      'input, select, textarea, button { font-size: max(16px, 1em); }'
                    ].join('\n');
                    head.appendChild(style);
                  }
                }

                if (window.HotsMonBridge && !window.__hotsMonNotificationPatched) {
                  window.__hotsMonNotificationPatched = true;
                  var NativeNotification = function(title, options) {
                    var opts = options || {};
                    var body = opts.body || '';
                    var url = (opts.data && opts.data.url) ? opts.data.url : window.location.href;
                    window.HotsMonBridge.notify(String(title || 'HotsMon'), String(body), String(url));
                  };
                  Object.defineProperty(NativeNotification, 'permission', {
                    get: function() {
                      return window.HotsMonBridge.isNotificationGranted() ? 'granted' : 'default';
                    }
                  });
                  NativeNotification.requestPermission = function(callback) {
                    window.HotsMonBridge.requestNotificationPermission();
                    var result = window.HotsMonBridge.isNotificationGranted() ? 'granted' : 'default';
                    if (typeof callback === 'function') {
                      callback(result);
                    }
                    return Promise.resolve(result);
                  };
                  window.Notification = NativeNotification;
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }
}
