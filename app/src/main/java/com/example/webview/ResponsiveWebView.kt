package com.example.webview

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.AppPreferencesRepository

private const val MOBILE_USER_AGENT_SUFFIX = " HotsMonAndroid/1.0"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 HotsMonAndroid/1.0"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ResponsiveWebView(
    urlToLoad: String,
    desktopMode: Boolean,
    textZoomPercent: Int,
    hasNotificationPermission: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onPageStarted: (url: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageFinished: (url: String?, title: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavigationChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageError: (String?) -> Unit,
    onWebNotification: (title: String, body: String, url: String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    val defaultUserAgent = remember(context) {
        WebSettings.getDefaultUserAgent(context) + MOBILE_USER_AGENT_SUFFIX
    }

    val permissionGrantedState = remember { mutableStateOf(hasNotificationPermission) }
    LaunchedEffect(hasNotificationPermission) {
        permissionGrantedState.value = hasNotificationPermission
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val currentWebView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(currentWebView, true)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
            }

            val bridge = WebNotificationBridge(
                onNotify = onWebNotification,
                onRequestPermission = onRequestNotificationPermission,
                checkPermissionGranted = { permissionGrantedState.value }
            )
            addJavascriptInterface(bridge, WebNotificationBridge.BRIDGE_NAME)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUri = request?.url ?: return false
                    val scheme = targetUri.scheme?.lowercase().orEmpty()
                    val host = targetUri.host?.lowercase().orEmpty()

                    if (scheme == "https" && (host == "hotsmon.bondanglangit.com" || host.endsWith(".bondanglangit.com"))) {
                        return false
                    }

                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onPageStarted(url, view?.canGoBack() == true, view?.canGoForward() == true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        WebNotificationBridge.RESPONSIVE_AND_NOTIFICATION_JS,
                        null
                    )
                    CookieManager.getInstance().flush()
                    onPageFinished(
                        url,
                        view?.title,
                        view?.canGoBack() == true,
                        view?.canGoForward() == true
                    )
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onPageError(error?.description?.toString())
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.cancel()
                    onPageError("Sertifikat SSL tidak valid atau koneksi tidak aman.")
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    onNavigationChanged(view?.canGoBack() == true, view?.canGoForward() == true)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    onProgressChanged(newProgress)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    return try {
                        val intent = fileChooserParams?.createIntent()
                            ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                        filePickerLauncher.launch(intent)
                        true
                    } catch (_: Exception) {
                        filePathCallback = null
                        false
                    }
                }
            }

            loadUrl(AppPreferencesRepository.BASE_URL)
        }
    }

    LaunchedEffect(webView) {
        onWebViewCreated(webView)
    }

    LaunchedEffect(desktopMode) {
        val targetUa = if (desktopMode) DESKTOP_USER_AGENT else defaultUserAgent
        if (webView.settings.userAgentString != targetUa) {
            webView.settings.userAgentString = targetUa
            webView.reload()
        }
    }

    LaunchedEffect(textZoomPercent) {
        if (webView.settings.textZoom != textZoomPercent) {
            webView.settings.textZoom = textZoomPercent
        }
    }

    LaunchedEffect(urlToLoad) {
        if (urlToLoad.isNotBlank() && webView.url != urlToLoad) {
            webView.loadUrl(urlToLoad)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}
