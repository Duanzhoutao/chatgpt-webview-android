package com.example.gptweb.web

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class AppWebViewClient(
    private val isInternalUri: (Uri) -> Boolean,
    private val onPageStartedCallback: (WebView?, String?, Bitmap?) -> Unit,
    private val onPageFinishedCallback: (WebView?, String?) -> Unit,
    private val onMainFrameErrorCallback: (WebView?, String, String) -> Unit,
    private val onUpdateVisitedHistoryCallback: (String?) -> Unit,
    private val onOpenExternalUriCallback: (Uri) -> Unit,
    private val onHandleSpecialUriCallback: (Uri) -> Boolean,
    private val onRenderProcessGoneCallback: (Boolean, String?) -> Boolean,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val uri = request?.url ?: return false
        return handleUri(uri)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val uri = url?.let { Uri.parse(it) } ?: return false
        return handleUri(uri)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStartedCallback(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinishedCallback(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onUpdateVisitedHistoryCallback(url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        val description = error?.description?.toString().orEmpty()
        if (request?.isForMainFrame == true) {
            onMainFrameErrorCallback(
                view,
                "Unable to load page",
                if (description.isBlank()) {
                    "Check your network connection and try again."
                } else {
                    description
                },
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && errorResponse != null) {
            onMainFrameErrorCallback(
                view,
                "Server error",
                "The page returned HTTP ${errorResponse.statusCode}. Tap retry to try again.",
            )
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        handler?.cancel()
        onMainFrameErrorCallback(
            view,
            "Secure connection failed",
            "The site could not be verified. For safety, the connection was blocked.",
        )
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        return onRenderProcessGoneCallback(detail?.didCrash() == true, view?.url)
    }

    private fun handleUri(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                if (WebAppConfig.OPEN_ALL_HTTP_URLS_IN_APP || isInternalUri(uri)) {
                    false
                } else {
                    onOpenExternalUriCallback(uri)
                    true
                }
            }

            "about", "data", "blob" -> false
            else -> onHandleSpecialUriCallback(uri)
        }
    }
}
