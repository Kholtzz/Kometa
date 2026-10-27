package com.kometa.reader.data.repository

import com.kometa.reader.data.backup.BackupCategory
import com.kometa.reader.data.backup.BackupDto
import com.kometa.reader.data.backup.MangaBackup
import com.kometa.reader.data.download.ChapterFiles
import com.kometa.reader.data.local.BookmarkEntity
import com.kometa.reader.data.local.CategoryEntity
import com.kometa.reader.data.local.ChapterEntity
import com.kometa.reader.data.local.DownloadStatus
import com.kometa.reader.data.local.HistoryEntity
import com.kometa.reader.data.local.MangaEntity
import com.kometa.reader.data.webview.WebViewPageExtractor
import com.kometa.reader.domain.model.Page
import com.kometa.reader.domain.source.MangaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val db: com.kometa.reader.data.local.AppDatabase,
    private val sources: @JvmSuppressWildcards List<MangaSource>,
    private val files: ChapterFiles,
    private val extractor: WebViewPageExtractor,
) {
    private val mangaDao = db.mangaDao()
    private val chapterDao = db.chapterDao()
    private val historyDao = db.historyDao()
    private val categoryDao = db.categoryDao()
    private val bookmarkDao = db.bookmarkDao()

    val allSources: List<MangaSource> get() = sources
    fun sourceById(id: Long): MangaSource = sources.first { it.id == id }
    suspend fun popular(sourceId: Long, page: Int) = sourceById(sourceId).popular(page)
    suspend fun search(sourceId: Long, q: String, page: Int) = sourceById(sourceId).search(q, page)

    fun observeLibrary() = mangaDao.observeLibraryWithUnread()
    fun observeChapters(mangaId: Long) = chapterDao.observeByManga(mangaId)
    fun observeHistory() = historyDao.observeHistory()
    fun observeContinue() = historyDao.observeContinue()
    fun observeBookmarks() = bookmarkDao.observeAll()
    fun observeCategories() = categoryDao.observeAll()

    suspend fun mangaById(url: String, sourceId: Long) = mangaDao.find(sourceId, url)

    suspend fun ensureManga(sourceId: Long, mangaUrl: String): MangaEntity {
        mangaDao.find(sourceId, mangaUrl)?.let { return it }
        val d = sourceById(sourceId).details(mangaUrl)
        val id = mangaDao.upsert(
            MangaEntity(
                sourceId = sourceId, url = mangaUrl, title = d.title,
                coverUrl = d.coverUrl, description = d.description,
                status = d.status.name, genres = d.genres,
            )
        )
        return mangaDao.byId(id)!!
    }

    suspend fun refreshChapters(manga: MangaEntity, countNew: Boolean = false): Int {
        val fresh = sourceById(manga.sourceId).chapters(manga.url)
        val known = chapterDao.urlsForManga(manga.id).toSet()
        val added = fresh.count { it.url !in known }
        chapterDao.upsertAll(
            fresh.map {
                ChapterEntity(
                    mangaId = manga.id, sourceId = it.sourceId,
                    url = it.url, name = it.name, number = it.number,
                )
            }
        )
        mangaDao.upsert(
            manga.copy(
                totalChapters = fresh.size,
                newChapters = if (countNew) manga.newChapters + added else 0,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return added
    }

    suspend fun toggleLibrary(manga: MangaEntity) = mangaDao.setInLibrary(manga.id, !manga.inLibrary)
    suspend fun removeFromLibrary(id: Long) = mangaDao.setInLibrary(id, false)
    suspend fun setCategory(mangaId: Long, categoryId: Long?) = mangaDao.setCategory(mangaId, categoryId)
    suspend fun createCategory(name: String) =
        categoryDao.upsert(CategoryEntity(name = name, order = categoryDao.nextOrder()))
    suspend fun deleteCategory(id: Long) { categoryDao.clearFrom(id); categoryDao.delete(id) }

    suspend fun chapter(id: Long) = chapterDao.byId(id)
    suspend fun setChapterRead(id: Long, read: Boolean) = chapterDao.setRead(id, read)
    suspend fun saveHistory(mangaId: Long, chapterId: Long, page: Int) =
        historyDao.upsert(HistoryEntity(mangaId, chapterId, page))
    suspend fun savedPage(chapterId: Long) = historyDao.pageIndex(chapterId)

    suspend fun toggleBookmark(ch: ChapterEntity, page: Int, pageUrl: String?): Boolean {
        val existing = bookmarkDao.find(ch.id, page)
        return if (existing != null) { bookmarkDao.delete(existing.id); false }
        else {
            bookmarkDao.upsert(
                BookmarkEntity(mangaId = ch.mangaId, chapterId = ch.id, pageIndex = page, pageUrl = pageUrl)
            )
            true
        }
    }
    suspend fun isBookmarked(chapterId: Long, page: Int) = bookmarkDao.find(chapterId, page) != null
    suspend fun deleteBookmark(id: Long) = bookmarkDao.delete(id)

    suspend fun resetDownload(id: Long) = chapterDao.setResult(id, false, DownloadStatus.NONE.name, null)
    suspend fun deleteDownload(ch: ChapterEntity) {
        files.deleteChapter(ch.mangaId, ch.id)
        chapterDao.setResult(ch.id, false, DownloadStatus.NONE.name, null)
    }
    fun offlinePages(mangaId: Long, chapterId: Long) = files.offlinePages(mangaId, chapterId)

    suspend fun pages(ch: ChapterEntity, alwaysWebView: Boolean): List<Page> {
        val source = sourceById(ch.sourceId)
        return when {
            ch.downloaded && ch.pageCount > 0 -> files.offlinePages(ch.mangaId, ch.id)
            alwaysWebView -> extractor.extract(ch.url, source.webviewScript)
            else -> source.pages(ch.url)
        }
    }

    suspend fun checkNewChapters(): Int {
        var total = 0
        mangaDao.libraryIds().forEach { id ->
            mangaDao.byId(id)?.let {
                runCatching { refreshChapters(it, countNew = true) }.onSuccess { total += it }
            }
        }
        return total
    }

    suspend fun export(): String {
        val json = Json { prettyPrint = true }
        return json.encodeToString(
            BackupDto(
                exportedAt = System.currentTimeMillis(),
                categories = categoryDao.all().map { BackupCategory(it.id, it.name, it.order) },
                manga = mangaDao.libraryManga().map { m ->
                    MangaBackup(
                        m.sourceId, m.url, m.title, m.favorite, m.categoryId,
                        m.genres, chapterDao.readUrlsFor(m.id),
                    )
                },
            )
        )
    }

    suspend fun import(json: String): Int {
        val dto = Json.decodeFromString<BackupDto>(json)
        val catMap = dto.categories.associate { it.id to categoryDao.upsert(CategoryEntity(name = it.name, order = it.order)) }
        var restored = 0
        dto.manga.forEach { mb ->
            val existing = mangaDao.find(mb.sourceId, mb.url)
            val id = existing?.id ?: mangaDao.upsert(
                MangaEntity(
                    sourceId = mb.sourceId, url = mb.url, title = mb.title,
                    genres = mb.genres, inLibrary = true, favorite = mb.favorite,
                    categoryId = mb.categoryId?.let { catMap[it] },
                )
            )
            mangaDao.upsert(
                mangaDao.byId(id)!!.copy(
                    inLibrary = true, favorite = mb.favorite,
                    categoryId = mb.categoryId?.let { catMap[it] },
                )
            )
            if (existing == null) restored++
            val known = chapterDao.urlsForManga(id).toSet()
            mb.readChapterUrls.filter { it in known }
                .forEach { chapterDao.setReadByUrl(id, it, true) }
        }
        return restored
    }
}
