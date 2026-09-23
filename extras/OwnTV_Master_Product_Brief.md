<p align="center">
  <img src="logo.png" alt="OwnTV" width="300">
</p>

<h1 align="center">OwnTV — Master Product Brief</h1>

<p align="center">
  <b>Native Android TV IPTV player · dual playback engine · Jetpack Compose for TV</b><br>
  <sub>Bring your own M3U, Xtream or Stalker/Ministra portal sources</sub>
</p>

---

## 1. Overview

OwnTV is a native **Android TV** IPTV player written in Kotlin with Jetpack Compose for TV, running
**two playback engines**: **libmpv (FFmpeg)** for films, series and maximum compatibility, and
**ExoPlayer (Media3)** for near-instant Live TV.

It is a **player only**. You supply an Xtream login, an M3U playlist (URL or a local file) or a
Stalker/Ministra portal (Portal URL + MAC, with optional Serial Number, Device ID, Device ID2 and
Signature for stricter portals).

**Key principles**

| | |
|---|---|
| **Player only** | No bundled content, no subscriptions, no paywalls |
| **Remote-first** | D-pad navigation designed for a screen across the room |
| **Language-first** | 26 packaged interface languages, chosen before anything else |
| **Dual engine** | Compatibility and speed, rather than a compromise between them |
| **Built for scale** | Tested at ~50k channels / ~168k films |
| **Open source** | GPLv3, original code, built with the help of AI |

A companion **[OwnTV Mobile](https://github.com/ahXN00/OwnTV_Mobile)** app for phones and tablets
shares the same engine through a common core library.

---

## 2. Playback

Each engine is chosen automatically by content type, with fallback between them.

### 2.1 Dual-engine design

| Engine | Role |
|---|---|
| **libmpv (FFmpeg)** | Films, series, and any live stream ExoPlayer cannot open. Widest codec and container support, every audio and subtitle track, zero-copy 4K/HDR rendering |
| **ExoPlayer / Media3** | Live TV by default. Opens HLS almost instantly, so the preview and full screen appear with no wait; the running preview is promoted to full screen with no reload |

- **Engine preference, per section** — four choices each for Live TV and for Movies & Series: either
  engine first with automatic handover, or one engine **only** with the handover off (that engine
  still gets both its `.m3u8` and `.ts`). Defaults differ deliberately: Live starts on ExoPlayer,
  VOD on mpv. Overridable **per playlist**.
- **Per-item toggle** — the **⇄ MPV/EXO** pill pins a channel or a film to the other engine and
  remembers it, in both directions. A pin always outranks the setting.
- **Track memory** — the audio and subtitle language picked in the player is remembered per
  channel, film and series (per profile), on both engines.
- **Per-playlist provider quirks** — catch-up time zone, "give up after" time and an HTTP Referer
  can be set for one playlist without touching the others.
- **The fallback ladder** — a failing live channel walks up to four rungs, each tried once:
  `ExoPlayer+HLS → ExoPlayer+TS → mpv+HLS → mpv+TS`, or the same list led by mpv. **Give up on a
  channel after** (15/30/60 s or Never, default 30 s) bounds the whole tune; provider-requested
  back-off waits are not charged against it.
- **VOD fallback is never remembered** — every film starts on the engine you chose.
- **Reset saved player choices** forgets every per-item pin in one step.
- **Protected (DRM) playback** — Widevine and ClearKey over DASH and HLS, read from the playlist's
  `#KODIPROP` licence properties, for live channels, films and episodes. The device's own CDM does
  the work, so there is nothing to configure and no licence to buy; the device's security level
  decides whether HD is served. Such an item is pinned to ExoPlayer, outranks every other preference,
  and never leaves for an external player. It also **cannot be recorded** — the CDM decrypts only
  into a secure decoder for immediate display, so a recording is refused before it starts rather than
  left as a file that will not play.
- **Container is decided from evidence, not from the file extension** — the declared
  `manifest_type`, then what the response actually turns out to be, then what the same provider has
  already been caught serving. This is what lets a DASH channel published at an extensionless address
  play at all, and it is the only route available to Stalker portals and Xtream panels, whose stream
  addresses can carry no declaration.

### 2.2 Rendering

- **Direct-to-display zero-copy pipeline** — the decoder writes frames straight to the screen, giving
  smooth 4K HDR with the panel's own HDR handling.
- App-drawn subtitles on the direct path; automatic **software-decode fallback** for streams the
  hardware decoder mangles; late-frame dropping to hold A/V sync on high-bitrate content.
- **Frame-rate matching** (opt-in) — asks the display for the video's native rate to remove judder.
  When a live stream declares no rate it is measured, and used only when two samples agree. Android
  TV's own *Match content frame rate* preference is honoured above the app's toggle.
- **Live buffering under user control** — the **Live latency** choice sizes the real buffer on both
  engines, and a separate **Pre-buffer** gate (off / 2 / 5 / 10 s) holds playback until enough video
  is collected. Both overridable per playlist.

### 2.3 Audio

- Every audio track is exposed with language labels and switchable on the fly.
- **Surround sound** — *Auto* / *Stereo only* / *Surround*, applied to both engines. A shared,
  non-disableable output watchdog (silence, sink error, repeated underruns) latches the session to
  stereo when the sink accepts a format it cannot actually play. On ExoPlayer that recovery recreates
  the surface and resumes at the same position rather than mistaking an audio problem for a video one.
- **Volume boost to 150%** with a soft limiter.
- **A/V sync nudge** in 25 ms steps, optionally remembered per item.
- **Audio-only items are labelled, not failed** — a radio channel shows an *Audio only* plate, so
  sound with no picture is never mistaken for a fault. Distinct from **Audio Mode**, which is the
  user switching the picture off.

### 2.4 Subtitles

| Kind | How |
|---|---|
| Text (SRT/ASS) | Drawn by the app on the direct render path |
| Image (PGS/VOBSUB/DVB) | Handed to a second ExoPlayer layer, keeping video on the zero-copy/HDR path |
| Closed captions (CEA-608/708) | Decoded from the video stream into a selectable track |

Independent **font, scale, colour, position and background** across mpv, ExoPlayer and the app-drawn
overlay; *Default* preserves authored styling. **Preferred audio/subtitle language** selects the
matching track on both engines. Subtitle size is stored per engine, because the two render the same
multiplier at visibly different sizes.

External subtitles come from **OpenSubtitles** (own account, remote sign-in by QR + PIN) or a
**local file**, with a timing nudge in 0.1 s / 0.5 s steps.

### 2.5 Player HUD

Scrubbable seek bar · previous/next through the episode queue · play/pause · audio, subtitle and
speed pickers · zoom and aspect (Fit · Fill · Stretch · Original · Force 16:9 · Force 4:3) · volume
with mute · favourite the current item · **stream info overlay** (codec, resolution, fps, bit depth,
HDR type, bitrate, decoder, audio, buffer, dropped frames, masked source URL) · a **clock** in every
mode, becoming *Programme time* + *Current time* during a replay · auto-hiding controls, with **Back**
hiding them first and then exiting.

### 2.6 Channel zapping

**CH+/CH−** always switch channels for the whole Live TV or catch-up session, including while the
controls are up or playback is starting, wrapping at both ends. **Up/Down** (controls hidden) and the
media ⏮/⏭ keys do the same. **Left** opens the channel list for the context the channel was opened
from; **Left again** opens the category browser without leaving full screen. **Right** shows the last
30 channels. Typing a **channel number** tunes it directly.

### 2.7 Resume, auto-play and the mini-player

Films and episodes remember where you stopped (*Always* / *Ask* / *Never*). **Auto-play next episode**
rolls on across seasons, and carries catch-up on to the next programme in the guide. Reopening a
series jumps to the last-watched episode.

The **mini-player** docks a film, episode or channel to a corner and keeps playing across the whole
app; selecting another channel updates it in place. Size and position are one popup in Settings.

### 2.8 Robustness

Memory budget scales to the device · a decode watchdog blocks 4K/8K software-decode death spirals
with a clear error · backgrounding releases the stream immediately · every player command runs off
the UI thread · auto-reconnect with backoff on dropped live streams · corrupted-file recovery
destroys and recreates the mpv instance so one bad file cannot poison the session · screensaver
restore brings a paused film back at the exact spot and re-tunes a live channel to the edge.

---

## 3. Browse

### 3.1 Home

A hero row of partly-watched films, episodes and recent channels, newest first; the focused card
expands and plays a muted preview (switchable, and off by default on a low-RAM device). Below it,
**Favourite Channels**, **Continue Watching** rows and an optional **Recent Channels** row. A
**Continue** chip in the top bar resumes the most recent item from any screen. Home feeds the system
**Watch Next** row on stock Android TV launchers.

**Now Trending** is an optional fixed first row: 4–10 current TMDB titles, shown only after matching
titles the active provider can actually play. The TMDB chart is re-fetched per source on a randomised
5–8 day schedule, while matching re-runs every sync at no API cost, so new catalogue titles surface
the same day. It behaves as a single focus row, with auto-advance that pauses on the main actions.

### 3.2 Sections

Live TV shows a preview pane with the channel's video, now/next/later and the **real** stream
resolution. Movies and Series are poster walls with a **Grid/List** toggle, and inside a show an
**Episode Grid/List** toggle swaps rows for a wall of episode stills — a whole show's details arrive
in one request. Downloads holds offline films and episodes, and recordings as its Live TV tab.

### 3.3 Multiple playlists

Merge every playlist into one browse, or narrow the whole app to one. The choice applies everywhere
at once and survives a restart; a **quick switcher** in the top bar appears once there are two. It is
display-only — nothing is deleted or re-imported. When two sources are active for a section, compact
**provider labels** identify categories and items throughout. **Test connection** reports account
status, expiry, trial and connections in use, and writes nothing.

### 3.4 Stalker / Ministra portals

A third source type alongside M3U and Xtream: Portal URL + MAC, with optional Serial Number, Device
ID, Device ID2 and Signature. MAG handshake auth with in-memory tokens and User-Agent presets. Play
links are minted per play and silently re-resolved on expiry, so long sessions, downloads with range
resume, and cross-engine fallback all survive token resets. Full feature parity: downloads, external
player, TMDB, backup, auto refresh and the playlist switcher.

### 3.5 Layout

A fixed, slim icon rail with the logo at the top and the profile avatar at the bottom; a top bar
carrying the section chip, search, clock, weather, active playlist and Continue. Live TV, Movies and
Series share **one rounded browse container** with category, list and preview regions. Hand-drawn
duotone navigation icons. **Panel Width Adjustment** sets each section's three widths, with the
preview allowed to be 0% to hide it; the Guide's two columns split independently. Theme is
Dark / Light / System.

Movies and Series additionally offer a second layout, chosen in Settings → Layout → **Movies &
Series layout** and shared by both sections. **Separate panels** is the three-region container above
and remains the default. **Cinematic** drops the container entirely: the focused title's TMDB
backdrop is drawn full-bleed behind the whole screen under two fixed wash gradients, the category
panel floats on it as its own translucent plate, and a **read-only** detail block — title-logo
artwork with the name beneath it, rating, quality badges, genres, plot and cast photos — sits above a
wide poster grid. It is deliberately grid-only and deliberately has no focusable children, so focus
never leaves the grid and every action stays where it already is: OK plays, long-press opens the
context menu, resume state reads as a badge and a poster sliver. Panel Width Adjustment resolves the
same section into two columns there, with the detail block's height held as its own separate
setting rather than as one of the three width shares. Live TV is never Cinematic.

### 3.6 Categories, search and memory

Folder rails with Favorites and History per section; full category names, never abbreviated; a
category search box. **Customize** (per profile) hides, renames and reorders categories and
individual items, recovers hidden ones, filters to All/Visible/Hidden, and can be **PIN-locked**.
**Bulk rename** applies ordered prefix/suffix rules with automatic cleanup, a review step and a
restore-original undo. **Custom combined categories** gather items from anywhere. A category can also
be hidden or moved straight from the browse screen by holding OK. All customizations survive
re-syncs.

Search covers Live, Movies and Series together, with a "Jump to" launcher row when empty. Live TV
reopens on the category you last used, with focus on the last channel. **App startup** is per
profile: Home, last channel, Live Favorites, or one chosen channel.

---

## 4. EPG / TV Guide

- **Full time × channel grid** (XMLTV), opening with the current time about 37.5% across and two
  recent hours already loaded. An amber **NOW** badge and row marker refresh every 30 seconds.
- Two-stage navigation: **Right** selects a channel's row, **OK** steps in to browse programmes.
- Sort by A–Z, Provider, Live TV order, Catch-up or Favorites; filter by category.
- **Catch-up TV** — replay programmes that already aired, up to 7 days, seekable. **Live rewind**
  scrubs the live stream on archive-capable channels. **Go back to…** jumps straight to a time, with
  an exact day/hour/minute picker clamped to the archive window — and works with **no guide at all**.
  A **Catch-up category** in Live TV lists every channel that advertises an archive.
- **Auto-match EPG** links channels to guide data when `tvg-id` is missing or wrong; confident matches
  apply automatically and the rest go to a review list. Matches are per profile and survive re-syncs.
  A **guide time offset** corrects a feed published in another time zone, globally or per channel.
- **Multiple XMLTV feeds** merge into one guide. Opt-in: importing a playlist does not auto-download
  its guide. Each feed can use its own channel logos and has its own auto-refresh interval.
- **Performance** — pre-loaded in the background; only your channels' programmes are stored; a
  malformed tag no longer aborts the guide; batched loading instead of a query per row.

---

## 5. Recording & Multiview

### 5.1 Record live TV

Record from the **Guide** (following the programme's times, with padding), from the **channel list**,
or from the **player**, on live channels. **Record every showing** sets a standing rule for that
programme on that channel. A catch-up programme can be saved from the provider's archive. Recordings
appear in **Downloads → Live TV** with duration, size and full path.

A recording costs one of the playlist's connections and says so before it starts. A timer booked
across a reboot is repaired on the next launch.

### 5.2 Multiview

Up to four live channels at once, entered from the player or by marking channels with **Add to
Multiview**. One tile carries the sound at a time; **sound only** gives up a tile's picture and keeps
its commentary. A tile that cannot start explains why rather than sitting blank. Leaving the grid
stops everything.

Tiles draw through a **TextureView**, not a SurfaceView: a device has very few hardware video planes
and several have exactly one, so a second SurfaceView would get audio and no picture.

### 5.3 Connection limits

Most providers never publish how many streams an account may run, so OwnTV **measures it once**, at a
playlist's first sync, before any channel rows exist and while nothing is playing. The result is
stored with the playlist, carried in backups (it describes the account, not the device) and used to
warn before a stream is refused. A broken channel cannot fool it — it stores nothing rather than a
wrong number. **Settings → Playlists → Info** shows it; **Re-test** re-measures behind a warning.

---

## 6. Profiles

Multiple profiles, each with its own favourites, history, resume positions and layout. Optional
**PIN locks** (salted hash) and a **"Who's watching?"** launch gate. **Kids mode** hides adult
provider categories and items across every surface — Live, Movies, Series, Home, Search, Guide,
catch-up, downloads, launcher recommendations and direct playback — and excludes adult TMDB results;
the Guide itself is hidden. Detection uses multilingual category markers with an *Adult Swim*
exception. Sources can be shared between profiles, and profiles switch without leaving the app.

---

## 7. Downloads & storage

Offline downloads for films and episodes (never live channels), with pause, resume, retry and delete,
a queue grouped Active / Waiting / Completed / Failed, and a storage bar. Downloads continue when the
app leaves the screen. The download folder is the user's choice. Removing the target USB stick marks
that download failed rather than losing it silently.

---

## 8. Personalization & settings

**Settings is a spine and a sheet**: the left spine lists **Quick** plus nine section headings, each
with an icon and row count; only that section's rows compose on the right, inside one sheet with
values right-aligned and a chevron only where a row opens another screen. Returning from a sub-screen
restores focus to the exact row. A **search pill** searches every setting at once and names the full
path in each result.

- **Appearance** — theme, any accent colour (preset, palette or hex, generating the whole theme), a
  separate **focus highlight** colour and thickness, UI zoom, and **font customization** (main text
  60–140%, popup text 50–120%, popup geometry 50–120%, six bundled families).
- **Glass Effect** — an opt-in, interaction-aware material on its own page with a live preview: six
  clarity presets, 20–100% tint, ten real frost levels, adaptive readability, optional depth and
  parallax, a local or remote wallpaper, and per-surface control. Real frost needs Android 12+ and a
  background image; otherwise panels stay readably tinted.
- **Ambient Glow** — a separate radiance for the solid interface, available only with the explicit
  Dark theme while Glass is off.
- **Remote Shortcuts** — short and long presses of spare colour, number, channel and media keys
  mapped to 25 actions. Essential keys stay protected; the shipped CH+/− paging remains the default.
- **Content** — clear watch history by type; per-source auto refresh; browsing and list toggles.
- **Video player** is the complete playback list, so no playback setting lives on two screens.
- **Error log** (App) — the last crash plus a readable history of playback failures, fallbacks and
  reports, with optional detailed tracing. A crash is written to disk as it happens, so it survives
  the process dying. Export writes `Download/owntv-playback-report.txt`.

---

## 9. Language & first run

A fresh installation opens with a **language selector before Get Started**. English plus 25 packaged
translations; further requested languages stay catalogue-only until they reach the reviewed
readiness threshold. The first run is welcome (which carries the language selector) → text size →
disclaimer → **Set up OwnTV** → profile → **add a playlist** → how to enter it → the import, closing
with an offer to download the guide. **Set up OwnTV** offers three routes, not two: create a profile,
restore a backup file, or **copy everything from another OwnTV device** over the local network — so
replacing a box does not mean finishing setup first and then finding Local sync in the menus. The
"how to enter it" page is the one that keeps an Xtream password off the D-pad: **Remote** hands the
form to a phone over Wi-Fi, **Manual** types it on the television. It is the same page Settings →
Manage sources reaches later, so the choice is never a first-run-only opportunity.

App language is independent of profiles and of the separate TMDB metadata language, and survives
restart and backup/restore. Locale-aware plurals, dates, times, numbers, RTL navigation, font
fallback and English fallback apply across the interface, notifications, launcher text, companion
pages, player messages and diagnostics — without changing playback or sync behaviour. A generated
locale catalogue, six resource domains, Hosted Weblate contribution paths, tooling tests and CI
checks protect placeholders, plural forms, formatting, overflow and release packaging.

---

## 10. Backup, sync & updates

**Backup & Restore** writes a single `.own` container: the backup data, the Glass wallpaper and the
downloaded subtitle files. An optional **backup password** encrypts the whole container
(AES-256-GCM, PBKDF2); without one the file is unencrypted and every secret is omitted — source and
proxy passwords, Stalker identity, TMDB and OpenSubtitles credentials, PIN hashes. Restore accepts
`.own` and legacy `.json`, detected by content rather than extension, and **merge-restore remaps
every id** the file carries. Sources are matched on type + URL + username, plus the MAC for Stalker.

> Android's automatic backup is deliberately **disabled**: the raw stores hold plaintext credentials,
> so this screen is the single explicit, encryptable path off the device.

**Local sync** swaps favourites, history and resume positions with the OwnTV mobile app over the
local network — no account, no cloud. Both devices enter Sync mode deliberately, an arriving
container is previewed before it is applied, and a deletion propagates as a deletion rather than
being undone by the merge. The same engine is offered during first run, where a device that has
nothing yet only receives and never hosts, so only the established device enters Sync mode.

**Updates** — in-app, from GitHub Releases, with an optional startup check, the full changelog on a
manual check, and installation on the TV itself.

---

## 11. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.20 (no `kotlin-android` plugin; the Compose compiler plugin pulls the Kotlin Gradle plugin to this version) |
| Build | AGP 9.4.0 / Gradle 9.7.1, KSP2 2.3.11 |
| UI | Jetpack Compose for TV (`androidx.tv:tv-material` 1.1.0), Compose BOM 2026.08.00 |
| Media | libmpv (FFmpeg) — `dev.jdtech.mpv:libmpv` · ExoPlayer/Media3 1.11.1 |
| Database | Room 2.8.5 + Paging 3.5.1 + FTS4 (WAL) |
| DI | Koin 4.2.2 |
| Networking | OkHttp 5 — the panel-facing client is pinned to HTTP/1.1 for flaky IPTV panels, while the image client keeps h2 so poster grids multiplex on one connection |
| Images | Coil 3.6.0 |
| Preferences | DataStore |
| SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 37`, `applicationId tv.own.owntv` |

---

## 12. Architecture

### 12.1 Three repositories

| Repo | Holds |
|---|---|
| **[OwnTV_Core](https://github.com/ahXN00/OwnTV_Core)** | Database, sync, parsers, EPG, backup, profiles, downloads, recording, settings storage, both playback engines, **and every translated string** |
| **[OwnTV](https://github.com/ahXN00/OwnTV)** | This app — Compose-for-TV interface, navigation, player HUD |
| **[OwnTV_Mobile](https://github.com/ahXN00/OwnTV_Mobile)** | The phone and tablet interface |

Core is published as `tv.own.owntv:core` and `tv.own.owntv:player-core`; both apps always consume the
same version.

### 12.2 Parsing & sync

M3U playlists are line-streamed and Xtream `player_api` JSON is read with `android.util.JsonReader`,
so a huge provider payload is never fully buffered. Typed M3U import routes `type=` / `tvg-type=`
entries into Movies and Series. `SyncManager` inserts in chunked transactions with progress and
cancellation, with the database write on its own coroutine fed over a rendezvous channel — so
downloading and parsing overlap with writing the previous batch. A truncated bulk list (HTTP 512)
falls back to per-category fetching.

### 12.3 Metadata & Trending

`MetadataRepository` is the shared lazy TMDB cache; Trending populates it from an already-confirmed
id rather than a second fuzzy search. A self-hostable **Cloudflare Worker** gateway canonicalises
cache keys, gives Trending a 15-minute fresh / 24-hour stale policy and ordinary metadata 30-day /
90-day, retries a transient failure once, and exposes diagnostics without exposing the TMDB key. The
default gateway is access-controlled with a build-time shared key plus a per-install client id,
checked inside the Worker. Each install holds its own allowance (40/minute, 150/hour, 400/day),
metered at a single choke point so no endpoint can bypass it. Detail cache keys carry the metadata
language, so switching language does not wipe the cache.

A personal TMDB key, and OpenSubtitles credentials, can be handed over from another device by QR +
PIN rather than typed with a remote. The same companion server can receive an uploaded `.m3u` file.

### 12.4 Storage

A Room schema covering profiles and sources (cross-referenced for sharing), content (categories,
channels, movies, series, seasons, episodes), per-profile favourites/history/progress/downloads, EPG
channels and programmes, recordings and recording rules, external-subtitle cache and links, FTS4
search tables, and per-source Trending snapshots. Paging 3 with a bounded `maxSize` keeps memory flat
across 50k-item lists. `ANALYZE` runs after sync and at startup to keep query-planner stats fresh.

### 12.5 Player

Two engines behind one `PlaybackEngine` interface, with the fallback ladder and its watchdogs in
`:player-core` and therefore shared with the mobile app. Live promotes the running preview straight
to full screen; the shell hoists the active surface between full screen and mini-player; player state
is published as `StateFlow`s for the Compose HUD. A stuck demuxer triggers a destroy-and-recreate of
the mpv instance.

### 12.6 Dependency injection

Koin modules: `appModule`, `databaseModule`, `dataModule`, `playerModule`.

---

## 13. Legal

OwnTV is a media **player** only. It ships with no channels, playlists, subscriptions or content, and
does not endorse or facilitate access to unauthorized streams. Users are solely responsible for the
sources they add.

Released under the **GNU General Public License v3.0**.

Metadata and trailers from [TMDB](https://www.themoviedb.org/) (not endorsed or certified by TMDB).
Subtitles from [OpenSubtitles](https://www.opensubtitles.com/) (not endorsed or certified by
OpenSubtitles). Translations hosted free of charge by
[Hosted Weblate](https://hosted.weblate.org/projects/owntv/).
