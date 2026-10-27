package com.kometa.reader.data.repository

import com.kometa.reader.data.local.AppDatabase
import com.kometa.reader.domain.source.GroupleSource
import com.kometa.reader.domain.source.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

data class Recommendation(val genre: String, val manga: List<com.kometa.reader.domain.model.Manga>)

@Singleton
class RecommendationService @Inject constructor(
    private val db: AppDatabase,
    private val sources: @JvmSuppressWildcards List<MangaSource>,
) {
    suspend fun refresh(perGenre: Int = 8): List<Recommendation> {
        val library = db.mangaDao().libraryManga()
        if (library.isEmpty()) return emptyList()
        val grouple = sources.filterIsInstance<GroupleSource>()
        if (grouple.isEmpty()) return emptyList()

        val topUrls = library.flatMap { it.genres }
            .filter { it.url != null }
            .groupingBy { it.url!! }
            .eachCount()
            .entries.sortedByDescending { it.value }.take(3).map { it.key }

        val result = mutableListOf<Recommendation>()
        for (url in topUrls) {
            val ref = library.flatMap { it.genres }.first { it.url == url }
            val source = grouple.firstOrNull { s -> library.any { it.sourceId == s.id } } ?: continue
            runCatching {
                Recommendation(
                    genre = ref.name,
                    manga = source.catalogByGenre(url, 1)
                        .filter { c -> library.none { it.url == c.url } }
                        .take(perGenre),
                )
            }.getOrNull()?.let { if (it.manga.isNotEmpty()) result.add(it) }
        }
        return result
    }
}
