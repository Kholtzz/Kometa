package com.kometa.reader.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaCard(
    cover: String?,
    title: String,
    unread: Int = 0,
    isNew: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box {
            AsyncImage(
                model = cover,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            if (unread > 0) {
                Badge(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Text("$unread") }
            }
            if (isNew) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
    }
}

@Composable
fun EmptyHint(text: String, pad: PaddingValues = PaddingValues(0.dp)) {
    Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}
