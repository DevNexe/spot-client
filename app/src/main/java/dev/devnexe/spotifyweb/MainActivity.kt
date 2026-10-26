package dev.devnexe.spotifyweb

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    companion object {
        const val START_URL = "https://open.spotify.com/"
        // Chrome на Windows — Spotify отдаёт полноценный веб-плеер, а не "скачайте приложение"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

        // Ширина вёрстки в CSS px по умолчанию. Меньше — элементы крупнее.
        const val DEFAULT_WIDTH = 480
    }

    private lateinit var web: WebView
    private val layoutWidth = DEFAULT_WIDTH
    private var mobileCss = ""
    private var mobileJs = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // chrome://inspect на ПК — чтобы смотреть DOM Spotify и править селекторы в assets/mobile.css
        WebView.setWebContentsDebuggingEnabled(true)

        mobileCss = assets.open("mobile.css").bufferedReader().use { it.readText() }
        mobileJs = assets.open("mobile.js").bufferedReader().use { it.readText() }


        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(false) // target=_blank / window.open грузятся в этом же WebView
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                applyViewport(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                applyViewport(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val uri = req.url
                return when (uri.scheme) {
                    "http", "https" -> false // все ссылки открываем у себя
                    "intent" -> {
                        runCatching {
                            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                .getStringExtra("browser_fallback_url")
                        }.getOrNull()?.let { view.loadUrl(it) }
                        true
                    }
                    else -> true // spotify:, market: и т.п.
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // DRM (Widevine) для воспроизведения
                val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                if (allowed.isNotEmpty()) request.grant(allowed.toTypedArray()) else request.deny()
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(intent?.data?.toString() ?: START_URL)
    }

    // Задаём ширину вёрстки и пытаемся снять минимальную ширину у страницы
    private fun applyViewport(view: WebView) {
        val cssJson = org.json.JSONObject.quote(mobileCss)
        view.evaluateJavascript(
            """
            (function () {
              try {
                var W = $layoutWidth;
                function apply() {
                  var host = document.head || document.documentElement;
                  if (!host) return;
                  var m = document.querySelector('meta[name=viewport]');
                  if (!m) { m = document.createElement('meta'); m.name = 'viewport'; host.appendChild(m); }
                  m.setAttribute('content', 'width=' + W + ', initial-scale=1, user-scalable=yes');
                  var st = document.getElementById('spw-style');
                  if (!st) { st = document.createElement('style'); st.id = 'spw-style'; host.appendChild(st); }
                  st.textContent = 'html,body,#main{min-width:0 !important}' + $cssJson;
                }
                apply();
                [300, 1500, 4000].forEach(function (t) { setTimeout(apply, t); });
              } catch (e) {}
            })();
            """.trimIndent(), null
        )
        view.evaluateJavascript(mobileJs, null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyViewport(web)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { web.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        super.onDestroy()
    }
}
