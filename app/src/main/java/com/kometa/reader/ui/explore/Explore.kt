package com.kometa.reader.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kometa.reader.data.repository.CatalogRepository
import com.kometa.reader.data.repository.Recommendation
import com.kometa.reader.data.repository.RecommendationService
import com.kometa.reader.domain.model.Manga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repo: CatalogRepository,
    recService: RecommendationService,
) : ViewModel() {
    val sources = repo.allSources

    var sourceId by mutableStateOf(sources.first().id); private set
    var query by mutableStateOf(""); private set
    var items by mutableStateOf<List<Manga>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var canLoadMore by mutableStateOf(true); private set
    var recommendations by mutableStateOf<List<Recommendation>>(emptyList()); private set

    private var page = 1
    private var job: Job? = null

    init {
        viewModelScope.launch {
            recommendations = runCatching { recService.refresh() }.getOrDefault(emptyList())
        }
    }

    fun selectSource(id: Long) { sourceId = id; items = emptyList(); canLoadMore = true; load(true) }
    fun loadNext() { if (!loading) load(false) }

    fun onQuery(q: String) {
        query = q
        job?.cancel()
        job = viewModelScope.launch { delay(400); load(true) }
    }

    private fun load(reset: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            page = if (reset) 1 else page + 1
            runCatching {
                if (query.isBlank()) repo.popular(sourceId, page) else repo.search(sourceId, query, page)
            }.onSuccess {
                items = if (reset) it else items + it
                canLoadMore = it.isNotEmpty()
            }.onFailure {
                if (reset) items = emptyList()
                canLoadMore = false
            }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onOpenManga: (Long, String) -> Unit, vm: ExploreViewModel = hiltViewModel()) {
    Scaffold(topBar = { TopAppBar(title = { Text("Обзор") }) }) { pad ->
        Column(Modifier.padding(pad)) {
            SourcePicker(vm)
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQuery,
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (vm.items.isEmpty() && vm.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (vm.query.isBlank() && vm.recommendations.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Column {
                            Text(
                                "Подборка для тебя",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                            vm.recommendations.forEach { rec ->
                                Text(
                                    rec.genre,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(rec.manga, key = { it.url }) { m ->
                                        Card(onClick = { onOpenManga(m.sourceId, m.url) }) {
                                            Column(Modifier.width(110.dp)) {
                                                AsyncImage(
                                                    m.coverUrl, null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxWidth().height(160.dp),
                                                )
                                                Text(
                                                    m.title,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(vm.items, key = { it.url }) { m ->
                    Card(onClick = { onOpenManga(m.sourceId, m.url) }) {
                        Column {
                            AsyncImage(
                                m.coverUrl, m.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                            )
                            Text(
                                m.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                    }
                }
                if (vm.loading && vm.items.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= vm.items.size - 6 && !vm.loading && vm.canLoadMore && vm.items.isNotEmpty()
            }
        }
        LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadNext() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePicker(vm: ExploreViewModel) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = vm.sources.first { it.id == vm.sourceId }.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Источник") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        ExposedDropdownMenu(open, { open = false }) {
            vm.sources.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name) },
                    onClick = { vm.selectSource(s.id); open = false },
                )
            }
        }
    }
}
