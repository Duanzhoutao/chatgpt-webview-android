package com.example.gptweb

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.res.Configuration
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.gptweb.web.AppWebChromeClient
import com.example.gptweb.web.AppWebViewClient
import com.example.gptweb.web.WebAppConfig
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var webViewContainer: FrameLayout
    private lateinit var popupWebViewContainer: FrameLayout
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var errorContainer: View
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button

    private var webView: WebView? = null
    private var popupWebView: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var lastBackPressedAt = 0L
    private var pendingRestoreState: Bundle? = null
    private var lastKnownUrl: String = WebAppConfig.HOME_URL

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            val uris = if (result.resultCode == RESULT_OK) {
                parseSelectedFiles(result.data)
            } else {
                null
            }
            callback.onReceiveValue(uris)
            fileChooserCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        webViewContainer = findViewById(R.id.webViewContainer)
        popupWebViewContainer = findViewById(R.id.popupWebViewContainer)
        progressBar = findViewById(R.id.progressBar)
        errorContainer = findViewById(R.id.errorContainer)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
        retryButton = findViewById(R.id.retryButton)

        lastKnownUrl = savedInstanceState?.getString(STATE_LAST_URL, WebAppConfig.HOME_URL)
            ?: WebAppConfig.HOME_URL
        pendingRestoreState = savedInstanceState?.getBundle(STATE_WEBVIEW_BUNDLE)

        retryButton.setOnClickListener {
            hideErrorState()
            webView?.reload() ?: attachWebView(loadImmediately = true)
        }

        configureWindowInsets()
        configureSystemBars()
        initializeCookieManager()
        initializeSafeBrowsing()
        attachWebView(loadImmediately = true)
        registerBackHandler()
    }

    private fun configureWindowInsets() {
        val root = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = max(systemBars.bottom, imeInsets.bottom)
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun configureSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val isLightBars = !isDarkMode()
            isAppearanceLightStatusBars = isLightBars
            isAppearanceLightNavigationBars = isLightBars
        }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun initializeCookieManager() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
    }

    private fun initializeSafeBrowsing() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
            androidx.webkit.WebViewCompat.startSafeBrowsing(this) { }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun attachWebView(loadImmediately: Boolean) {
        val newWebView = createConfiguredWebView(isPopup = false)

        webViewContainer.removeAllViews()
        webViewContainer.addView(newWebView)
        webView = newWebView

        val restoreState = pendingRestoreState
        pendingRestoreState = null

        if (restoreState != null) {
            val restoredState = newWebView.restoreState(restoreState)
            if (restoredState == null && loadImmediately) {
                newWebView.loadUrl(lastKnownUrl)
            }
        } else if (loadImmediately) {
            newWebView.loadUrl(lastKnownUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(isPopup: Boolean): WebView {
        val newWebView = WebView(this)
        val cookieManager = CookieManager.getInstance()

        newWebView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        // Keep WebView capabilities explicit so login, storage, and upload flows behave predictably.
        newWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(newWebView.settings, true)
        }

        cookieManager.setAcceptThirdPartyCookies(newWebView, true)

        newWebView.webViewClient = createWebViewClient(isPopup)
        newWebView.webChromeClient = createWebChromeClient(isPopup)
        newWebView.setDownloadListener(createDownloadListener())
        newWebView.isFocusable = true
        newWebView.isFocusableInTouchMode = true

        return newWebView
    }

    private fun createWebViewClient(isPopup: Boolean): WebViewClient {
        return AppWebViewClient(
            isInternalUri = ::isInternalUri,
            onPageStartedCallback = { _, _, favicon ->
                handlePageStarted(favicon)
            },
            onPageFinishedCallback = { _, url ->
                if (!isPopup) {
                    lastKnownUrl = url ?: lastKnownUrl
                    hideErrorState()
                }
                progressBar.isIndeterminate = false
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
            },
            onMainFrameErrorCallback = { _, title, message ->
                progressBar.isIndeterminate = false
                progressBar.visibility = View.GONE
                if (isPopup) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                } else {
                    showErrorState(title, message)
                }
            },
            onUpdateVisitedHistoryCallback = { url ->
                if (!isPopup) {
                    lastKnownUrl = url ?: lastKnownUrl
                }
            },
            onOpenExternalUriCallback = { uri ->
                if (isPopup) {
                    popupWebView?.loadUrl(uri.toString())
                } else {
                    openExternalUri(uri)
                }
            },
            onHandleSpecialUriCallback = { uri ->
                handleSpecialUri(uri)
            },
            onRenderProcessGoneCallback = { didCrash, currentUrl ->
                if (isPopup) {
                    Toast.makeText(this, R.string.popup_closed, Toast.LENGTH_SHORT).show()
                    closePopupWebView()
                    true
                } else {
                    handleRenderProcessGone(didCrash, currentUrl)
                }
            },
        )
    }

    private fun createWebChromeClient(isPopup: Boolean): WebChromeClient {
        return AppWebChromeClient(
            onProgressChangedCallback = { progress ->
                progressBar.isIndeterminate = false
                progressBar.progress = progress
                progressBar.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
            },
            onShowFileChooserCallback = { callback, params ->
                launchFileChooser(callback, params)
            },
            onCreatePopupWebViewCallback = {
                if (isPopup) {
                    popupWebView
                } else {
                    createPopupWebView()
                }
            },
            onCloseWindowCallback = { closingWindow ->
                if (closingWindow == popupWebView || isPopup) {
                    closePopupWebView()
                }
            },
        )
    }

    private fun createPopupWebView(): WebView {
        popupWebView?.let { existingPopup ->
            return existingPopup
        }

        val newPopupWebView = createConfiguredWebView(isPopup = true)
        popupWebViewContainer.removeAllViews()
        popupWebViewContainer.addView(newPopupWebView)
        popupWebViewContainer.visibility = View.VISIBLE
        popupWebView = newPopupWebView
        return newPopupWebView
    }

    private fun closePopupWebView() {
        destroySingleWebView(popupWebView)
        popupWebViewContainer.removeAllViews()
        popupWebViewContainer.visibility = View.GONE
        popupWebView = null
        progressBar.isIndeterminate = false
        progressBar.visibility = View.GONE
    }

    private fun createDownloadListener(): DownloadListener {
        return DownloadListener { url, _, _, _, _ ->
            val uri = url?.toUri() ?: return@DownloadListener
            Toast.makeText(
                this,
                R.string.download_opening_in_browser,
                Toast.LENGTH_SHORT,
            ).show()
            openExternalUri(uri)
        }
    }

    private fun handlePageStarted(favicon: Bitmap?) {
        progressBar.isIndeterminate = favicon == null
        progressBar.visibility = View.VISIBLE
        hideErrorState()
    }

    private fun launchFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?,
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback

        val acceptedTypes = params
            ?.acceptTypes
            ?.mapNotNull { type -> type.takeIf { it.isNotBlank() } }
            ?.distinct()
            .orEmpty()

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (acceptedTypes.size == 1 && !acceptedTypes[0].contains(",")) {
                acceptedTypes[0]
            } else {
                "*/*"
            }
            putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,
            )
            if (acceptedTypes.isNotEmpty()) {
                putExtra(Intent.EXTRA_MIME_TYPES, acceptedTypes.toTypedArray())
            }
        }

        return try {
            fileChooserLauncher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            Toast.makeText(this, R.string.file_picker_unavailable, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun parseSelectedFiles(data: Intent?): Array<Uri>? {
        if (data == null) {
            return null
        }

        val clipData = data.clipData
        if (clipData != null && clipData.itemCount > 0) {
            return Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
        }

        val singleUri = data.data
        return if (singleUri != null) arrayOf(singleUri) else null
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentPopupWebView = popupWebView
                    if (currentPopupWebView != null) {
                        if (currentPopupWebView.canGoBack()) {
                            currentPopupWebView.goBack()
                        } else {
                            closePopupWebView()
                        }
                        return
                    }

                    val currentWebView = webView
                    if (currentWebView != null && currentWebView.canGoBack()) {
                        hideErrorState()
                        currentWebView.goBack()
                        return
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastBackPressedAt <= WebAppConfig.EXIT_CONFIRM_WINDOW_MS) {
                        finish()
                    } else {
                        lastBackPressedAt = now
                        Toast.makeText(
                            this@MainActivity,
                            R.string.press_back_again_to_exit,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }

    private fun isInternalUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return false
        }

        val host = uri.host?.lowercase() ?: return false
        return WebAppConfig.INTERNAL_HOST_SUFFIX_ALLOWLIST.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun openExternalUri(uri: Uri) {
        val externalIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        try {
            startActivity(externalIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSpecialUri(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            "mailto", "tel", "sms" -> {
                openSpecialUri(uri)
                true
            }

            "intent" -> {
                openIntentUri(uri)
                true
            }

            else -> {
                Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun openIntentUri(uri: Uri) {
        val parsedIntent = try {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
            return
        }

        val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
        if (!fallbackUrl.isNullOrBlank()) {
            val fallbackUri = fallbackUrl.toUri()
            if (fallbackUri.scheme in listOf("https", "http")) {
                if (WebAppConfig.OPEN_ALL_HTTP_URLS_IN_APP || isInternalUri(fallbackUri)) {
                    webView?.loadUrl(fallbackUri.toString())
                } else {
                    openExternalUri(fallbackUri)
                }
                return
            }
        }

        // Drop explicit component routing to reduce the chance of unsafe intent launches.
        parsedIntent.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
        }

        if (WebAppConfig.OPEN_ALL_HTTP_URLS_IN_APP) {
            Toast.makeText(this, R.string.external_app_login_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(parsedIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSpecialUri(uri: Uri) {
        val intent = when (uri.scheme?.lowercase()) {
            "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
            "tel" -> Intent(Intent.ACTION_DIAL, uri)
            "sms" -> Intent(Intent.ACTION_SENDTO, uri)
            else -> Intent(Intent.ACTION_VIEW, uri)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleRenderProcessGone(didCrash: Boolean, currentUrl: String?): Boolean {
        lastKnownUrl = currentUrl ?: lastKnownUrl
        Toast.makeText(
            this,
            if (didCrash) R.string.webview_crashed else R.string.webview_recovered,
            Toast.LENGTH_SHORT,
        ).show()
        recreateWebView()
        return true
    }

    private fun recreateWebView() {
        destroyMainWebView()
        attachWebView(loadImmediately = true)
    }

    private fun showErrorState(title: String, message: String) {
        errorTitle.text = title
        errorMessage.text = message
        errorContainer.visibility = View.VISIBLE
    }

    private fun hideErrorState() {
        errorContainer.visibility = View.GONE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val webState = Bundle()
        webView?.saveState(webState)
        outState.putBundle(STATE_WEBVIEW_BUNDLE, webState)
        outState.putString(STATE_LAST_URL, lastKnownUrl)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        popupWebView?.onResume()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        webView?.onPause()
        popupWebView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        CookieManager.getInstance().flush()
        closePopupWebView()
        destroyMainWebView()
        super.onDestroy()
    }

    private fun destroyMainWebView() {
        destroySingleWebView(webView)
        webViewContainer.removeAllViews()
        webView = null
    }

    private fun destroySingleWebView(targetWebView: WebView?) {
        targetWebView?.let { currentWebView ->
            runCatching {
                currentWebView.stopLoading()
                currentWebView.webChromeClient = null
                currentWebView.webViewClient = WebViewClient()
                currentWebView.setDownloadListener(null)
                currentWebView.clearHistory()
                currentWebView.loadUrl("about:blank")
                currentWebView.removeAllViews()
                currentWebView.destroy()
            }
        }
    }

    companion object {
        private const val STATE_WEBVIEW_BUNDLE = "state_webview_bundle"
        private const val STATE_LAST_URL = "state_last_url"
    }
}
