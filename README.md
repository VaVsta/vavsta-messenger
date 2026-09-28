# VaVsta Messenger

> **Unofficial fork.** This project is a renamed and re-branded fork of
> [Element X Android](https://github.com/element-hq/element-x-android)
> (c) Element Creations Ltd / New Vector Ltd. It is **not affiliated with,
> endorsed or sponsored by Element**, and does not use the "Element" name or
> logo. "VaVsta", VaVsta Messenger and VaVsta Call are the author's own marks.

VaVsta Messenger is a Matrix client for the `chat.vavsta.ru` homeserver, built on
top of [Element X Android](https://github.com/element-hq/element-x-android) —
the next-generation Matrix client that uses the
[Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) and a
Jetpack Compose UI with [Appyx](https://github.com/bumble-tech/appyx)
navigation. Minimum supported Android version is 7.0 (SDK 24).

## What changed vs upstream

- **Identity:** package id `ru.vavsta.messenger`, app name "VaVsta Messenger",
  own icons, accent colors and launcher assets (`io.element.android.x` moved to
  `ru.vavsta.messenger`).
- **Default servers** (override in `local.properties`, see below):
  - homeserver: `https://chat.vavsta.ru`
  - video calls: `https://call.vavsta.ru/room`
  - OTA updates: `https://chat.vavsta.ru/vavsta-messenger/version.json`
- **Custom OTA updater** (replaces Squirrel): manual check + download + install
  in the About screen, plus a background worker (once a day and on app start)
  that shows a silent notification when a newer version exists. The APK is
  downloaded and installed manually; the worker never auto-installs.
- Everything else tracks upstream `develop` unless deliberately touched.

## Building

Clone, open in Android Studio (or `./gradlew :app:assembleGplayRelease`).

**Important:** the source defaults to the VaVsta servers. To point the build at
another homeserver, create `local.properties` (git-ignored):

```properties
sdk.dir=/path/to/android-sdk
vavsta.homeserver=https://example.org
vavsta.call_url=https://call.example.org/room
vavsta.update_url=https://example.org/vavsta-messenger/version.json
```

If `local.properties` is missing the build still works but silently targets
`chat.vavsta.ru`.

> **Signing note:** the `release` build type is signed with
> `app/signature/debug.keystore`, a key that is committed in the upstream repo
> and therefore **public**. It keeps OTA updates compatible with already
> installed builds, but it is *not* a security boundary. OTA authenticity
> effectively comes from HTTPS on the manifest URL.

## Releasing an update

`tools/publish-ota.sh <path-to-arm64-apk> [notes]` — reads the real
`versionCode` from the APK, writes `version.json`, uploads both to the webroot
and verifies the public URLs. Version schema is CalVer
(year.month.release, e.g. `202609032`).

## License

This software is a fork of Element X Android and is used under the
**GNU Affero General Public License, version 3 (AGPL-3.0-only)** — the
open-source branch of Element's dual license.

Upstream copyright:
- Copyright (c) 2025 Element Creations Ltd.
- Copyright (c) 2022–2025 New Vector Ltd.

This fork adds changes by the VaVsta author (see `git log`). All files, including
modified ones, retain their upstream `SPDX-License-Identifier:
AGPL-3.0-only OR LicenseRef-Element-Commercial` headers; a valid AGPL-3.0
license is in [`LICENSE`](LICENSE).

"Element" is a trademark of Element (Elemental Technologies Ltd / Element
Creations Ltd). This project is an independent fork and nothing here grants a
right to use the Element brand.