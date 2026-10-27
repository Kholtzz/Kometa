package com.kometa.reader.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kometa.reader.data.local.LibraryItem
import com.kometa.reader.data.repository.CatalogRepository
import com.kometa.reader.ui.common.MangaCard
import com.kometa.reader.ui.theme.LocalGridColumns
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(private val repo: CatalogRepository) : ViewModel() {
    private val catFilter = MutableStateFlow<Long?>(null)
    val filter = catFilter.asStateFlow()

    val library = combine(repo.observeLibrary(), catFilter) { items, f ->
        when (f) {
            null -> items
            -1L -> items.filter { it.manga.categoryId == null }
            else -> items.filter { it.manga.categoryId == f }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val continueReading = repo.observeContinue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: Long?) { catFilter.value = f }
    fun createCategory(name: String) = viewModelScope.launch { repo.createCategory(name) }
    fun assignCategory(mangaId: Long, categoryId: Long?) =
        viewModelScope.launch { repo.setCategory(mangaId, categoryId) }
    fun removeFromLibrary(id: Long) = viewModelScope.launch { repo.removeFromLibrary(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenManga: (Long, String) -> Unit,
    onOpenChapter: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val items by vm.library.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val continueItems by vm.continueReading.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    var assignTarget by remember { mutableStateOf<LibraryItem?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Библиотека") },
                actions = { IconButton(onOpenSettings) { Icon(Icons.Default.Settings, "Настройки") } },
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (continueItems.isNotEmpty()) {
                Text(
                    "Продолжить чтение",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(12.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(continueItems, key = { it.entry.chapterId }) { it ->
                        Card(onClick = { onOpenChapter(it.entry.chapterId) }) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(
                                        it.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("Гл. ${it.chapterNumber}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            LazyRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(filter == null, { vm.setFilter(null) }, { Text("Все") }) }
                item { FilterChip(filter == -1L, { vm.setFilter(-1L) }, { Text("Без категории") }) }
                items(categories, key = { it.id }) { c ->
                    FilterChip(filter == c.id, { vm.setFilter(c.id) }, { Text(c.name) })
                }
                item { FilterChip(false, { createOpen = true }, { Text("＋ Категория") }) }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(LocalGridColumns.current),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.manga.id }) { item ->
                    MangaCard(
                        cover = item.manga.coverUrl,
                        title = item.manga.title,
                        unread = item.unread,
                        isNew = item.manga.newChapters > 0,
                        onClick = { onOpenManga(item.manga.sourceId, item.manga.url) },
                        onLongClick = { assignTarget = item },
                    )
                }
            }
        }

        if (createOpen) {
            AlertDialog(
                onDismissRequest = { createOpen = false },
                title = { Text("Новая категория") },
                text = {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Название") })
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) vm.createCategory(newName.trim())
                            newName = ""; createOpen = false
                        }
                    ) { Text("Создать") }
                },
                dismissButton = { TextButton({ createOpen = false }) { Text("Отмена") } },
            )
        }

        assignTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { assignTarget = null },
                title = { Text(target.manga.title, maxLines = 1) },
                text = {
                    Column {
                        TextButton({
                            vm.assignCategory(target.manga.id, null); assignTarget = null
                        }) { Text("Без категории") }
                        categories.forEach { c ->
                            TextButton({
                                vm.assignCategory(target.manga.id, c.id); assignTarget = null
                            }) { Text(c.name) }
                        }
                        TextButton({
                            vm.removeFromLibrary(target.manga.id); assignTarget = null
                        }) { Text("Удалить из библиотеки", color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ assignTarget = null }) { Text("Отмена") } },
            )
        }
    }
}
