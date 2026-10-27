package com.kometa.reader.data.backup

import com.kometa.reader.domain.model.GenreRef
import kotlinx.serialization.Serializable

@Serializable
data class BackupCategory(val id: Long, val name: String, val order: Int)

@Serializable
data class MangaBackup(
    val sourceId: Long,
    val url: String,
    val title: String,
    val favorite: Boolean = false,
    val categoryId: Long? = null,
    val genres: List<GenreRef> = emptyList(),
    val readChapterUrls: List<String> = emptyList(),
)

@Serializable
data class BackupDto(
    val version: Int = 1,
    val exportedAt: Long,
    val categories: List<BackupCategory> = emptyList(),
    val manga: List<MangaBackup> = emptyList(),
)
