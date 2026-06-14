<p align="center">
  <img src="extras/logo.png" alt="OwnTV" width="360">
</p>

<p align="center">
  <b>Your own IPTV player for Android TV</b><br>
  <sub>Fast · modern · remote-first — bring your own M3U or Xtream sources</sub>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%20TV-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose for TV" src="https://img.shields.io/badge/Jetpack%20Compose-for%20TV-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Player" src="https://img.shields.io/badge/player-libmpv%20%2B%20FFmpeg-FB8C00">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
  <img alt="Built with AI" src="https://img.shields.io/badge/built%20with-AI-8A2BE2">
</p>

<p align="center">
  <a href="https://github.com/ahXN00/OwnTV/actions/workflows/android.yml">
    <img alt="Android CI" src="https://github.com/ahXN00/OwnTV/actions/workflows/android.yml/badge.svg">
  </a>
</p>

---

OwnTV is a native **Android TV** IPTV **player** built with Kotlin, Jetpack Compose for TV, and
**libmpv** (FFmpeg). It's a *player only* — you bring your own M3U or Xtream sources, and OwnTV gives
you a fast, modern, remote-first way to browse and watch them.

> ⚠️ OwnTV does **not** provide any channels, playlists, subscriptions, streams, or media content.
> You are responsible for adding your own legally accessible sources.

This is an **open-source** project — the code is original (not derived from any other app) and was
**built with the help of AI**. **Contributions are welcome**: clone, build, and test it freely. It
targets **Android TV only** (leanback launcher, D-pad-first UI).

---

## ✨ Features

### 🎬 Playback (libmpv / FFmpeg)
- Plays virtually any codec/container, and exposes **every** audio and subtitle track (not just the
  ones the device can decode).
- **TV-optimized rendering** — the decoder writes frames **directly to the display** (the zero-copy
  pipeline streaming apps use): smooth 4K HDR with the panel's native HDR handling and fast channel
  starts, with subtitles drawn by the app. Software decoding is used automatically as a fallback for
  streams the hardware decoder can't handle.
- Custom TV HUD: scrubbable seek bar, prev/next, audio/subtitle/speed pickers, zoom & aspect modes,
  volume, and auto-hide controls.
- Large demuxer cache for smooth 4K/8K streams; **HDR passthrough** when the video and TV support it.
- **Mini-player / PiP** — dock a movie, episode **or live channel** to a corner and keep browsing
  (it keeps streaming across the whole app); selecting another channel updates the docked window,
  and you can expand or close it.
- **Resume, your way** — movies & episodes remember where you stopped; replaying shows a small
  *"Resume at 23:45?"* prompt, with a setting for **Always / Ask / Never** resume.
- 📺 **[Complete player design & feature reference →](extras/player.html)** — an interactive Material 3
  mockup documenting the full player HUD, PiP, popup menus, and remote-key mappings.

### 🧭 Browse
- **Live TV**, **Movies**, **Series**, **Downloads**, and a full **EPG Guide**.
- Folder rail with section-specific **Favorites** and **History** — it expands on focus to show **full
  category names**. Inline per-folder search and a cross-section **global search** — TV-style: search
  bars take focus like any control and only open the keyboard on **OK**.
- **Sort toggle** per section: your **playlist's own order** or **A–Z** (Live TV defaults to playlist order).
- **Customize everything (per profile):** hide, rename & reorder categories; hide & rename channels —
  and it all survives re-syncs.
- Built for scale — tested with ~50k channels / ~168k movies via streaming import and Paging 3.

### 🗓️ EPG
- A full **time × channel guide grid** (XMLTV), plus per-channel **now/next** in the Live preview.
- **Multiple EPG sources** — add any number of XMLTV feeds in **Settings → EPG Sources** (edit /
  delete / re-sync); they merge into the guide. Adding a playlist **auto-syncs its EPG** (Xtream
  `xmltv.php` / M3U `url-tvg`), and you can **manually match** any channel to a guide entry when the
  auto-match misses (per profile, survives re-syncs).
- **Tune from the guide**: OK on a channel tunes straight to it; programme details have a
  *Watch channel* button.
- Clear status: the Guide shows *"Guide loaded: N channels · M programmes"*, and each EPG source
  shows its own sync state.

### 👥 Profiles
- Multiple profiles with their own favorites/history/resume, optional **PIN locks** (salted hash) and a
  **kids** flag, and a "Who's watching?" launch gate. Sources can be shared between profiles.

### ⬇️ Downloads
- Offline downloads for **movies & episodes** (never Live TV), with pause/resume and a user-chosen
  download folder.

### 🎨 Personalization & settings
- Material 3 theme (AMOLED dark / light / system), **any accent color** — presets, a palette, or an
  exact **hex code** (the whole theme is generated from it) — UI zoom, per-profile avatars.
- **Video Player** settings: hardware decoding, default zoom, subtitle size & language, audio sync.
- **Complete Backup & Restore** — one file covers profiles, sources, customizations, **favorites,
  watch history, and resume positions** — and you **choose what to include** on export and what to
  apply on restore; per-source User-Agent, refresh-on-startup, default source.
- **In-app updates** — OwnTV checks GitHub Releases (automatically on startup with a small corner
  status card — toggleable — or manually), shows the full changelog, and installs the new APK right
  on the TV. No browser, no storage permission.

### 🛡️ Robustness
- **Sized for real TVs** — the player's memory budget scales to the device (lean stream buffers on
  low-RAM panels), a decode watchdog blocks 4K/8K software-decode death spirals with a clear error,
  backgrounding releases the stream immediately, and the app sheds caches under memory pressure.
- All player commands run **off the UI thread** — a stalling stream can never freeze the remote (no ANRs),
  and fast preview-scrolling coalesces loads so only the channel you land on is opened.
- **Connection-friendly**: preview → fullscreen reuses the same stream (no reconnect — kind to strict
  1-connection providers), and a dropped live stream **auto-reconnects** before showing an error + Retry.
- Offline detection with a banner, and friendly, offline-aware error messages on import/sync/guide.

---

## 📸 Screenshots

<table>
  <tr>
    <td align="center"><img src="extras/screenshots/Main_View.png" alt="Main view"><br><sub>Main view</sub></td>
    <td align="center"><img src="extras/screenshots/LiveTV_with_PreviewON.png" alt="Live TV with preview"><br><sub>Live TV — preview playing</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="extras/screenshots/Movies.png" alt="Movies"><br><sub>Movies</sub></td>
    <td align="center"><img src="extras/screenshots/Profile_Selection.png" alt="Profile selection"><br><sub>"Who's watching?" profile gate</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="extras/screenshots/Downloads.png" alt="Downloads"><br><sub>Downloads</sub></td>
    <td align="center"><img src="extras/screenshots/Settings_Main.png" alt="Settings"><br><sub>Settings</sub></td>
  </tr>
</table>

More in **[extras/screenshots/](extras/screenshots/)** — Live TV (preview off), playlist management,
personalization, profiles & sources settings.

---

## 🧱 Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.3.10 (AGP 9 **built-in Kotlin**, no `kotlin-android` plugin) |
| Build | AGP 9.2.1 / Gradle 9.4.1, KSP2 2.3.9 |
| UI | Jetpack Compose for TV (`androidx.tv:tv-material` 1.1.0), Compose BOM 2026.05.00 |
| Media | **libmpv** (FFmpeg) — `dev.jdtech.mpv:libmpv` |
| Database | Room 2.8.4 + Paging 3.5.0 + FTS4 (WAL) |
| DI | Koin 4.1.1 |
| Networking | OkHttp |
| Images | Coil 3.3.0 |
| Preferences | DataStore |

`minSdk 26`, `targetSdk 36`, `applicationId tv.own.owntv`.

> **Build note:** Kotlin comes from AGP 9's built-in Kotlin (no `kotlin-android` plugin). KSP 2.3.6+
> supports built-in Kotlin, so Room codegen works alongside it; the Compose compiler and KSP track
> Kotlin 2.3.x.

## ⚙️ How it works (backend)

- **Parsing & sync** — M3U playlists are line-streamed and Xtream `player_api` JSON is read with
  `android.util.JsonReader`, so huge provider payloads are never fully buffered. `SyncManager` does a
  clear-then-insert refresh in ~500-row chunked transactions with Flow progress and cancellation.
- **Storage** — a 19-entity Room schema: profiles & sources (`ProfileSourceCrossRef` for sharing),
  content (categories/channels/movies/series/seasons/episodes), per-profile favorites/history/progress/
  downloads, EPG channels/programmes, and FTS4 search tables. Totals come from indexed `COUNT` queries.
- **Lists** — Paging 3 with a bounded `maxSize` keeps memory flat across 50k+ item lists.
- **EPG** — bulk XMLTV is stream-parsed (gzip-aware) into a rolling now→+48h window and pruned.
- **Player** — a single hoisted `libmpv` surface drives preview, fullscreen, and the mini-player, with
  state published as `StateFlow`s for the Compose HUD.
- **DI** — Koin modules (`appModule`, `databaseModule`, `dataModule`, `playerModule`).

### Project layout

```
tv.own.owntv/
├── core/        database (Room), network, parser (M3U/Xtream/XMLTV), repository, sync, util
├── player/      libmpv player + Compose surface + HUD + mini-player
├── ui/          theme + reusable components (focus surface, cards, state views, avatars)
├── features/    setup, shell, live, movies, series, search, downloads, epg, profiles, settings
└── di/          Koin modules
```

## 📚 Docs & design (`extras/`)

- 📄 **[Complete Product Brief & Build Plan](extras/OwnTV_Complete_Brief_Plan_With_Logo.docx)** — the full
  as-built design brief, architecture, data model, and all 14 build phases.
- 📺 **[Player design reference](extras/player.html)** — an interactive Material 3 mockup of the player UI.
- 🖼️ `extras/logo.png` — the OwnTV logo.

## 📥 Installing (Fire TV / Android TV)

Grab the signed APK from the [**latest release**](https://github.com/ahXN00/OwnTV/releases/latest) and
sideload it. A fixed link always points at the newest signed build:

```
https://github.com/ahXN00/OwnTV/releases/latest/download/OwnTV.apk
```

- **Fire TV** — install the **Downloader** app (by AFTVnews) from the Amazon Appstore, then enter the
  **Downloader code `4308278`** (or [`aftv.news/4308278`](https://aftv.news/4308278), which always
  points at the latest signed `OwnTV.apk`). Enable *Apps from Unknown Sources* if prompted.
- **Android TV / Google TV** — the **Downloader** app is also on Google Play, so the same code
  **`4308278`** works here too. (If Downloader doesn't show in search, open the Play Store on the TV —
  you can reach it via *Settings → Apps → See all apps → Show system apps → Google Play Store* — and
  install it from there.) Or just sideload the APK with your tool of choice (*Send files to TV*, a USB
  drive, or `adb install OwnTV.apk`).

> Only install the APK from this repository's official Releases (or the `…/releases/latest/download/OwnTV.apk`
> link above). It's the build signed by this project's CI — third-party re-hosts aren't endorsed.

## 🛠️ Building & running

1. Open the project in **Android Studio** (a version matching AGP 9.x) and let Gradle sync.
2. Run the `app` configuration on an **Android TV** emulator or device. The app declares
   `LEANBACK_LAUNCHER` and requires the leanback feature, so it appears in the **TV launcher**, not the
   phone launcher.
3. Or from the command line:

```bash
./gradlew assembleDebug
```

On first launch you'll go through onboarding: accept the disclaimer, create a profile, then **add a
source** (M3U or Xtream) — or import a backup. After it imports, browse from the sidebar and open the
**Guide** for the EPG. Everything is managed under **Settings**.

**Tested on:** a real **TCL Google TV**, and the **Android Studio emulator** (both the Android TV and
Google TV system images).

## 🤖 CI & releases

GitHub Actions ([`.github/workflows/android.yml`](.github/workflows/android.yml)) builds the app in the
cloud — no local build needed:

- **Every push / PR** → builds a debug APK and uploads it as a workflow **artifact** named
  `OwnTV-v<version>-<sha>.apk` (download it from the run's *Summary → Artifacts*).
- **Push a `v*` tag** (e.g. `git tag v1.1.0 && git push origin v1.1.0`) → builds a **signed** APK and
  publishes a **GitHub Release** with `OwnTV-v1.1.0.apk` attached. The release notes are taken from
  the newest section of [`CHANGELOG.md`](CHANGELOG.md) (which the in-app updater shows as
  "What's new"), plus GitHub's auto-generated commit list.

**Versioning is automatic**: `versionName`/`versionCode` are derived from the tag (e.g. `v1.1.0` →
`1.1.0` / `10100`) — no need to touch `build.gradle.kts`.

**Signed release builds (optional, recommended for distribution).** Tag builds are debug-signed until you
add a release keystore. Create one and add four repo **Secrets** to get properly signed releases:

```bash
keytool -genkey -v -keystore owntv.keystore -alias owntv -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 owntv.keystore   # copy the output into the KEYSTORE_BASE64 secret
```

Then add repo Secrets (*Settings → Secrets and variables → Actions*): `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Keep the keystore file private — it's never committed.

## 🤝 Contributing

Contributions, bug reports, and ideas are welcome — open an issue or a pull request. Please keep the
project's player-only, bring-your-own-source positioning, and match the existing code style.

## ⚖️ Legal

OwnTV is a media **player** only. It ships with no channels, playlists, subscriptions, or content, and
does not endorse or facilitate access to unauthorized streams. Users are solely responsible for the
sources they add and for complying with the laws and rights that apply to them.

## 📄 License

Released under the **MIT License** — see [LICENSE](LICENSE).

---

<sub>OwnTV is an open-source, player-only project, built with the help of AI.</sub>
