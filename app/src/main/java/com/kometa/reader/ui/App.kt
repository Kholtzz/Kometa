package com.kometa.reader.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CollectionsBookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.kometa.reader.ui.bookmarks.BookmarksScreen
import com.kometa.reader.ui.details.DetailsScreen
import com.kometa.reader.ui.explore.ExploreScreen
import com.kometa.reader.ui.history.HistoryScreen
import com.kometa.reader.ui.library.LibraryScreen
import com.kometa.reader.ui.reader.ReaderRoute
import com.kometa.reader.ui.reader.WebViewReaderRoute
import com.kometa.reader.ui.settings.SettingsScreen
import com.kometa.reader.ui.theme.LocalGridColumns

@Composable
fun KometaRoot() {
    val settingsVm: com.kometa.reader.ui.settings.SettingsViewModel = hiltViewModel()
    val prefs by settingsVm.prefs.collectAsStateWithLifecycle()

    androidx.compose.material3.MaterialTheme(
        colorScheme = if (prefs.theme == com.kometa.reader.data.settings.ThemeMode.DARK ||
            (prefs.theme == com.kometa.reader.data.settings.ThemeMode.SYSTEM &&
                androidx.compose.foundation.isSystemInDarkTheme())
        ) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    ) {
        CompositionLocalProvider(LocalGridColumns provides prefs.gridColumns) {
            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val route = backStack?.destination?.route

            Scaffold(
                bottomBar = {
                    if (route in listOf("library", "explore", "history", "bookmarks")) {
                        NavigationBar {
                            NavigationBarItem(route == "library", { nav.go("library") },
                                { Icon(Icons.Default.CollectionsBookmarks, null) }, label = { Text("Библиотека") })
                            NavigationBarItem(route == "explore", { nav.go("explore") },
                                { Icon(Icons.Default.Search, null) }, label = { Text("Обзор") })
                            NavigationBarItem(route == "history", { nav.go("history") },
                                { Icon(Icons.Default.History, null) }, label = { Text("История") })
                            NavigationBarItem(route == "bookmarks", { nav.go("bookmarks") },
                                { Icon(Icons.Default.BookmarkBorder, null) }, label = { Text("Закладки") })
                        }
                    }
                }
            ) { pad ->
                NavHost(nav, "library", Modifier.padding(pad)) {
                    composable("library") {
                        LibraryScreen(
                            onOpenManga = { s, u -> nav.navigate("details/$s/${Uri.encode(u)}") },
                            onOpenChapter = { nav.navigate("reader/$it") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("explore") {
                        ExploreScreen { s, u -> nav.navigate("details/$s/${Uri.encode(u)}") }
                    }
                    composable("history") { HistoryScreen { nav.navigate("reader/$it") } }
                    composable("bookmarks") { BookmarksScreen { ch, p -> nav.navigate("reader/$ch?page=$p") } }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("details/{sourceId}/{mangaUrl}") {
                        DetailsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenChapter = { nav.navigate("reader/$it") },
                        )
                    }
                    composable("reader/{chapterId}?page={page}") {
                        ReaderRoute(
                            onBack = { nav.popBackStack() },
                            onOpenWebView = { nav.navigate("webreader/$it") },
                        )
                    }
                    composable("webreader/{chapterId}") {
                        WebViewReaderRoute(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

private fun NavHostController.go(route: String) {
    navigate(route) {
        popUpTo("library") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
