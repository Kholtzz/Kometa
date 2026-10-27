package com.kometa.reader.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kometa.reader.data.repository.CatalogRepository
import com.kometa.reader.data.settings.SettingsRepository
import com.kometa.reader.data.settings.ThemeMode
import com.kometa.reader.data.settings.UiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repo: CatalogRepository,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val prefs = settings.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, UiPrefs())
    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast

    fun setTheme(m: ThemeMode) = viewModelScope.launch { settings.setTheme(m) }
    fun setColumns(n: Int) = viewModelScope.launch { settings.setColumns(n.coerceIn(2, 6)) }
    fun setAlwaysWebView(b: Boolean) = viewModelScope.launch { settings.setAlwaysWebView(b) }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        runCatching {
            context.contentResolver.openOutputStream(uri)!!
                .use { it.write(repo.export().toByteArray()) }
        }.onSuccess { _toast.emit("Бэкап сохранён") }
            .onFailure { _toast.emit("Ошибка экспорта: ${it.message}") }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        runCatching {
            context.contentResolver.openInputStream(uri)!!
                .bufferedReader().readText().let { repo.import(it) }
        }.onSuccess { _toast.emit("Импортировано тайтлов: $it") }
            .onFailure { _toast.emit("Ошибка импорта: ${it.message}") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri?.let(vm::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri?.let(vm::importFrom) }

    LaunchedEffect(Unit) {
        vm.toast.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                ListItem(
                    headlineContent = { Text("Тема") },
                    supportingContent = {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = prefs.theme == mode,
                                    onClick = { vm.setTheme(mode) },
                                    label = { Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "Системная"
                                            ThemeMode.LIGHT -> "Светлая"
                                            ThemeMode.DARK -> "Тёмная"
                                        }) },
                                )
                            }
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Колонок в сетке") },
                    supportingContent = {
                        Slider(
                            value = prefs.gridColumns.toFloat(),
                            onValueChange = { vm.setColumns(it.roundToInt()) },
                            valueRange = 2f..6f,
                            steps = 3,
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Всегда читать через WebView") },
                    supportingContent = { Text("Медленнее, но надёжнее для сайтов с JS-рендерингом") },
                    trailingContent = { Switch(prefs.alwaysWebView, { vm.setAlwaysWebView(it) }) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Экспорт библиотеки") },
                    supportingContent = { Text("Сохранить всё в .json") },
                    modifier = Modifier.padding(0.dp).let { m ->
                        m.then(
                            Modifier.clickable { exportLauncher.launch("kometa_backup.json") }
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Импорт библиотеки") },
                    supportingContent = { Text("Восстановить из файла бэкапа") },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) },
                )
            }
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(this) { onClick() })
