package com.kometa.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import com.kometa.reader.data.local.ChapterEntity
import com.kometa.reader.data.repository.CatalogRepository
import com.kometa.reader.data.settings.SettingsRepository
import com.kometa.reader.domain.model.Page
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: CatalogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val chapterId: Long = checkNotNull(savedStateHandle["chapterId"])
    private val argPage: Int? = savedStateHandle["page"]

    var pages by mutableStateOf<List<Page>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var startPage by mutableStateOf(0); private set
    var bookmarked by mutableStateOf(false); private set
    private var chapter: ChapterEntity? = null

    init {
        viewModelScope.launch {
            runCatching {
                val ch = repo.chapter(chapterId) ?: error("Глава не найдена")
                chapter = ch
                pages = repo.pages(ch, settings.prefs.first().alwaysWebView)
                check(pages.isNotEmpty()) { "Страницы не найдены" }
                startPage = argPage ?: repo.savedPage(chapterId) ?: 0
            }.onFailure { error = it.message }
            loading = false
        }
    }

    fun onPageChanged(page: Int) {
        val ch = chapter ?: return
        viewModelScope.launch {
            repo.saveHistory(ch.mangaId, ch.id, page)
            if (page >= pages.lastIndex) repo.setChapterRead(ch.id, true)
        }
        viewModelScope.launch { bookmarked = repo.isBookmarked(chapterId, page) }
    }

    fun toggleBookmark(page: Int, pageUrl: String?) {
        val ch = chapter ?: return
        viewModelScope.launch { bookmarked = repo.toggleBookmark(ch, page, pageUrl) }
    }
}

@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    onOpenWebView: (Long) -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
) {
    when {
        vm.loading ->
            Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null ->
            Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(vm.error!!, color = Color.White, modifier = Modifier.padding(16.dp))
                    Button(onClick = { onOpenWebView(vm.chapterId) }) { Text("Открыть в WebView") }
                }
            }
        else -> ReaderScreen(
            pages = vm.pages,
            initialPage = vm.startPage,
            isBookmarked = vm.bookmarked,
            onPageChanged = vm::onPageChanged,
            onToggleBookmark = vm::toggleBookmark,
            onBack = onBack,
        )
    }
}

@Composable
fun ReaderScreen(
    pages: List<Page>,
    initialPage: Int,
    isBookmarked: Boolean,
    onPageChanged: (Int) -> Unit,
    onToggleBookmark: (Int, String?) -> Unit,
    onBack: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }

    LaunchedEffect(pagerState.currentPage) {
        zoomed = false
        onPageChanged(pagerState.currentPage)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { index ->
            ZoomablePage(
                pageUrl = pages[index].url,
                onZoom = { zoomed = it },
                onTap = { pos ->
                    val third = pagerState.pageSize / 3f
                    when {
                        pos.x < third -> scope.launch {
                            runCatching { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        pos.x > third * 2 -> scope.launch {
                            runCatching { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        else -> chromeVisible = !chromeVisible
                    }
                },
            )
        }

        if (chromeVisible) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("${pagerState.currentPage + 1} / ${pages.size}", color = Color.White)
                Box(Modifier.weight(1f))
                IconButton(
                    { onToggleBookmark(pagerState.currentPage, pages.getOrNull(pagerState.currentPage)?.url) }
                ) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        "Закладка", tint = Color.White,
                    )
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(12.dp)
            ) {
                Slider(
                    value = pagerState.currentPage.toFloat(),
                    onValueChange = { scope.launch { pagerState.scrollToPage(it.toInt()) } },
                    valueRange = 0f..(pages.size - 1).coerceAtLeast(0).toFloat(),
                )
            }
        }
    }
}

@Composable
private fun ZoomablePage(pageUrl: String, onZoom: (Boolean) -> Unit, onTap: (Offset) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformable = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
        onZoom(scale > 1.01f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .transformable(transformable)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        if (scale <= 1.01f) onTap(pos)
                        else { scale = 1f; offset = Offset.Zero; onZoom(false) }
                    },
                    onDoubleTap = {
                        if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        onZoom(scale > 1.01f)
                    },
                )
            },
    ) {
        SubcomposeAsyncImage(
            model = pageUrl,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
            loading = { CircularProgressIndicator(Modifier.align(Alignment.Center)) },
        )
    }
}
