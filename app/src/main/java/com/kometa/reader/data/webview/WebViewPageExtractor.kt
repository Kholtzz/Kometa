package com.kometa.reader.data.webview

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kometa.reader.domain.model.Page
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebViewPageExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extract(
        chapterUrl: String,
        customScript: String? = null,
        timeoutMs: Long = 25_000,
    ): List<Page> = withContext(Dispatchers.Main) {
        val urls = linkedSetOf<String>()
        var loaded = false

        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
            layout(0, 0, 1080, 1920)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) { loaded = true }
        }

        val pages = try {
            webView.loadUrl(chapterUrl)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!loaded && System.currentTimeMillis() < deadline) delay(100)
            check(loaded) { "Глава не загрузилась в WebView (таймаут)" }

            customScript?.let { webView.eval(it) }

            var stable = 0
            while (System.currentTimeMillis() < deadline && stable < 2) {
                val before = urls.size
                webView.eval("window.scrollTo(0, document.body.scrollHeight); void 0")
                delay(600)
                urls += parseUrls(webView.eval(COLLECT_JS)).filter { isPageImage(it) }
                stable = if (urls.size == before) stable + 1 else 0
            }
            check(urls.isNotEmpty()) { "WebView не обнаружил изображения страниц" }
            urls.mapIndexed { i, u -> Page(index = i, url = u, chapterId = 0) }
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
        pages
    }

    private suspend fun WebView.eval(js: String): String =
        suspendCancellableCoroutine { c -> evaluateJavascript(js) { c.resume(it) } }

    private val COLLECT_JS = """
        (function() {
            var out = [];
            document.querySelectorAll('img').forEach(function(img) {
                var u = img.currentSrc || img.src
                     || img.getAttribute('data-src')
                     || img.getAttribute('data-original')
                     || img.getAttribute('data-lazy-src')
                     || (img.getAttribute('data-srcset') || '').split(' ')[0]
                     || '';
                if (u && u.indexOf('http') === 0) out.push(u);
            });
            return out;
        })()
    """.trimIndent()

    private fun parseUrls(result: String): List<String> = runCatching {
        val s = if (result.length >= 2 && result.first() == '"' && result.last() == '"')
            result.substring(1, result.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        else result
        val arr = JSONArray(s)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    private fun isPageImage(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        if (lower.endsWith(".svg") || lower.endsWith(".ico") || lower.endsWith(".gif")) return false
        if (listOf("logo", "icon", "avatar", "banner", "favicon", "ads/", "emoji", "smile")
                .any { lower.contains(it) }) return false
        return true
    }
}
