# Tonic

Музыкальный плеер для Android в стиле ViMusic.

- Вертикальная боковая панель и экран настроек как в ViMusic
- Поиск песен в YouTube Music
- Импорт плейлистов YouTube / YouTube Music по ссылке (вход в аккаунт не нужен)
- Свои плейлисты и «Избранное»
- Скачивание треков для офлайн-прослушивания
- Изменение скорости (×0.5–×2.0) и тональности (±12 полутонов)
- Фоновое воспроизведение, уведомление с управлением

## Как получить APK (GitHub Actions)

1. Создайте репозиторий на GitHub и загрузите в него **содержимое** этой папки (чтобы `settings.gradle.kts` лежал в корне репозитория, а `.github/workflows/build.yml` — в `.github/workflows/`).
2. Откройте вкладку **Actions** — сборка запустится сама после загрузки. Если нет — выберите «Build APK» → **Run workflow**.
3. Когда сборка станет зелёной, откройте её и внизу в разделе **Artifacts** скачайте `Tonic-debug-apk`.
4. Распакуйте zip и установите `app-debug.apk` на телефон.

Если на телефоне стоит старая версия Tonic с другой подписью — сначала удалите её.

## Если перестали играть треки

YouTube часто меняет свой код. Поднимите версию в `app/build.gradle.kts`:

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.4")

до последнего релиза: https://github.com/TeamNewPipe/NewPipeExtractor/releases

## Лицензия

NewPipe Extractor распространяется по GPL-3.0, поэтому проект тоже должен быть под GPL-3.0.
Добавьте файл LICENSE через GitHub: Add file → Create new file → имя `LICENSE` → «Choose a license template» → GPL-3.0.
