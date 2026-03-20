package com.example.gptweb.web

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class AppWebChromeClient(
    private val onProgressChangedCallback: (Int) -> Unit,
    private val onShowFileChooserCallback:
        (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Boolean,
    private val onCreatePopupWebViewCallback: () -> WebView?,
    private val onCloseWindowCallback: (WebView?) -> Unit,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChangedCallback(newProgress)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams?,
    ): Boolean {
        return onShowFileChooserCallback(filePathCallback, fileChooserParams)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean {
        val popupWebView = onCreatePopupWebViewCallback() ?: return false
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView?) {
        onCloseWindowCallback(window)
    }
}
