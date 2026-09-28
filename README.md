# VaVsta Messenger

> **Неофициальный форк.** Этот проект — переименованный и переоформленный форк
> [Element X Android](https://github.com/element-hq/element-x-android)
> (© Element Creations Ltd / New Vector Ltd). Он **не связан с Element, не
> одобрен и не поддерживается им**, а также не использует имя или логотип
> «Element». «VaVsta», VaVsta Messenger и VaVsta Call — собственные торговые
> марки автора.

VaVsta Messenger — Matrix-клиент для хоумсервера `chat.vavsta.ru`, построенный
на базе [Element X Android](https://github.com/element-hq/element-x-android) —
Matrix-клиента нового поколения, использующего
[Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk), Jetpack
Compose UI и навигацию [Appyx](https://github.com/bumble-tech/appyx).
Минимальная поддерживаемая версия Android — 7.0 (SDK 24).

## Что изменено относительно апстрима

- **Идентичность:** пакет `ru.vavsta.messenger`, название «VaVsta Messenger»,
  собственные иконки, акцентные цвета и рабочий стол (`io.element.android.x`
  переехала в `ru.vavsta.messenger`).
- **Серверы по умолчанию** (переопределяются в `local.properties`, см. ниже):
  - хоумсервер: `https://chat.vavsta.ru`
  - видеозвонки: `https://call.vavsta.ru/room`
  - OTA-обновления: `https://chat.vavsta.ru/vavsta-messenger/version.json`
- **Собственный OTA-апдейтер** (вместо Squirrel): ручная проверка + загрузка +
  установка в экране «О приложении», плюс фоновый воркер (раз в сутки и при
  старте), показывающий тихое уведомление при наличии новой версии. APK
  скачивается и устанавливается вручную; воркер никогда ничего не ставит сам.
- Всё остальное повторяет апстрим-ветку `develop`, пока что-то не трогается
  намеренно.

## Сборка

Клонируйте, откройте в Android Studio (или `./gradlew :app:assembleGplayRelease`).

**Важно:** в исходниках по умолчанию прописаны серверы VaVsta. Чтобы
указать другой хоумсервер, создайте `local.properties` (он в `.gitignore`):

```properties
sdk.dir=/path/to/android-sdk
vavsta.homeserver=https://example.org
vavsta.call_url=https://call.example.org/room
vavsta.update_url=https://example.org/vavsta-messenger/version.json
```

Если `local.properties` нет, сборка всё равно работает, но молча целится в
`chat.vavsta.ru`.

> **Про подпись:** buildType `release` подписывается
> `app/signature/debug.keystore` — ключом, который закоммичен в апстрим-репо
> и потому **публичен**. Это сохраняет совместимость OTA с уже установленными
> сборками, но **не является защитой от подмены**. Подлинность OTA по факту
> обеспечивается HTTPS на адресе манифеста.

## Выпуск обновления

`tools/publish-ota.sh <путь-к-arm64-apk> [примечания]` — читает реальный
`versionCode` из APK, пишет `version.json`, заливает оба файла в вебрут и
проверяет публичные URL. Схема версии — CalVer
(year.month.release, например `202609032`).

## Лицензия

Данное ПО — форк Element X Android, используемый под
**GNU Affero General Public License, версия 3 (AGPL-3.0-only)** — открытой
веткой двойной лицензии Element.

Авторское право апстрима:
- Copyright (c) 2025 Element Creations Ltd.
- Copyright (c) 2022–2025 New Vector Ltd.

Изменения форка принадлежат автору VaVsta (см. `git log`). Во всех файлах,
включая изменённые, сохранены апстрим-заголовки
`SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial`;
полный текст AGPL-3.0 — в [`LICENSE`](LICENSE).

«Element» — товарный знак Element (Elemental Technologies Ltd / Element
Creations Ltd). Это независимый форк, и ничего в нём не даёт права на
использование бренда Element.