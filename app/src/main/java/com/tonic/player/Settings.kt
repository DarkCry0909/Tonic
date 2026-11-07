package com.tonic.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.imageLoader

enum class SettingsTab(val title: String) {
    Look("Внешний вид"), Player("Плеер"), Cache("Кэш"), Db("База данных"),
    Sync("Синхронизировать"), Other("Прочее"), About("О приложении")
}

@Composable
fun SettingsSection(title: String) {
    Text(
        title, Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
fun SettingItem(
    title: String, subtitle: String? = null, onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingItem(title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun ChoiceItem(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    SettingItem(title, options[selected.coerceIn(0, options.lastIndex)], onClick = { show = true })
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEachIndexed { i, o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(i); show = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = i == selected, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(o)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Закрыть") } }
        )
    }
}

@Composable
fun SettingsScreen(vm: AppVm, onBack: () -> Unit, doSync: () -> Unit, doImport: () -> Unit) {
    var tab by remember { mutableStateOf(SettingsTab.Look) }
    val ctx = LocalContext.current
    val ui = vm.ui
    val lib by vm.store.lib.collectAsState()
    var linkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let { uri -> vm.exportLibrary(uri) }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> vm.importLibrary(uri) }
    }

    Row(Modifier.fillMaxSize()) {
        Rail(
            SettingsTab.values().map { it.title }, tab.ordinal, Icons.AutoMirrored.Filled.ArrowBack, onBack
        ) { tab = SettingsTab.values()[it] }

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            when (tab) {
                SettingsTab.Look -> {
                    SettingsSection("Цвета")
                    ChoiceItem(
                        "Цветовая схема",
                        listOf("Динамический (Android 12+)", "Индиго (как в ViMusic)"),
                        ui.scheme.value
                    ) { ui.scheme.set(it) }
                    ChoiceItem("Тема", listOf("Системная", "Светлая", "Тёмная"), ui.theme.value) { ui.theme.set(it) }
                    ChoiceItem("Темнота", listOf("Нормальная", "Чёрная"), if (ui.black.value) 1 else 0) {
                        ui.black.set(it == 1)
                    }
                    SettingsSection("Формы")
                    ChoiceItem(
                        "Скруглённость миниатюр",
                        listOf("Нет", "Малая", "Средняя", "Большая"), ui.radius.value
                    ) { ui.radius.set(it) }
                    SettingsSection("Стиль")
                    SettingSwitch(
                        "Жидкое стекло",
                        "Полупрозрачные панели и размытая обложка на фоне",
                        ui.glass.value
                    ) { ui.glass.set(it) }
                    SettingsSection("Плеер")
                    SettingSwitch(
                        "Кнопка предыдущей песни в свёрнутом виде",
                        "Показывает кнопку «назад» в мини-плеере",
                        ui.miniPrev.value
                    ) { ui.miniPrev.set(it) }
                }

                SettingsTab.Player -> {
                    SettingsSection("Звук")
                    SettingSwitch(
                        "Экономия трафика",
                        "Выбирать аудио с меньшим битрейтом",
                        ui.lowQuality.value
                    ) {
                        ui.lowQuality.set(it)
                        Yt.lowQuality = it
                        StreamResolver.clear()
                    }
                    SettingsSection("Воспроизведение")
                    SettingSwitch("Повторять текущий трек", null, vm.repeatOne) { vm.toggleRepeat() }
                }

                SettingsTab.Cache -> {
                    SettingsSection("Кэш")
                    SettingItem("Очистить кэш изображений", "Обложки загрузятся заново", onClick = {
                        ctx.imageLoader.memoryCache?.clear()
                        ctx.imageLoader.diskCache?.clear()
                        vm.say("Кэш изображений очищен")
                    })
                    SettingItem("Очистить кэш ссылок на аудио", "Ссылки на потоки будут получены заново", onClick = {
                        StreamResolver.clear()
                        vm.say("Кэш ссылок очищен")
                    })
                }

                SettingsTab.Db -> {
                    SettingsSection("Библиотека")
                    SettingItem(
                        "Состояние",
                        "Избранное: ${lib.favorites.size}, плейлистов: ${lib.playlists.size}, история: ${lib.history.size}"
                    )
                    SettingItem("Экспортировать в файл", "Сохранить избранное, плейлисты и историю",
                        onClick = { exportL.launch("tonic_library.json") })
                    SettingItem("Импортировать из файла", "Добавить данные из ранее сохранённого файла",
                        onClick = { importL.launch(arrayOf("application/json", "*/*")) })
                    SettingsSection("История")
                    SettingItem("Очистить историю и статистику", "Сбросит «Недавно слушали» и «Мой топ 50»",
                        onClick = { vm.clearHistory() })
                }

                SettingsTab.Sync -> {
                    SettingsSection("Google Drive")
                    SettingItem(
                        if (vm.syncOn) "Подключено: ${vm.account ?: "Google"}" else "Синхронизация не подключена",
                        "Избранное, плейлисты и история хранятся в скрытой папке вашего Google Drive"
                    )
                    SettingItem(
                        if (vm.syncOn) "Синхронизировать сейчас" else "Войти через Google",
                        onClick = doSync
                    )
                    if (vm.syncOn) SettingItem("Отключить аккаунт", onClick = { vm.disconnect() })
                    SettingsSection("Плейлисты YouTube")
                    SettingItem(
                        "Импорт из аккаунта",
                        "Ваши плейлисты и «Понравившиеся» (нужен вход через Google)",
                        onClick = doImport
                    )
                    SettingItem(
                        "Импорт по ссылке",
                        "Публичный плейлист YouTube или YouTube Music, вход не нужен",
                        onClick = { linkDialog = true }
                    )
                    if (vm.syncStatus.isNotEmpty()) {
                        Text(
                            vm.syncStatus, Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    SettingsSection("Если вход не работает")
                    Text(
                        "1. В Google Cloud → Audience добавьте свой Gmail в «Test users».\n" +
                            "2. Включите Google Drive API и YouTube Data API v3.\n" +
                            "3. Создайте OAuth client типа Android: пакет com.tonic.player и SHA-1 из README.\n" +
                            "4. На экране «Приложение не проверено» нажмите «Дополнительно» → «Перейти», " +
                            "затем разрешите доступ.\n" +
                            "Без входа можно импортировать плейлисты по ссылке.",
                        Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                }

                SettingsTab.Other -> {
                    SettingsSection("Прочее")
                    SettingItem("Сбросить настройки оформления", "Вернёт вид по умолчанию", onClick = {
                        ui.resetAll()
                        StreamResolver.clear()
                        vm.say("Настройки сброшены")
                    })
                }

                SettingsTab.About -> {
                    SettingsSection("Tonic")
                    SettingItem("Версия", "1.0")
                    SettingItem("Лицензия", "GNU GPL v3")
                    SettingItem(
                        "Оформление",
                        "Вдохновлено ViMusic и ArchiveTune. Музыка и поиск — через NewPipeExtractor."
                    )
                }
            }
        }
    }

    if (linkDialog) {
        AlertDialog(
            onDismissRequest = { linkDialog = false },
            title = { Text("Ссылка на плейлист") },
            text = {
                OutlinedTextField(
                    value = link, onValueChange = { link = it },
                    label = { Text("https://youtube.com/playlist?list=…") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (link.isNotBlank()) vm.importLink(link)
                    link = ""; linkDialog = false
                }) { Text("Импортировать") }
            },
            dismissButton = { TextButton(onClick = { linkDialog = false }) { Text("Отмена") } }
        )
    }
}
