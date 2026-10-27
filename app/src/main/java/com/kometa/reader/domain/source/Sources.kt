package com.kometa.reader.domain.source

import com.kometa.reader.data.webview.WebViewPageExtractor
import com.kometa.reader.domain.model.Chapter
import com.kometa.reader.domain.model.GenreRef
import com.kometa.reader.domain.model.Manga
import com.kometa.reader.domain.model.MangaStatus
import com.kometa.reader.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import javax.inject.Inject

abstract class GroupleSource(
    override val id: Long,
    override val name: String,
    override val baseUrl: String,
) : MangaSource {

    override val language = "ru"

    private fun document(url: String): Document =
        Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .referrer(baseUrl)
            .get()

    override suspend fun popular(page: Int): List<Manga> =
        withContext(Dispatchers.IO) {
            parseTiles(document("$baseUrl/list?sortType=rate" + offset(page)))
        }

    override suspend fun search(query: String, page: Int): List<Manga> =
        withContext(Dispatchers.IO) {
            parseTiles(document("$baseUrl/list?name=${URLEncoder.encode(query, "UTF-8")}" + offset(page)))
        }

    private fun offset(page: Int) = if (page > 1) "&offset=${(page - 1) * 20}" else ""

    suspend fun catalogByGenre(genreUrl: String, page: Int): List<Manga> =
        withContext(Dispatchers.IO) {
            val suffix = if (page > 1) "/offset=${(page - 1) * 20}" else ""
            parseTiles(document(genreUrl.trimEnd('/') + suffix))
        }

    private fun parseTiles(doc: Document): List<Manga> =
        doc.select("div.tile").mapNotNull { el ->
            val url = el.selectFirst("a")?.absUrl("href") ?: return@mapNotNull null
            Manga(
                id = url.trimEnd('/').substringAfterLast('/'),
                sourceId = id,
                url = url,
                title = el.selectFirst("h3")?.text().orEmpty(),
                coverUrl = el.selectFirst("img")?.absUrl("src"),
            )
        }

    override suspend fun details(mangaUrl: String): Manga = withContext(Dispatchers.IO) {
        val doc = document(mangaUrl)
        Manga(
            id = mangaUrl.trimEnd('/').substringAfterLast('/'),
            sourceId = id,
            url = mangaUrl,
            title = doc.selectFirst("h1.names .name")?.text().orEmpty(),
            altTitle = doc.selectFirst("h1.names .eng-name")?.text(),
            coverUrl = doc.selectFirst(".picture-side img")?.absUrl("src"),
            description = doc.selectFirst(".manga-description")?.text(),
            genres = doc.select(".genre-list a").map { GenreRef(it.text(), it.absUrl("href")) },
            status = when {
                doc.selectFirst(".is-ongoing") != null -> MangaStatus.ONGOING
                doc.selectFirst(".is-completed") != null -> MangaStatus.COMPLETED
                else -> MangaStatus.UNKNOWN
            },
        )
    }

    override suspend fun chapters(mangaUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        document(mangaUrl).select("div.chapter-item").mapIndexed { i, el ->
            val a = el.selectFirst("a.chapter-link")!!
            Chapter(
                mangaId = 0,
                sourceId = id,
                url = a.absUrl("href"),
                name = a.text(),
                number = a.attr("href").trimEnd('/').substringAfterLast('/').toFloatOrNull() ?: i.toFloat(),
            )
        }.asReversed()
    }

    override suspend fun pages(chapterUrl: String): List<Page> = withContext(Dispatchers.IO) {
        document(chapterUrl).select("div.img-link img").mapIndexed { i, img ->
            Page(index = i, url = img.absUrl("src"), chapterId = 0)
        }
    }
}

class ReadMangaSource : GroupleSource(1, "ReadManga", "https://readmanga.live")
class MintMangaSource : GroupleSource(2, "MintManga", "https://mintmanga.live")
class SelfMangaSource : GroupleSource(3, "SelfManga", "https://selfmanga.live")

class MangaLoveSource @Inject constructor(
    private val extractor: WebViewPageExtractor,
) : MangaSource {
    override val id = 4L
    override val name = "MangaLove"
    override val baseUrl = "https://mangalove.ru"
    override val language = "ru"
    override val useWebViewPages = true

    private suspend fun doc(url: String): Document = withContext(Dispatchers.IO) {
        Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .referrer(baseUrl)
            .get()
    }

    // Селекторы предварительно — сверить с живой вёрсткой
    override suspend fun popular(page: Int): List<Manga> = withContext(Dispatchers.IO) {
        doc("$baseUrl/manga${if (page > 1) "?page=$page" else ""}")
            .select("div.card").mapNotNull { el ->
                val url = el.selectFirst("a")?.absUrl("href") ?: return@mapNotNull null
                Manga(
                    id = url.trimEnd('/').substringAfterLast('/'),
                    sourceId = id,
                    url = url,
                    title = el.selectFirst(".card-title")?.text().orEmpty(),
                    coverUrl = el.selectFirst("img")?.absUrl("src"),
                )
            }
    }

    override suspend fun search(query: String, page: Int): List<Manga> = popular(page)

    override suspend fun details(mangaUrl: String): Manga = withContext(Dispatchers.IO) {
        val d = doc(mangaUrl)
        Manga(
            id = mangaUrl.trimEnd('/').substringAfterLast('/'),
            sourceId = id,
            url = mangaUrl,
            title = d.selectFirst("h1")?.text().orEmpty(),
            coverUrl = d.selectFirst(".manga-cover img")?.absUrl("src"),
            description = d.selectFirst(".manga-description")?.text(),
        )
    }

    override suspend fun chapters(mangaUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        doc(mangaUrl).select("a.chapter-link").mapIndexed { i, a ->
            Chapter(
                mangaId = 0,
                sourceId = id,
                url = a.absUrl("href"),
                name = a.text(),
                number = a.attr("href").trimEnd('/').substringAfterLast('/').toFloatOrNull() ?: i.toFloat(),
            )
        }.asReversed()
    }

    override suspend fun pages(chapterUrl: String): List<Page> =
        extractor.extract(chapterUrl, customScript = webviewScript)
}
