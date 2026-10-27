package com.kometa.reader.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(repo: CatalogRepository) : ViewModel() {
    val items = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("История") }) }) { pad ->
        if (items.isEmpty()) EmptyHint("Пока пусто — почитай что-нибудь!", pad)
        else LazyColumn(Modifier.padding(pad)) {
            items(items, key = { "${it.entry.chapterId}_${it.entry.readAt}" }) { item ->
                ListItem(
                    leadingContent = {
                        AsyncImage(
                            item.coverUrl, null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(item.chapterName.ifBlank { "Глава ${item.chapterNumber}" })
                    },
                    trailingContent = {
                        Text(
                            DateUtils.getRelativeTimeSpanString(item.entry.readAt).toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.clickable { onOpen(item.entry.chapterId) },
                )
            }
        }
    }
}
