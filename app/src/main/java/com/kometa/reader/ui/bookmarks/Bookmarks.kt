package com.kometa.reader.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kometa.reader.data.repository.CatalogRepository
import com.kometa.reader.ui.common.EmptyHint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(private val repo: CatalogRepository) : ViewModel() {
    val items = repo.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { repo.deleteBookmark(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpen: (chapterId: Long, page: Int) -> Unit,
    vm: BookmarksViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Закладки") }) }) { pad ->
        if (items.isEmpty()) EmptyHint("В читалке жми 🔖 — страница сохранится здесь", pad)
        else LazyColumn(Modifier.padding(pad)) {
            items(items, key = { it.bookmark.id }) { item ->
                ListItem(
                    leadingContent = {
                        AsyncImage(
                            item.bookmark.pageUrl, null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(40.dp).height(56.dp),
                        )
                    },
                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${item.chapterName} · стр. ${item.bookmark.pageIndex + 1}")
                    },
                    trailingContent = {
                        IconButton({ vm.delete(item.bookmark.id) }) {
                            Icon(Icons.Default.DeleteOutline, "Удалить")
                        }
                    },
                    modifier = Modifier.clickable { onOpen(item.bookmark.chapterId, item.bookmark.pageIndex) },
                )
            }
        }
    }
}
