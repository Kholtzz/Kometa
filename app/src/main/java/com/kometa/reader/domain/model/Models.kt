package com.kometa.reader.domain.model

import kotlinx.serialization.Serializable

enum class MangaStatus { ONGOING, COMPLETED, PAUSED, UNKNOWN }

@Serializable
data class GenreRef(val name: String, val url: String? = null)

data class Manga(
    val id: String,
    val sourceId: Long,
    val url: String,
    val title: String,
    val altTitle: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val genres: List<GenreRef> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
)

data class Chapter(
    val id: Long = 0,
    val mangaId: Long,
    val sourceId: Long,
    val url: String,
    val name: String,
    val number: Float,
    val uploadDate: Long = 0,
)

data class Page(val index: Int, val url: String, val chapterId: Long)
