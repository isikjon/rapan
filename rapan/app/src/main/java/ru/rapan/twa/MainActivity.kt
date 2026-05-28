package ru.rapan.twa

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ru.rapan.miniapp.BuildConfig
import ru.rapan.miniapp.R

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val isDriverBuild = BuildConfig.APPLICATION_ID == "ru.rapan.driver"
    private val allowedHosts = setOf("рапан-такси.рф", "xn----7sbab4blsohri.xn--p1ai")
    private val primaryHostUrl = "https://рапан-такси.рф"
    private val punycodeHostUrl = "https://xn----7sbab4blsohri.xn--p1ai"
    private val prefsName = "rapan_webview_state"
    private val keyLastUrl = "last_url"
    private val keyPersistedCookies = "persisted_cookies"
    private val locationPermissionRequestCode = 4101
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var currentMainFrameErrorUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(255, 215, 0)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.mainWebView)
        configureWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(getInitialUrl())
        }
    }

    private fun configureWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        restorePersistedCookies()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.loadsImagesAutomatically = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.setGeolocationEnabled(true)
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin.isNullOrBlank() || callback == null) {
                    super.onGeolocationPermissionsShowPrompt(origin, callback)
                    return
                }

                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        locationPermissions,
                        locationPermissionRequestCode
                    )
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val rawUrl = view?.hitTestResult?.extra
                val uri = rawUrl?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (uri != null && handleExternalScheme(uri)) {
                    return true
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                currentMainFrameErrorUrl = null
                persistCookies()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
                if (shouldOpenHttpExternally(uri)) {
                    return safeStartActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                if (handleExternalScheme(uri)) return true

                return false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                if (shouldOpenHttpExternally(uri)) {
                    return safeStartActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                if (handleExternalScheme(uri)) return true

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                handleFinishedPage(view, url)
                applyDriverHeaderlessModeIfNeeded()
                persistCookies()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame != true) return

                val statusCode = errorResponse?.statusCode ?: return
                if (statusCode < 400) return

                val failedUrl = request.url?.toString().orEmpty()
                currentMainFrameErrorUrl = failedUrl
                clearSavedUrl()

                val fallbackUrl = getErrorFallbackUrl()
                if (fallbackUrl != failedUrl) {
                    view?.post { view.loadUrl(fallbackUrl) }
                }
            }
        }
    }

    private fun getInitialUrl(): String {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val lastUrl = prefs.getString(keyLastUrl, null)
        if (!lastUrl.isNullOrBlank() && isAllowedHost(lastUrl)) {
            return normalizeRapanUrl(lastUrl)
        }

        val persistedCookies = prefs.getString(keyPersistedCookies, null).orEmpty()
        if (!persistedCookies.isBlank()) {
            return BuildConfig.RETURNING_BROWSER_URL
        }

        return BuildConfig.START_URL
    }

    private fun handleFinishedPage(view: WebView?, url: String?) {
        if (url.isNullOrBlank() || !isAllowedHost(url) || url == currentMainFrameErrorUrl) return

        view?.evaluateJavascript(
            """
                (function () {
                    var title = (document.title || '').toLowerCase();
                    var body = (document.body && document.body.innerText || '').toLowerCase();
                    return title.indexOf('страница не найдена') !== -1 ||
                        body.indexOf('error 404') !== -1 ||
                        body.indexOf('страница не найдена') !== -1;
                })();
            """.trimIndent()
        ) { result ->
            if (result == "true") {
                currentMainFrameErrorUrl = url
                clearSavedUrl()
                val fallbackUrl = getErrorFallbackUrl()
                if (normalizeRapanUrl(url) != fallbackUrl) {
                    webView.loadUrl(fallbackUrl)
                }
            } else {
                saveCurrentUrl(url)
            }
        }
    }

    private fun saveCurrentUrl(url: String?) {
        if (url.isNullOrBlank() || !isAllowedHost(url)) return

        getSharedPreferences(prefsName, MODE_PRIVATE)
            .edit()
            .putString(keyLastUrl, normalizeRapanUrl(url))
            .apply()
    }

    private fun clearSavedUrl() {
        getSharedPreferences(prefsName, MODE_PRIVATE)
            .edit()
            .remove(keyLastUrl)
            .apply()
    }

    private fun getErrorFallbackUrl(): String {
        return BuildConfig.START_URL
    }

    private fun normalizeRapanUrl(url: String): String {
        return url
            .replace("https://рапан-такси.рф", punycodeHostUrl)
            .replace("http://рапан-такси.рф", punycodeHostUrl)
            .replace("http://xn----7sbab4blsohri.xn--p1ai", punycodeHostUrl)
    }

    private fun handleExternalScheme(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme.isNullOrBlank()) return false

        if (scheme == "http" || scheme == "https") return false

        if (scheme == "intent") {
            // We've handled this URL type even if no app/fallback exists.
            handleIntentScheme(uri)
            return true
        }

        when (scheme) {
            "tel" -> {
                safeStartActivity(Intent(Intent.ACTION_DIAL, uri))
            }
            "mailto", "sms", "smsto", "mms", "mmsto" -> {
                safeStartActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            else -> safeStartActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        // Prevent WebView from trying to load unknown custom schemes itself.
        return true
    }

    private fun handleIntentScheme(uri: Uri): Boolean {
        val intent = runCatching {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return false

        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null

        if (safeStartActivity(intent)) return true

        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
        if (!fallbackUrl.isNullOrBlank()) {
            return safeStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
        }

        return false
    }

    private fun safeStartActivity(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun shouldOpenHttpExternally(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host !in allowedHosts
    }

    private fun isAllowedHost(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host in allowedHosts
    }

    private fun hasLocationPermission(): Boolean {
        return locationPermissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun restorePersistedCookies() {
        val rawCookies = getSharedPreferences(prefsName, MODE_PRIVATE)
            .getString(keyPersistedCookies, null)
            ?.trim()
            .orEmpty()

        if (rawCookies.isBlank()) return

        val cookieManager = CookieManager.getInstance()
        parseCookiePairs(rawCookies)
            .forEach { pair ->
                // Persist cookie as explicit long-living value so WebView survives app/device restart.
                val persistentCookie = "$pair; Path=/; Max-Age=2592000; SameSite=Lax"
                cookieManager.setCookie(primaryHostUrl, persistentCookie)
                cookieManager.setCookie(punycodeHostUrl, persistentCookie)
            }
        cookieManager.flush()
    }

    private fun persistCookies() {
        val cookieManager = CookieManager.getInstance()
        val mergedCookies = parseCookiePairs(
            listOfNotNull(
                cookieManager.getCookie(primaryHostUrl),
                cookieManager.getCookie(punycodeHostUrl)
            ).joinToString("; ")
        ).joinToString("; ")

        if (mergedCookies.isNotBlank()) {
            getSharedPreferences(prefsName, MODE_PRIVATE)
                .edit()
                .putString(keyPersistedCookies, mergedCookies)
                .apply()
        }

        cookieManager.flush()
    }

    private fun parseCookiePairs(rawCookies: String): List<String> {
        val cookiesByName = linkedMapOf<String, String>()
        rawCookies.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { pair ->
                val name = pair.substringBefore("=").trim()
                if (name.isNotBlank()) {
                    cookiesByName[name] = pair
                }
            }
        return cookiesByName.values.toList()
    }

    private fun applyDriverHeaderlessModeIfNeeded() {
        if (!isDriverBuild) return
        if (!::webView.isInitialized) return

        val script = """
            (function () {
                if (document.getElementById('rapan-app-hide-header')) return;
                var style = document.createElement('style');
                style.id = 'rapan-app-hide-header';
                style.innerHTML =
                    '.rapan-navbar{display:none !important;}' +
                    '.rapan-navbar-dropdown{display:none !important;}' +
                    '.article{padding-top:0 !important;}' +
                    '.rapan-body{margin-top:0 !important;}' +
                    '.main_body{margin-top:0 !important;}';
                document.head.appendChild(style);
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    override fun onPause() {
        super.onPause()
        saveCurrentUrl(webView.url)
        persistCookies()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentUrl(webView.url)
        persistCookies()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            saveCurrentUrl(webView.url)
            persistCookies()
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != locationPermissionRequestCode) return

        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        val origin = pendingGeoOrigin
        val callback = pendingGeoCallback
        pendingGeoOrigin = null
        pendingGeoCallback = null

        if (!origin.isNullOrBlank() && callback != null) {
            callback.invoke(origin, granted, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
