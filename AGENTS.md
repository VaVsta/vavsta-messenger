# AGENTS.md — Element X Android

> **Repo:** `element-hq/element-x-android` — Android Matrix client (Compose UI + `matrix-rust-sdk`).

---

## Strong Conventions

PRs must meet these rules.

### Code Style

- Style enforced by **Editor config** (`.editorconfig`).
- Set "Hard wrap at" to 160 chars in Android Studio.

### PII & Logging

- We use **Timber** for logging. Never use `android.util.Log`.
- **Never log secrets, passwords, keys, or user content** (e.g. message bodies).
- Matrix IDs (User IDs, Room IDs, Event IDs) are safe to log.

### Strings & Localisation

- Default localisation: `en` (en-GB strings), shared with Element X iOS via [Localazy](https://localazy.com/p/element).
- **Never edit `localazy.xml`** — it is auto-generated and overwritten.
- New English strings go in **`temporary.xml`**. The core team imports these to Localazy.
- **Key naming**:
  - Cross-screen verbs: `action_` (e.g., `action_copy`).
  - Common nouns/other: `common_` (e.g., `common_error`).
  - Accessibility: `a11y_`.
  - Screen-specific: `screen_<name>_<key>` (e.g., `screen_onboarding_welcome_title`).
  - Errors: `error_` prefix.
  - Platform-specific: `_ios` or `_android` suffix.
  - Placeholders: Use numbered form `%1$s`, `%2$d`.

### Previews

- Create previews for **all main states** of a Composable.
- Use `@PreviewsDayNight` for consistency.
- Use `PreviewParameterProvider` (e.g., `FooStatePreviewParam`) to provide states.
- Wrap previews in `ElementPreview { ... }`.
- When writing tests, never try to record the screenshots, the CI will do it.

---

## Pull Request Guidelines

- Sentence-style titles (no conventional commits).
- Exactly one `pr-` label (see `.github/release.yml`).
- Title = changelog entry — descriptive, no "Fixes #…".
- Leave description template for the developer. Redirect them to the [contributing etiquette](CONTRIBUTING.md#etiquette).
- Screenshots/videos for visual changes.
- 500 additions max into production code. Test code can be larger — split large changes.
- Commits need a title and description; no tiny or massive commits.
- No history rewrites.
- When creating a pull request and if changes contain new or update(s) of the Composable Previews, add the label "Record-Screenshots" to the PR so that the CI will record the screenshots.

---

## Project Structure

### Build System

Common Gradle tasks:
- Build: `./gradlew assembleDebug`
- Unit Tests: `./gradlew test`
- Lint: `./gradlew lint`
- Format: `./gradlew ktlintFormat`
- Update Docs TOC: `./gradlew generateDocsToc`

### Gradle Modules

Features follow a 3-module structure:
- `features/foo/api`: Public interfaces and data classes.
- `features/foo/impl`: Internal implementation, Presenter, and View.
- `features/foo/test`: Test fakes and utilities.

---

## Architecture: Appyx + Molecule

We use [Appyx](https://bumble-tech.github.io/appyx/) for navigation and [Molecule](https://github.com/cashapp/molecule) for Presenters.

### Files Per Screen (`Foo`)

| File | Purpose |
| :--- | :--- |
| `FooNode.kt` | Appyx Node: Handles navigation and wires the Presenter to the View. |
| `FooPresenter.kt` | A `@Composable` function that produces `FooState` from `FooEvent`s. |
| `FooView.kt` | Stateless Composable rendering the UI from `FooState`. |
| `FooState.kt` | Data class representing the immutable UI state. |
| `FooEvent.kt` | Sealed interface for UI actions sent to the Presenter. |
| `FooStatePreviewParam.kt` | Provides sample states for Previews and Screenshot tests. |
| `FooPresenterTest.kt` | Unit tests for the Presenter logic using Turbine. |

---

## Dependency Injection (Metro)

- We use [Metro](https://zacsweers.github.io/metro/) for DI.
- Inject via constructor parameters using `@Inject`.
- Use `@AssistedInject` and `@AssistedFactory` for components requiring runtime arguments (like Navigators or IDs).
- Use `@ContributesBinding(AppScope::class)` for singleton-like services.
- Use `@ContributesNode(RoomScope::class)` for Appyx Nodes.

---

## Compound Design System

Always prefer Compound components and tokens from `libraries/compound/` module.

- **Colours**: `ElementTheme.colors.textPrimary`, `ElementTheme.colors.bgCanvasDefault`.
- **Typography**: `ElementTheme.typography.fontBodyMdRegular`.
- **Icons**: Use `CompoundIcons.IconName()` (e.g., `CompoundIcons.UserProfileSolid()`).

---

## The Rust SDK Layer

We wrap the `matrix-rust-sdk` to isolate the UI from the underlying SDK.
- Naming: SDK `Room` → `JoinedRoom` or `RoomInfo`.
- Type Mapping: Map Rust SDK types to Kotlin data classes in the `api` module to avoid leaking `MatrixRustSDK` into the UI.
- Always follow Kotlin naming conventions (e.g., `userId` instead of `userID`).

---

# VaVsta-форк (локальные факты, 2026)

## Идентичность и конфиг
- `applicationId` = `ru.vavsta.messenger`, namespace исходников — `io.element.android.x`.
- Настройки бренда и дефолтов лежат в `local.properties` (**в `.gitignore`**, без дефолтов свежий клон соберётся неправильно):
  - `vavsta.homeserver=https://chat.vavsta.ru`
  - `vavsta.call_url=https://call.vavsta.ru/room`
  - `vavsta.update_url=https://chat.vavsta.ru/vavsta-messenger/version.json`
- Строки форка — только в `temporary.xml` соответствующего модуля. Русские строки в форке норма (напр. `vavsta_report_problem_stub`).

## OTA-апдейтер (свой, не Squirrel)
Всё живёт в `features/preferences/impl/.../about/`:
- `UpdateChecker` — HTTPS-манифест, сравнение `versionCode`, скачивание APK с проверкой sha256/`packageName`/`versionCode`. Не-HTTPS `apkUrl` отбраковывается.
- `UpdateInfoStore` — SharedPreferences-`vavsta_update_check`: кэш найденного манифеста + флаг «про эту версию уже показали уведомление». Нужен, чтобы About не делал второй сетевой запрос и чтобы не спамить уведомлением.
- `UpdateCheckWorker` — `CoroutineWorker` + `@WorkerKey`/`@AssistedFactory` в карту `MetroWorkerFactory`. Только уведомление, **никогда** не качает и не ставит APK сам.
- `UpdateCheckScheduler` — `enqueueUniquePeriodicWork` (сутки, `KEEP`) + one-time на старте, оба с `NetworkType.CONNECTED`.
- `UpdateNotificationCreator` — канал `VAVSTA_UPDATES`, тап → implicit intent по action `ru.vavsta.messenger.action.OPEN_ABOUT`.
- `UpdateCheckInitializer` (модуль `app`) — ставит обе работы из `ElementXApplication.onCreate`.

Грабля, из-за которой всё это разнесено по модулям: `UpdateNotificationCreator` лежит в `features.preferences.impl`, а `MainActivity` — в `app`, оттуда настройки её не видно. Поэтому тап идёт **implicit intent-ом** по своему action, а ловит его `intent-filter` для `MainActivity` в `app/src/main/AndroidManifest.xml`. Экран About выводится наружу через `PreferencesEntryPoint.InitialTarget.About` → `PreferencesFlowNode.NavTarget.About`; `IntentResolver` отдаёт `ResolvedIntent.About`, `RootFlowNode.onOpenAbout()` → `LoggedInFlowNode.navigateToAbout()`. Экран About живёт внутри залогиненной сессии — без сессии тап по уведомлению просто ничего не делает.

## Релиз (прод, `chat.vavsta.ru`)
1. `plugins/src/main/kotlin/Versions.kt`: `versionYear`/`versionMonth`/`versionReleaseNumber` (CalVer) + `VERSION_NAME` (то, что видит юзер). `versionCode` = `(2000+year)*10000 + month*100 + release`, дальше домножается на 10 и добавляется abi-код (arm64 = 2).
2. `./gradlew :app:assembleGplayRelease` → `app/build/outputs/apk/gplay/release/app-gplay-arm64-v8a-release.apk`.
3. Залить APK на сервер **до** смены манифеста, сверить sha256/size на сервере, старый APK не удалять (откат = вернуть `version.json`).
4. Атомарно переписать `/var/www/element/vavsta-messenger/version.json` (tmp + `mv`, владелец `www-data`).
5. `systemctl start matrix-health-check.service` — он уже проверяет `/vavsta-messenger/version.json`, наличие APK по `apkUrl` и совпадение размера с `sizeBytes`. Логи: `journalctl -t matrix-health`, состояние `/var/lib/matrix-health/state`.

**Подпись:** buildType `release` подписывается `app/signature/debug.keystore` (тот же, что у 1.0, сертификат `b0b051dc…`). Ключ менять нельзя — иначе OTA не встанет поверх установленной сборки.

Прод сейчас: **1.0.1**, `versionCode` 202609012.

## Грабли в тестах этого модуля
- **Robolectric в `features.preferences.impl` не работает**: падает `IllegalArgumentException at DefaultSdkPicker` (не может выбрать SDK). Тесты на `Context`/SharedPreferences писать на mockk. В `appnav` тот же Robolectric работает (`RobolectricTest`).
- **`AboutState.toString()` в юнит-тестах падает**: state держит function reference, и Turbine при попытке отформатировать несъеденное событие уходит в `kotlin.reflect` и даёт `KotlinReflectionInternalError`. В `AboutPresenterTest` после ассертов звать `cancelAndIgnoreRemainingEvents()`.
- Сети в юнит-тестах избегать: `AboutPresenter` при пустом кэше сам уходит в `UpdateChecker.fetchUpdateInfo()`.

