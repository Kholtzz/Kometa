package com.kometa.reader.ui.reader

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kometa.reader.data.local.ChapterEntity
import com.kometa.reader.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class WebViewReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: CatalogRepository,
) : ViewModel() {
    private val chapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    var chapter by mutableStateOf<ChapterEntity?>(null); private set
    var startPct by mutableStateOf(0); private set
    var bookmarked by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            chapter = repo.chapter(chapterId)
            startPct = repo.savedPage(chapterId) ?: 0
        }
    }

    fun onProgress(pct: Int) {
        val ch = chapter ?: return
        viewModelScope.launch {
            repo.saveHistory(ch.mangaId, ch.id, pct)
            if (pct >= 100) repo.setChapterRead(ch.id, true)
        }
    }

    fun toggleBookmark(pct: Int) {
        val ch = chapter ?: return
        viewModelScope.launch { bookmarked = repo.toggleBookmark(ch, pct, null) }
    }
}

@Composable
fun WebViewReaderRoute(onBack: () -> Unit, vm: WebViewReaderViewModel = hiltViewModel()) {
    val ch = vm.chapter
    if (ch == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) { CircularProgressIndicator() }
    } else {
        WebViewReaderScreen(
            url = ch.url,
            title = ch.name,
            initialPct = vm.startPct,
            isBookmarked = vm.bookmarked,
            onProgress = vm::onProgress,
            onToggleBookmark = vm::toggleBookmark,
            onBack = onBack,
        )
    }
}

@Composable
fun WebViewReaderScreen(
    url: String,
    title: String,
    initialPct: Int,
    isBookmarked: Boolean,
    onProgress: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var chromeVisible by remember { mutableStateOf(true) }
    var scrollPct by remember { mutableStateOf(0) }
    val pendingScroll = remember { mutableIntStateOf(initialPct) }

    val webView = remember(url) {
        object : WebView(context) {
            override fun onScrollChanged(l: Int, t: Int, ol: Int, ot: Int) {
                super.onScrollChanged(l, t, ol, ot)
                val max = computeVerticalScrollRange() - height
                if (max > 0) scrollPct = (t * 100f / max).roundToInt().coerceIn(0, 100)
            }
        }.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    val pct = pendingScroll.intValue
                    if (pct in 1..99) view.evaluateJavascript(
                        "(function(){var m=document.body.scrollHeight-innerHeight;window.scrollTo(0, m*$pct/100);})()",
                        null,
                    )
                    pendingScroll.intValue = 0
                    view.evaluateJavascript(CLEAN_READER_JS, null)
                }
            }
            loadUrl(url)
        }
    }

    LaunchedEffect(scrollPct) { delay(600); onProgress(scrollPct) }
    DisposableEffect(url) { onDispose { webView.destroy() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { chromeVisible = !chromeVisible }
            },
        )
        if (chromeVisible) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton({ onToggleBookmark(scrollPct) }) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        null, tint = Color.White,
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton({ webView.pageUp(true) }) {
                    Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White)
                }
                Text("$scrollPct %", color = Color.White, style = MaterialTheme.typography.labelLarge)
                IconButton({ webView.pageDown(true) }) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                }
            }
        }
    }
}

private val CLEAN_READER_JS = """
    (function() {
        document.querySelectorAll('header, footer, nav, ins, .adsbygoogle').forEach(function(e) {
            e.style.display = 'none';
        });
        document.querySelectorAll('div, section, aside').forEach(function(e) {
            var s = getComputedStyle(e);
            if ((s.position === 'fixed' || s.position === 'sticky')
                && e.getBoundingClientRect().height > 40) e.style.display = 'none';
        });
        document.querySelectorAll('img').forEach(function(i) {
            i.style.width = '100%'; i.style.height = 'auto'; i.style.margin = '0 auto';
        });
        if (document.body) { document.body.style.background = '#000'; document.body.style.margin = '0'; }
    })()
""".trimIndent()
