package dev.devnexe.spotifyweb

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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
        // Масштаб интерфейса: 1.0 = размеры экрана телефона как есть,
        // меньше (0.6–0.8) = вёрстка шире и мельче, больше = крупнее.
        const val LAYOUT_SCALE = 1.0f
        // Chrome на Windows — Spotify отдаёт полноценный веб-плеер, а не "скачайте приложение"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
    }

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = DESKTOP_UA
            useWideViewPort = true          // страница считает, что экран широкий
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
                    else -> true // spotify:, market: и т.п. — глушим, чтобы не выкидывало в другие приложения
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

    // Ширина вёрстки = ширина экрана, но если у сайта есть минимальная ширина больше —
    // замеряем её и расширяем вёрстку ровно до неё, чтобы ничего не вылезало за экран
    private fun applyViewport(view: WebView) {
        val dm = resources.displayMetrics
        val w = Math.round(dm.widthPixels / dm.density / LAYOUT_SCALE)
        view.evaluateJavascript(
            """
            (function () {
              try {
                var W = $w;
                var m = document.querySelector('meta[name=viewport]');
                if (!m) {
                  var host = document.head || document.documentElement;
                  if (!host) return;
                  m = document.createElement('meta');
                  m.name = 'viewport';
                  host.appendChild(m);
                }
                function set(x) {
                  m.setAttribute('content', 'width=' + x + ', initial-scale=1, user-scalable=yes');
                }
                function fit() {
                  var b = document.body, d = document.documentElement;
                  var sw = Math.max(d ? d.scrollWidth : 0, b ? b.scrollWidth : 0);
                  var cur = parseInt((m.getAttribute('content').match(/width=(\d+)/) || [])[1] || W, 10);
                  if (sw > cur) set(sw);
                }
                set(W);
                [300, 900, 2000, 4000].forEach(function (t) { setTimeout(fit, t); });
              } catch (e) {}
            })();
            """.trimIndent(), null
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyViewport(web) // поворот экрана — пересчитать размеры
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
