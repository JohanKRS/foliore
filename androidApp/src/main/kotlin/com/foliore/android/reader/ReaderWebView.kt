package com.foliore.android.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Only the app's small runtime can call this bridge; all publication scripts are removed. */
internal class ReaderWebController(
    private val view: WebView,
    private val publication: EpubPublication,
    private val onPosition: (ReadingLocator, Int, Int, Boolean) -> Unit,
    private val onChapter: (Int) -> Unit,
    private val onToggleChrome: () -> Unit,
    private val onLink: (String) -> Unit,
    private val onError: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var navigation: ReaderNavigation? = null
    private var settings = ReaderPreferences()
    private var loaded = false
    private var disposed = false
    private var highlight = ""
    private val json = Json { encodeDefaults = true }
    private val runtime = view.context.assets.open("reader/runtime.js").use { it.readBytes() }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun initialize() {
        with(view.settings) {
            javaScriptEnabled = true
            allowFileAccess = false; allowContentAccess = false
            @Suppress("DEPRECATION")
            setAllowFileAccessFromFileURLs(false)
            @Suppress("DEPRECATION")
            setAllowUniversalAccessFromFileURLs(false)
            domStorageEnabled = false; databaseEnabled = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            builtInZoomControls = true; displayZoomControls = false
            textZoom = 100
        }
        view.isHorizontalScrollBarEnabled = false
        view.isVerticalScrollBarEnabled = false
        view.setBackgroundColor(android.graphics.Color.parseColor("#f7f2e8"))
        view.addJavascriptInterface(object {
            @JavascriptInterface fun message(value: String) {
                if (value.length > 8192) return
                handler.post {
                    if (disposed || !loaded) return@post
                    runCatching {
                        val data = JSONObject(value)
                        when (data.optString("event")) {
                            "position" -> navigation?.let { nav ->
                                onPosition(ReadingLocator(nav.locator.href, data.optDouble("progression", 0.0).coerceIn(0.0, 1.0),
                                    data.optInt("textOffset").coerceAtLeast(0), data.optString("quote").take(200)), data.optInt("page", 1), data.optInt("pages", 1), data.optBoolean("user"))
                            }
                            "chapter" -> onChapter(data.optInt("direction").coerceIn(-1, 1))
                            "chrome" -> onToggleChrome()
                            "link" -> onLink(data.optString("href").take(4096))
                        }
                    }
                }
            }
        }, "FolioreBridge")
        if (com.foliore.android.BuildConfig.DEBUG) view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean { android.util.Log.d("FolioreReader", "${message.lineNumber()}: ${message.message()}"); return true }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                if (request.method != "GET") return blocked()
                val uri = request.url
                if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net" || uri.port != -1) return blocked()
                if (uri.path == "/_reader/runtime.js") return response("application/javascript", runtime)
                val path = uri.path?.removePrefix("/publication/")?.takeIf { uri.path?.startsWith("/publication/") == true } ?: return blocked()
                return runCatching { publication.resource(path)?.let { response(it.mime, it.bytes) } ?: blocked() }.getOrElse { blocked() }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean { onLink(request.url.toString()); return true }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { loaded = false }
            override fun onPageFinished(view: WebView, url: String) {
                if (disposed || url.substringBefore('#') != navigation?.locator?.href?.let(EpubPublication::url)) return
                loaded = true; configure()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) onError() }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { loaded = false; onError(); return true }
        }
    }

    fun navigate(value: ReaderNavigation, preferences: ReaderPreferences, search: String = "") {
        val changed = navigation?.token != value.token
        val sameDocument = navigation?.locator?.href == value.locator.href
        navigation = value; settings = preferences; highlight = search
        view.setBackgroundColor(android.graphics.Color.parseColor(when (preferences.theme) { "dark" -> "#191c1b"; "light" -> "#ffffff"; else -> "#f7f2e8" }))
        val fixed = publication.sections[value.section].fixedLayout
        view.settings.useWideViewPort = fixed
        view.settings.loadWithOverviewMode = fixed
        if (!changed) return
        if (sameDocument && loaded) configure() else { loaded = false; view.loadUrl(EpubPublication.url(value.locator.href)) }
    }

    fun updateSettings(value: ReaderPreferences, current: ReadingLocator?) {
        if (settings == value) return
        settings = value
        navigation = navigation?.copy(locator = current ?: navigation!!.locator, fragment = null, user = false)
        if (loaded) configure()
    }
    private fun configure() {
        val nav = navigation ?: return
        val prefs = JSONObject(json.encodeToString(settings)).put("rtl", publication.rtl).toString()
        evaluate("FolioreReader.configure($prefs,${json.encodeToString(nav.locator)},${JSONObject.quote(nav.fragment.orEmpty())},${nav.user});FolioreReader.highlight(${JSONObject.quote(highlight)});")
        navigation = nav.copy(fragment = null, user = false)
    }
    fun step(direction: Int) { evaluate("FolioreReader.step($direction)") }
    fun clearHighlight() { highlight = ""; evaluate("FolioreReader.highlight('')") }
    fun seek(value: Float) { evaluate("FolioreReader.seek(${value.coerceIn(0f, 1f)})") }
    fun flush(onDone: () -> Unit = {}) {
        if (loaded && !disposed) {
            var completed = false
            val finish = Runnable { if (!completed) { completed = true; onDone() } }
            handler.postDelayed(finish, 350)
            view.evaluateJavascript("window.FolioreReader && FolioreReader.flush()") { handler.removeCallbacks(finish); finish.run() }
        } else onDone()
    }
    private fun evaluate(script: String) { if (loaded && !disposed) view.evaluateJavascript("window.FolioreReader && (()=>{$script})()", null) }
    fun dispose() { disposed = true; view.stopLoading(); view.removeJavascriptInterface("FolioreBridge"); view.webViewClient = WebViewClient(); view.destroy() }
    private fun response(mime: String, bytes: ByteArray) = WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"), ByteArrayInputStream(bytes))
    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
