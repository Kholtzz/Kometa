package com.kometa.reader.di

import android.content.Context
import androidx.room.Room
import com.kometa.reader.data.local.AppDatabase
import com.kometa.reader.data.local.MIGRATION_1_2
import com.kometa.reader.data.local.MIGRATION_2_3
import com.kometa.reader.data.local.MIGRATION_3_4
import com.kometa.reader.data.webview.WebViewPageExtractor
import com.kometa.reader.domain.source.MangaLoveSource
import com.kometa.reader.domain.source.MangaSource
import com.kometa.reader.domain.source.MintMangaSource
import com.kometa.reader.domain.source.ReadMangaSource
import com.kometa.reader.domain.source.SelfMangaSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "kometa.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun sources(extractor: WebViewPageExtractor): List<MangaSource> = listOf(
        ReadMangaSource(),
        MintMangaSource(),
        SelfMangaSource(),
        MangaLoveSource(extractor),
    )
}
