package com.kometa.reader.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kometa.reader.domain.model.GenreRef
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

enum class DownloadStatus { NONE, RUNNING, DONE, ERROR }

@Entity(tableName = "manga", indices = [Index(value = ["sourceId", "url"], unique = true)])
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val status: String,
    val genres: List<GenreRef> = emptyList(),
    val inLibrary: Boolean = false,
    val favorite: Boolean = false,
    val categoryId: Long? = null,
    val totalChapters: Int = 0,
    val newChapters: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chapters", indices = [Index("mangaId"), Index(value = ["mangaId", "url"], unique = true)])
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val sourceId: Long,
    val url: String,
    val name: String,
    val number: Float,
    val read: Boolean = false,
    val pageCount: Int = 0,
    val downloadedPages: Int = 0,
    val downloaded: Boolean = false,
    val downloadState: String = DownloadStatus.NONE.name,
    val downloadError: String? = null,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    val readAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val order: Int = 0,
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["chapterId", "pageIndex"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    val pageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    @TypeConverter fun genresToJson(g: List<GenreRef>): String = json.encodeToString(g)
    @TypeConverter fun jsonToGenres(s: String): List<GenreRef> =
        runCatching { json.decodeFromString<List<GenreRef>>(s) }.getOrDefault(emptyList())
}

data class LibraryItem(@Embedded val manga: MangaEntity, val unread: Int)
data class HistoryItem(
    @Embedded val entry: HistoryEntity,
    val title: String,
    val coverUrl: String?,
    val chapterName: String,
    val chapterNumber: Float,
)
data class BookmarkItem(
    @Embedded val bookmark: BookmarkEntity,
    val title: String,
    val coverUrl: String?,
    val chapterName: String,
)

@Dao
interface MangaDao {
    @Query("SELECT m.*, (SELECT COUNT(*) FROM chapters c WHERE c.mangaId = m.id AND c.read = 0) AS unread FROM manga m WHERE m.inLibrary = 1 ORDER BY m.updatedAt DESC")
    fun observeLibraryWithUnread(): Flow<List<LibraryItem>>
    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun byId(id: Long): MangaEntity?
    @Query("SELECT * FROM manga WHERE sourceId = :s AND url = :url LIMIT 1")
    suspend fun find(s: Long, url: String): MangaEntity?
    @Query("SELECT * FROM manga WHERE inLibrary = 1")
    suspend fun libraryManga(): List<MangaEntity>
    @Query("SELECT id FROM manga WHERE inLibrary = 1")
    suspend fun libraryIds(): List<Long>
    @Upsert
    suspend fun upsert(manga: MangaEntity): Long
    @Query("UPDATE manga SET inLibrary = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(id: Long, inLibrary: Boolean)
    @Query("UPDATE manga SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY number ASC")
    fun observeByManga(mangaId: Long): Flow<List<ChapterEntity>>
    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun byId(id: Long): ChapterEntity?
    @Query("SELECT url FROM chapters WHERE mangaId = :mangaId")
    suspend fun urlsForManga(mangaId: Long): List<String>
    @Query("SELECT url FROM chapters WHERE mangaId = :id AND read = 1")
    suspend fun readUrlsFor(id: Long): List<String>
    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)
    @Query("UPDATE chapters SET read = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)
    @Query("UPDATE chapters SET read = :read WHERE mangaId = :mangaId AND url = :url")
    suspend fun setReadByUrl(mangaId: Long, url: String, read: Boolean)
    @Query("UPDATE chapters SET pageCount = :count WHERE id = :id")
    suspend fun setPageCount(id: Long, count: Int)
    @Query("UPDATE chapters SET downloadedPages = :pages WHERE id = :id")
    suspend fun setProgress(id: Long, pages: Int)
    @Query("UPDATE chapters SET downloaded = :done, downloadState = :state, downloadError = :error WHERE id = :id")
    suspend fun setResult(id: Long, done: Boolean, state: String, error: String?)
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(entry: HistoryEntity)
    @Query("SELECT pageIndex FROM history WHERE chapterId = :chapterId LIMIT 1")
    suspend fun pageIndex(chapterId: Long): Int?
    @Query("SELECT h.*, m.title AS title, m.coverUrl AS coverUrl, c.name AS chapterName, c.number AS chapterNumber FROM history h JOIN manga m ON m.id = h.mangaId JOIN chapters c ON c.id = h.chapterId ORDER BY h.readAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<HistoryItem>>
    @Query("SELECT h.*, m.title AS title, m.coverUrl AS coverUrl, c.name AS chapterName, c.number AS chapterNumber FROM history h JOIN manga m ON m.id = h.mangaId JOIN chapters c ON c.id = h.chapterId WHERE h.readAt = (SELECT MAX(h2.readAt) FROM history h2 WHERE h2.mangaId = h.mangaId) ORDER BY h.readAt DESC LIMIT 3")
    fun observeContinue(): Flow<List<HistoryItem>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY `order`, id")
    fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>
    @Upsert
    suspend fun upsert(c: CategoryEntity): Long
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT COALESCE(MAX(`order`), 0) + 1 FROM categories")
    suspend fun nextOrder(): Int
    @Query("UPDATE manga SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearFrom(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT b.*, m.title AS title, m.coverUrl AS coverUrl, c.name AS chapterName FROM bookmarks b JOIN manga m ON m.id = b.mangaId JOIN chapters c ON c.id = b.chapterId ORDER BY b.createdAt DESC")
    fun observeAll(): Flow<List<BookmarkItem>>
    @Query("SELECT * FROM bookmarks WHERE chapterId = :chapterId AND pageIndex = :page LIMIT 1")
    suspend fun find(chapterId: Long, page: Int): BookmarkEntity?
    @Upsert
    suspend fun upsert(b: BookmarkEntity): Long
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [MangaEntity::class, ChapterEntity::class, HistoryEntity::class,
                CategoryEntity::class, BookmarkEntity::class],
    version = 4,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun historyDao(): HistoryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun bookmarkDao(): BookmarkDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN downloadedPages INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN downloadState TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE chapters ADD COLUMN downloadError TEXT")
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN categoryId INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, `order` INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mangaId INTEGER NOT NULL, chapterId INTEGER NOT NULL, pageIndex INTEGER NOT NULL, pageUrl TEXT, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_chapterId_pageIndex ON bookmarks(chapterId, pageIndex)")
    }
}
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN genres TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE manga ADD COLUMN newChapters INTEGER NOT NULL DEFAULT 0")
    }
}
