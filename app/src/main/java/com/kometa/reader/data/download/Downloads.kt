package com.kometa.reader.data.download

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kometa.reader.data.local.AppDatabase
import com.kometa.reader.data.local.DownloadStatus
import com.kometa.reader.domain.model.Page
import com.kometa.reader.domain.source.MangaSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChapterFiles @Inject constructor(@ApplicationContext context: Context) {
    private val root = File(context.filesDir, "downloads").apply { mkdirs() }

    fun chapterDir(mangaId: Long, chapterId: Long) =
        File(File(root, mangaId.toString()), chapterId.toString())

    fun pageFile(mangaId: Long, chapterId: Long, index: Int) =
        File(chapterDir(mangaId, chapterId), "p%04d".format(index))

    fun offlinePages(mangaId: Long, chapterId: Long): List<Page> =
        chapterDir(mangaId, chapterId).listFiles()
            ?.sortedBy { it.name }
            ?.mapIndexed { i, f -> Page(index = i, url = Uri.fromFile(f).toString(), chapterId = chapterId) }
            .orEmpty()

    fun deleteChapter(mangaId: Long, chapterId: Long) {
        chapterDir(mangaId, chapterId).deleteRecursively()
    }
}

@Singleton
class ChapterDownloader @Inject constructor(
    private val db: AppDatabase,
    private val files: ChapterFiles,
    private val sources: @JvmSuppressWildcards List<MangaSource>,
) {
    private val http = OkHttpClient.Builder()
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        .build()

    suspend fun download(chapterId: Long) {
        val dao = db.chapterDao()
        val chapter = dao.byId(chapterId) ?: return
        if (chapter.downloaded) return
        dao.setResult(chapterId, false, DownloadStatus.RUNNING.name, null)
        try {
            withContext(Dispatchers.IO) {
                val source = sources.first { it.id == chapter.sourceId }
                val pages = source.pages(chapter.url)
                check(pages.isNotEmpty()) { "Источник не вернул страницы" }
                dao.setPageCount(chapterId, pages.size)
                var done = 0
                pages.forEach { page ->
                    val file = files.pageFile(chapter.mangaId, chapterId, page.index)
                    if (!file.exists() || file.length() == 0L) {
                        http.newCall(Request.Builder().url(page.url).build()).execute().use { resp ->
                            check(resp.isSuccessful) { "HTTP ${resp.code}: ${page.url}" }
                            file.parentFile?.mkdirs()
                            file.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
                        }
                    }
                    dao.setProgress(chapterId, ++done)
                }
            }
            dao.setResult(chapterId, true, DownloadStatus.DONE.name, null)
        } catch (e: CancellationException) {
            dao.setResult(chapterId, false, DownloadStatus.NONE.name, null)
            throw e
        } catch (e: Exception) {
            dao.setResult(chapterId, false, DownloadStatus.ERROR.name, e.message ?: "Ошибка сети")
        }
    }
}

@HiltWorker
class ChapterDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloader: ChapterDownloader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY, -1L)
        if (id > 0) downloader.download(id)
        return Result.success()
    }

    companion object {
        private const val KEY = "chapterId"
        private fun workName(id: Long) = "dl_chapter_$id"

        fun enqueue(context: Context, chapterId: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(chapterId),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                    .setInputData(workDataOf(KEY to chapterId))
                    .setConstraints(Constraints(NetworkType.CONNECTED))
                    .build(),
            )
        }

        fun cancel(context: Context, chapterId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(chapterId))
        }
    }
}
