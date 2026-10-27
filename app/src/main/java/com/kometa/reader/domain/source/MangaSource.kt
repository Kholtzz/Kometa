package com.kometa.reader.domain.source

import com.kometa.reader.domain.model.Chapter
import com.kometa.reader.domain.model.Manga
import com.kometa.reader.domain.model.Page

interface MangaSource {
    val id: Long
    val name: String
    val baseUrl: String
    val language: String
    val useWebViewPages: Boolean get() = false
    val webviewScript: String? get() = null

    suspend fun popular(page: Int): List<Manga>
    suspend fun search(query: String, page: Int): List<Manga>
    suspend fun details(mangaUrl: String): Manga
    suspend fun chapters(mangaUrl: String): List<Chapter>
    suspend fun pages(chapterUrl: String): List<Page>
}
