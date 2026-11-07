package app.tonic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Экран настроек в стиле ViMusic: слева вертикальная панель разделов, справа содержимое. */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var section by remember { mutableIntStateOf(0) }
    val sections = listOf<Pair<String, ImageVector>>(
        "Внешний вид" to Icons.Filled.Palette,
        "Плеер" to Icons.Filled.PlayCircle,
        "Загрузки" to Icons.Filled.Download,
        "Прочее" to Icons.Filled.MoreHoriz,
        "О приложении" to Icons.Filled.Info
    )

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(104.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Spacer(Modifier.height(32.dp))
            sections.forEachIndexed { i, (name, icon) ->
                val selected = i == section
                Column(
                    Modifier.fillMaxWidth().clickable { section = i }.padding(vertical = 14.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selected) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        name,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)) {
            when (section) {
                0 -> AppearanceSection(vm)
                1 -> PlayerSection(vm)
                2 -> DownloadsSection(vm)
                3 -> OtherSection()
                else -> AboutSection()
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AppearanceSection(vm: AppViewModel) {
    SectionTitle("Цвета")
    Text("Цветовая схема")
    Text(
        "Динамическая (Android 12+), на старых версиях — фиксированная",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    Text("Тема")
    Spacer(Modifier.height(8.dp))
    Chips(listOf("Системная", "Тёмная", "Светлая"), vm.themeMode) { vm.setTheme(it) }

    SectionTitle("Формы")
    Text("Скруглённость миниатюр")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Chips(listOf("Малая", "Средняя", "Большая"), vm.rounding) { vm.setRounding(it) }
        Box(
            Modifier.size(48.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(vm.corner))
        )
    }
}

@Composable
private fun PlayerSection(vm: AppViewModel) {
    SectionTitle("Скорость и тональность")
    SpeedPitchControls(vm)
    SectionTitle("Плеер")
    SwitchRow(
        "Кнопка предыдущей песни в свёрнутом виде",
        "Показывает кнопку «назад» в мини-плеере",
        vm.showPrevInMini
    ) { vm.setShowPrev(it) }
}

@Composable
private fun DownloadsSection(vm: AppViewModel) {
    var size by remember { mutableLongStateOf(vm.downloadsSize()) }
    SectionTitle("Загруженные треки")
    Text("Скачано треков: ${vm.downloadedIds.size}")
    Text(
        "Занято: %.1f МБ".format(size / 1024.0 / 1024.0),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "Папка: Android/data/app.tonic/files/Music/Tonic",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { vm.deleteDownloads(); size = vm.downloadsSize() }) { Text("Удалить все загрузки") }
}

@Composable
private fun OtherSection() {
    SectionTitle("Как добавить плейлист")
    Text("Откройте плейлист в YouTube или YouTube Music, скопируйте ссылку и вставьте её в строку поиска. Плейлист должен быть открытым или доступным по ссылке; импортируется до 1000 треков.")
    SectionTitle("Если перестало играть")
    Text("YouTube периодически меняет свой код. Обновите версию NewPipeExtractor в app/build.gradle.kts и пересоберите приложение.")
}

@Composable
private fun AboutSection() {
    SectionTitle("О приложении")
    Text("Tonic 1.0", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text("Распространяется по лицензии GPL-3.0. Оформление вдохновлено ViMusic. Данные YouTube получаются через NewPipe Extractor.")
}
