package com.kometa.reader.ui.details

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kometa.reader.data.download.ChapterDownloadWorker
import com.kometa.reader.data.local.ChapterEntity
import com.kometa.reader.data.local.DownloadStatus
import com.kometa.reader.data.local.MangaEntity
import com.kometa.reader.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: CatalogRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val sourceId: Long = checkNotNull(savedStateHandle["sourceId"])
    private val mangaUrl: String = checkNotNull(savedStateHandle["mangaUrl"])

    var manga by mutableStateOf<MangaEntity?>(null); private set
    var chapters by mutableStateOf<List<ChapterEntity>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch {
            runCatching {
                manga = repo.ensureManga(sourceId, mangaUrl)
                manga?.let { repo.refreshChapters(it) }
            }.onFailure { error = it.message }
            loading = false
            manga?.let { m -> repo.observeChapters(m.id).collect { chapters = it } }
        }
    }

    fun toggleLibrary() {
        val m = manga ?: return
        viewModelScope.launch {
            repo.toggleLibrary(m)
            manga = repo.mangaById(m.url, m.sourceId)
        }
    }
    fun downloadChapter(id: Long) = ChapterDownloadWorker.enqueue(context, id)
    fun cancelDownload(id: Long) {
        ChapterDownloadWorker.cancel(context, id)
        viewModelScope.launch { repo.resetDownload(id) }
    }
    fun deleteDownload(ch: ChapterEntity) = viewModelScope.launch { repo.deleteDownload(ch) }
    fun downloadUnread() {
        viewModelScope.launch {
            chapters.filter { !it.read && it.downloadState == DownloadStatus.NONE.name }
                .forEach { ChapterDownloadWorker.enqueue(context, it.id) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit,
    vm: DetailsViewModel = hiltViewModel(),
) {
    val m = vm.manga
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(m?.title ?: "…") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
            )
        }
    ) { pad ->
        when {
            vm.loading -> Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            vm.error != null -> Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { Text(vm.error!!) }
            m != null -> LazyColumn(Modifier.padding(pad)) {
                item {
                    Row(Modifier.padding(12.dp)) {
                        AsyncImage(
                            m.coverUrl, null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp).height(170.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(m.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                statusLabel(m.status),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (m.genres.isNotEmpty()) {
                                Text(
                                    m.genres.take(3).joinToString { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FilledTonalButton(onClick = vm::toggleLibrary, Modifier.padding(top = 8.dp)) {
                                Text(if (m.inLibrary) "✓ В библиотеке" else "+ В библиотеку")
                            }
                            if (m.inLibrary) {
                                FilledTonalButton(onClick = vm::downloadUnread, Modifier.padding(top = 4.dp)) {
                                    Icon(Icons.Default.Download, null); Text("  Скачать непрочитанные")
                                }
                            }
                        }
                    }
                    m.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Text(
                        "Главы (${vm.chapters.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                items(vm.chapters, key = { it.id }) { ch ->
                    ChapterRow(
                        ch = ch,
                        onOpen = { onOpenChapter(ch.id) },
                        onDownload = { vm.downloadChapter(ch.id) },
                        onCancel = { vm.cancelDownload(ch.id) },
                        onDelete = { vm.deleteDownload(ch) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    ch: ChapterEntity,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            when (ch.downloadState) {
                DownloadStatus.RUNNING.name -> {
                    val frac = if (ch.pageCount > 0) ch.downloadedPages / ch.pageCount.toFloat() else 0f
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                }
                DownloadStatus.ERROR.name -> Text(
                    "Ошибка: ${ch.downloadError}",
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                else -> {}
            }
        },
        trailingContent = {
            when {
                ch.downloadState == DownloadStatus.RUNNING.name ->
                    IconButton(onCancel) { Icon(Icons.Default.Close, "Отменить") }
                ch.downloaded ->
                    IconButton(onDelete) { Icon(Icons.Default.DeleteOutline, "Удалить загрузку") }
                else ->
                    IconButton(onDownload) { Icon(Icons.Default.Download, "Скачать") }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

private fun statusLabel(s: String) = when (s) {
    "ONGOING" -> "Выходит"
    "COMPLETED" -> "Завершён"
    "PAUSED" -> "Заморожен"
    else -> "Статус неизвестен"
}
