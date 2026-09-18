package com.foliore.android.reader

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.foliore.android.R
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun ReaderImageDialog(publication: EpubPublication, document: String?, onDismiss: () -> Unit) {
    var web by remember(document) { mutableStateOf<WebView?>(null) }
    var failed by remember(document) { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val description = stringResource(R.string.reader_image_original)
    val closeLabel = stringResource(R.string.action_close)
    val zoomIn = stringResource(R.string.reader_image_zoom_in)
    val zoomOut = stringResource(R.string.reader_image_zoom_out)
    val fit = stringResource(R.string.reader_image_fit)
    val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > .5f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, lightSystemBars) {
            window?.setDimAmount(.28f)
            window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = lightSystemBars
                isAppearanceLightNavigationBars = lightSystemBars
            } }
            onDispose { }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                document == null -> CircularProgressIndicator(Modifier.size(28.dp))
                document.isEmpty() || failed -> Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { Text(stringResource(R.string.reader_image_failed)) }
                else -> AndroidView(
                    factory = { context -> WebView(context).also { view ->
                        web = view
                        val script = context.assets.open("reader/image-viewer.js").use { it.readBytes() }
                        view.setBackgroundColor(AndroidColor.TRANSPARENT)
                        with(view.settings) {
                            javaScriptEnabled = true; allowFileAccess = false; allowContentAccess = false
                            blockNetworkLoads = true; mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            builtInZoomControls = false; displayZoomControls = false
                            textZoom = 100
                        }
                        view.addJavascriptInterface(object {
                            @JavascriptInterface fun close() { view.post { if (view.isAttachedToWindow) dismiss() } }
                            @JavascriptInterface fun failed() { view.post { if (view.isAttachedToWindow) failed = true } }
                        }, "FolioreImage")
                        view.webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                                val uri = request.url
                                if (request.method == "GET" && uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.port == -1) {
                                    if (uri.path == "/_reader/image.html") return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(document.toByteArray(Charsets.UTF_8)))
                                    if (uri.path == "/_reader/image-viewer.js") return WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(script))
                                    if (uri.path?.startsWith("/publication/") == true) {
                                        val resource = runCatching { publication.resource(uri.path!!.removePrefix("/publication/")) }.getOrNull()?.takeIf { it.mime.startsWith("image/") }
                                        if (resource != null) return WebResourceResponse(resource.mime, null, ByteArrayInputStream(resource.bytes))
                                    }
                                }
                                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                            private fun required(request: WebResourceRequest) = request.isForMainFrame || request.url.path?.startsWith("/publication/") == true || request.url.path == "/_reader/image-viewer.js"
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (required(request)) failed = true }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) { if (required(request)) failed = true }
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { failed = true; return true }
                        }
                        view.loadUrl("${EpubPublication.ORIGIN}/_reader/image.html")
                    } },
                    onRelease = { it.stopLoading(); it.removeJavascriptInterface("FolioreImage"); it.webViewClient = WebViewClient(); it.destroy() },
                    modifier = Modifier.fillMaxSize().semantics {
                        contentDescription = description
                        customActions = listOf(
                            CustomAccessibilityAction(closeLabel) { dismiss(); true },
                            CustomAccessibilityAction(zoomIn) { web?.evaluateJavascript("window.FolioreImageViewer?.zoomIn()", null); true },
                            CustomAccessibilityAction(zoomOut) { web?.evaluateJavascript("window.FolioreImageViewer?.zoomOut()", null); true },
                            CustomAccessibilityAction(fit) { web?.evaluateJavascript("window.FolioreImageViewer?.reset()", null); true },
                        )
                    },
                )
            }
        }
    }
}
