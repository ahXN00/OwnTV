<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="brand/app-logos/logo_eggshell_light.png">
    <img src="brand/app-logos/logo_eggshell.png" alt="OwnTV" width="300">
  </picture>
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
  For films it can also hold playback through a non-seamless switch and match the film's resolution
  (never above the TV's own) — both opt-in.
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
- **A/V sync nudge** in 25 ms steps on both engines, optionally remembered per item.
- **Previous channel** — a player-bar button, the remote's Last-channel key and the media controls' "previous".
- **Night mode and Volume leveling** on both engines, and a
  **Dolby/DTS passthrough** switch; either of the first two makes ExoPlayer decode in the app.
- **Maximum video quality** plus a per-item **Quality** button; experimental **tunneled playback** for
  live ExoPlayer, offered only where a decoder supports it and switched off after the first failure.
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
overlay; *Default* preserves authored styling. **Preferred audio/subtitle language** (per profile, 50
languages, plus **Original language** for audio — TMDB's original language of the film or series,
else the stream's main track) selects the matching track on both engines. Subtitle size is stored per engine, because the two render the same
multiplier at visibly different sizes.

External subtitles come from **OpenSubtitles** (own account, remote sign-in by QR + PIN) or a
**local file**, with a timing row in the Subtitles menu (◀ ▶ 0.1 s, OK = back to zero).

### 2.5 Player HUD

Scrubbable seek bar · previous/next through the episode queue · play/pause · audio, subtitle and
speed pickers · zoom and aspect (Fit · Fill · Stretch · Original · Force 16:9 · Force 4:3) · volume
with mute · favourite the current item · **stream info overlay** (codec, resolution, fps, bit depth,
HDR type, interlacing on mpv, bitrate, decoder, audio, buffer, dropped frames, masked source URL) · a **clock** in every
mode, becoming *Programme time* + *Current time* during a replay · auto-hiding controls, with **Back**
hiding them first and then exiting.

### 2.6 Channel zapping

**CH+/CH−** always switch channels for the whole Live TV or catch-up session, including while the
controls are up or playback is starting, wrapping at both ends. **Up/Down** (controls hidden) and the
media ⏮/⏭ keys do the same. **Left** opens the channel list for the context the channel was opened
from; **Left again** opens Live TV's own category sheet (Favorites, Recently watched, Catch-up, All
channels and every group) without leaving full screen. **Right** shows the last 30 channels with when
each was watched. All three are Stage glass sheets at the screen edge, and CH+/CH− page through them. Typing a **channel number** tunes it directly.

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

**Now Trending** opens Home: 4–10 current TMDB titles, shown only after matching titles the active
provider can actually play. The TMDB chart is re-fetched per source on a randomised 5–8 day schedule,
while matching re-runs every sync at no API cost, so new catalogue titles surface the same day. In the
default **Full-bleed** layout it is one title over its dissolving backdrop — rank, *In your playlist*,
title, year, genres, runtime or seasons, rating, synopsis, and Play / Trailer / All versions (with a
count) / details / favourite — with a pager underneath (◀ ▶ change the title; auto-advance pauses while
the hero has focus). Moving into the rows folds the hero to a single line; ▲ from the first row
unfolds it. **Posters only** makes the trending titles the first poster row instead.

**Keep watching** follows: partly-watched films, episodes and recent channels as 16:9 stills (TMDB
backdrop → provider backdrop → poster → channel logo on a plate) with progress and time left; resting
on a card for 3 s plays a muted preview inside it. Then **Favourite channels** (cards or an On-now
mini-guide), **Favourite movies** and **Favourite series** poster rows (OK opens the film or the
show), continue rows for films and series as posters, and an optional **Recent channels** row.
Home feeds the system **Watch Next** row on stock Android TV launchers.

### 3.2 Sections

**Live TV** — a header with the category and channel count, a tool row (search, sort, Guide view) and
84-high channel rows (number, logo plate, name, programme and time left, progress, provider tags,
catch-up, favourite, playlist mark). Beside them the preview pane plays the channel with a LIVE badge,
the real quality / fps / sound, the programme with progress and synopsis, and next / later — **▶**
steps into that schedule, where OK offers Remind me / Record / Watch channel. **◀** brings the
categories in as a glass sheet (**Stage** layout) or keeps them as a column (**Separate**). The
channel menu is a grouped Stage menu (Watch / Channel / Guide data / Organise), opened by holding OK
or the Menu key. **Guide view** shows the guide grid inside Live TV, and *Live TV opens in* remembers
List or Guide.

**Movies and Series** — Cinematic or Separate panels (3.5), a sort tool, a title menu (Watch /
Library / Organise / Details). A **series page** carries the backdrop, Resume/Play, Favourite,
**Download season** and series options, season tabs with counts (Specials last and uncounted),
Grid | List and an Episode order menu (oldest/newest, hide watched); episode titles are cleaned of
"Show – S01E03 –" prefixes, with TMDB's title filling a gap. Page and hero headlines drop a leading
provider tag such as "|MULTI|"; poster labels keep the provider's spelling. **Trailers** play in an in-app player
(OK pauses, ◀ ▶ skip 10 s) that tries the next stored trailer when one is blocked, then offers to open
YouTube.

**Downloads** — Movies / Series / Recordings tabs with counts, the volume's name and free space, and
the folder each tab saves to; rows grouped Downloading / On this TV with picture, details, full file
path and live "64% · 12.4 MB/s · 3 min left"; actions appear on the focused row only. A Recordings card
shows the next scheduled recording.

**Search** — tabs All / Live TV / Movies / Series with counts; a channel result shows Live TV's own
preview pane, a film or series its poster panel. It is a show-only page: **OK goes to the item** in
its own category or page, and Back returns to Search with the query kept. Empty, it offers Jump to,
recent searches and Continue watching. An **On TV** group lists stored-guide programmes whose title
matches, on now or in the next 12 hours, and OK goes to the channel.

**More** — a glass section list with the profile row: Settings, Favourites, History, Backup &
Restore, Local sync, Error log and About, each an enterable page beside the list.

### 3.3 Multiple playlists

Merge every playlist into one browse, or narrow the whole app to one. The choice applies everywhere
at once and survives a restart; the **playlist pill** in the top-right switches it, listing each
playlist with its short mark (IPTV_GOLD → GOLD) and channel count. It is
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

The **Stage** interface (v5.1.0, designed in #227) replaced the old docked sidebar, top bar and card
screens entirely — there is no "classic" mode. Every screen sits on a full-width Stage page in the
**Plus Jakarta Sans** font, with one line-icon set, CSS-exact focus glows and a shared Stage popup kit.

- **Navigation rail** — Search, Home, Live TV, TV Guide, Movies, Series, Downloads and More, the
  wordmark at the top and the profile's avatar (or own picture) at the bottom; a **Now playing** item
  appears while something is docked or in Audio mode. Settings → Layout → **Navigation**: **Floating**
  (a capsule over the content that opens on ◀ and hides after 2 / 4 / 8 s) or **Docked** (always
  present, content reflows; recommended); **Size** Compact / Normal / Wide (+ counts and profile line)
  / Extra wide (+ a details line per item); **Length** Fit to items / Full height; **Widen on focus** (off by default)
  for Docked + Compact (off by default); **Menu items** Dynamic (follows the playlist) or Static (tick Home, Live TV,
  TV Guide, Movies, Series, Downloads; Search and More are never hideable). ▶ out of the rail always
  returns to the exact control left from.
- **Top-right cluster** — the **Continue** pill (Resume / Play / Next episode / Last channel), the
  playlist pill, and a large clock with date and weather in user-chosen colours. **Audio mode** rests
  as a glass pill in the Continue pill's place.
- **Panel widths** — named by the layout each screen uses: Separate layouts keep three shares
  (the third may be 0%), Live TV Stage sizes list + preview and the categories sheet on its own scale,
  Cinematic sizes the sheet and the details height. The Guide's two columns split independently.
  Theme is System / Dark / Light.

**Live TV layout** is **Stage** (categories as a sheet on ◀) or **Separate** (an always-visible
column). **Movies & Series layout** is shared by both sections: **Separate panels** — category column,
a grid (or list) and a glass details card (Poster 0% removes it) — or **Cinematic**, where the focused
title's backdrop fills the screen behind a **read-only** hero (title art, meta line, plot, cast) above
a vertical poster grid, with the categories as a sheet on ◀. Cinematic is grid-only and its hero takes
no focus: OK plays, holding OK opens the title menu. Art and details for titles already resolved this
session appear at once.

### 3.6 Categories, search and memory

Folder rails with Favorites and History per section; full category names, never abbreviated; a
category search box. **Customize** (per profile) hides, renames and reorders categories and
individual items, recovers hidden ones, filters to All/Visible/Hidden, and can be **PIN-locked**.
**Bulk rename** applies ordered prefix/suffix rules with automatic cleanup, a review step and a
restore-original undo. **Custom combined categories** gather items from anywhere. A category can also
be hidden, moved or restored to the playlist's default (hidden items, items moved out and custom
order are listed and undone together) straight from the browse screen by holding OK; **Restore
playlist defaults** in Customize's More menu does the same for the playlists shown in a section. All
customizations survive re-syncs. Removing a channel from a custom category is recorded so a sync cannot bring it back, and
hiding a provider category no longer empties custom categories. Custom categories also appear in the
player's channel list and the Multiview picker. A typed category search is kept while a channel plays;
Back in the list clears it.

Search covers Live, Movies and Series together (3.2). Live TV
reopens on the category you last used, with focus on the last channel. **App startup** is per
profile: Home, last channel, Live Favorites, or one chosen channel.

---

## 4. EPG / TV Guide

- **Full time × channel grid** (XMLTV), opening with the current time 3/8 across and two recent
  hours already loaded. The focused channel plays in the top video through Live TV's own preview
  player (no second decoder), with the programme under the cursor beside it and key hints at the
  corner; below, one control line — Today, Now, Category, Order, Search, Auto-match EPG — and a ruler
  with the now-line. The grid is also reachable as **Guide view** inside Live TV.
- Two-stage rows: focus selects the row, **OK** or **Right** steps into its programmes, **Back** steps
  out. Cells show recording / reminder / catch-up icons; overlapping programmes are drawn one after
  another; an empty stretch is a focusable "No program at this time" cell that Left/Right cross in
  30-minute steps. Every programme action (Remind me / Watch from start, Record, Watch channel…) is in
  the Hold-OK menu.
- Order by Provider, A–Z, Catch-up first or Favourites first; the category filter is Live TV's own
  list; *Show → Channels without guide* and *Show → Video preview* (off shows the channel's logo
  instead of playing it; kept in backups).
- **Programme reminders** — at the chosen lead time (5 min by default) OwnTV asks to switch, over any
  screen including the player, or switches, or only notifies.
- The grid reloads only once a guide sync finishes, instead of rebuilding every ~25 s during one.
- **Catch-up TV** — replay programmes that already aired, up to 7 days, seekable. **Live rewind**
  scrubs the live stream on archive-capable channels. **Go back to…** jumps straight to a time, with
  an exact day/hour/minute picker clamped to the archive window — and works with **no guide at all**.
  A **Catch-up category** in Live TV lists every channel that advertises an archive.
- **Pause and rewind live TV** (local timeshift, opt-in) — a channel without catch-up is saved on the
  device while it is watched full screen (15–60 min, always ≥ 1 GB free) and played from that copy, so it
  can be paused and rewound like an archive channel. The copy is the only provider connection; it is kept
  5 minutes after leaving (Resume / Go live on return — or always / never, per **Resume a saved
  channel**), deleted after 2 minutes on another channel, and
  wiped at every start. Built in core (`TimeshiftManager`, `LiveTuneController`), shared with the phone.
- **Auto-match EPG** links channels to guide data when `tvg-id` is missing or wrong, scanning only the
  playlist(s) the guide is showing (a 400-channel playlist in about 3 s); confident matches
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
appear in **Downloads → Recordings** with duration, size and full path. Padding (start early, keep
going after the end) and keeping one stream free are set in Watching & recording → Recording.

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

Offline downloads for films and episodes (never live channels) — singly or a whole season — with
pause, resume, retry, delete and play in an external player, shown on the Downloads screen (3.2) with
live speed and time left. Downloads continue when the app leaves the screen. The download folder is
the user's choice (USB volumes included), each kind in a fixed subfolder: Movies, Series, TV.
Removing the target USB stick marks that download failed rather than losing it silently.

---

## 8. Personalization & settings

**Settings is twelve group pages** under More → Settings, each opened from a card with its row count:
the group's rows on the left, and a glass **context panel** on the right with the focused row's
explanation, its CHOICES (the recommended one tagged) and key hints. Values sit right on each row —
switches, choices, steppers, segmented controls (◀ ▶), "N saved · Reset", accent swatches — with a
chevron only where a row opens another page; a list of choices or a number is edited **inside the
panel**, not in a popup. Larger editors (Playlists, EPG sources, Customize, Home screen, Proxy, DNS,
Panel and Guide widths, Long-press menus, Fonts & text size, Browsing & lists, Subtitle appearance,
Glass & background…) are full Stage pages with a breadcrumb. Returning from a sub-page restores focus
to the exact row. **Search all settings** matches setting names and shows path, title and value.
**Hold OK** on any row pins it to **Quick**; pins of player rows say "Pinned from <group>" and open
their group.

| Group | Holds |
|---|---|
| **Quick** | The user's pinned rows |
| **Profile** | Profiles, Add a profile, kids mode and PIN per profile |
| **Sources & guide** | Playlists, EPG sources · EPG time offset, Programme reminders, Reminder time, Guide days to keep, Catch-up time zone (+ per playlist) |
| **Appearance** | Theme, Accent color, Focus highlight, Glass & background, Ambient Glow (Dark + Glass off only), Fonts & text size, Popup size, UI zoom, Animations, Date, time & weather — with a live Home preview |
| **Layout** | Navigation, Live TV layout, Live TV opens in, Movies & Series layout, Panel widths, Guide column widths, Browsing & lists, Home screen, Long-press menus, Remote shortcuts |
| **Content & metadata** | Customize categories & items, Metadata (TMDB), OpenSubtitles |
| **Player** | Live TV / Movies & Series player (+ per playlist), saved choices, learned stream fixes, External player · Film buffer, Network timeout, Reconnect attempts |
| **Picture** | Hardware decoding, HDR (mpv only), Maximum video quality, Tunneled playback, Default zoom, saved zoom · Auto frame rate, Pause during the switch, Match resolution |
| **Sound & subtitles** | Default volume, Surround, Dolby/DTS passthrough, Night mode, Volume leveling, Audio sync (+ saved) · Preferred audio / subtitle language, Subtitle appearance |
| **Live TV** | Channel numbers, Live preview, Preview audio, Pause and rewind live TV, Rewind length, Resume a saved channel, Left and right rewind · Live latency, Pre-buffer, Give up after (each + per playlist) |
| **Watching & recording** | Seek step, Live rewind step, Resume playback, Auto-play next episode · Multiview, Max tiles, Mini-player · Recording: keep one stream free, record what I'm watching, start early, keep going after the end |
| **App** | Language, App icon, Accent-colored logo, App startup, Check for updates (+ on startup) · Proxy, DNS · Measured stream stats, Detailed playback logging |

- **Appearance** — any accent colour (presets, hex, a saturation square and hue bar as separate focus
  stops, live re-tint, Cancel restores), a separate **focus highlight**, one font family for the app and
  popups (**Plus Jakarta Sans** by default), main and popup text sizes, popup size and UI zoom stepped
  in the row, and clock / date / weather colours.
- **App icon** — the flip-card icon in eight colours (Eggshell by default) or **Pixel**, the
  dot-matrix TV set, changing the app row banner, the launch screen (a 600 ms dot sweep on Android 12+,
  a still mark with Animations off) and every in-app logo. **Accent-colored logo** tints the play
  triangle. The lowercase **owntv** wordmark is #227's, by @m3th0d93.
- **Glass & background** — the background is **Stage** (soft accent light), **Picture** (a local or
  phone-sent image in a Sharp, Soft or Dark look, with Darken and Blur) or **Plain**, with an optional
  **Accent light**; **Glass** frosts the rail, sheets, menus and popups at the chosen opacity, with a
  live Live TV preview. The older per-surface glass controls are kept in core for the phone only.
- **Ambient Glow** — a separate radiance (optionally pulsing) for the solid interface, offered only
  with the explicit Dark theme while Glass is off.
- **Remote Shortcuts** — short and long presses of spare colour, number, channel and media keys
  mapped to 26 actions. Essential keys stay protected; the shipped CH+/− paging remains the default.
- **Stage defaults** are written once at the first start of v5.1.0 (core `applyStageDefaults`); a
  value the user already stored is kept.
- **Developer** (dev-tools builds only) lives under More, not Settings.
- **Error log** (More) — the last crash plus a readable history of playback failures, fallbacks and
  reports, with optional detailed tracing. A crash is written to disk as it happens, so it survives
  the process dying. Export writes `Download/owntv-playback-report.txt`.

---

## 9. Language & first run

A fresh installation opens with a **language selector before Get Started**. English plus 25 packaged
translations; further requested languages stay catalogue-only until they reach the reviewed
readiness threshold. Every wizard step shares one frame (lockup, step dots, glass panel, Back / Next,
key hints). The first run is welcome (which carries the language selector) → display (UI zoom and
font steppers, app icon picker, a sample row) → disclaimer → **Set up OwnTV** → profile → **add a
playlist** → the import → **All set**, whose **Add a TV guide** opens Settings → EPG sources → Add once
the shell is up. **Set up OwnTV** offers three routes, not two: create a profile, restore a backup
file, or **copy everything from another OwnTV device** over the local network — so replacing a box
does not mean finishing setup first and then finding Local sync in the menus. **Add a playlist** is
four cards — **Remote**, **Type it here**, **Import**, **Existing** — and Remote is what keeps an
Xtream password off the D-pad: it hands the form to a phone over Wi-Fi. The same Remote / Type it here
pages are reached from Settings → Playlists → Add later, so the choice is never a first-run-only
opportunity.

App language is independent of profiles and of the separate TMDB metadata language, and survives
restart and backup/restore. Locale-aware plurals, dates, times, numbers, RTL navigation, font
fallback and English fallback apply across the interface, notifications, launcher text, companion
pages, player messages and diagnostics — without changing playback or sync behaviour. A generated
locale catalogue, six resource domains, Hosted Weblate contribution paths, tooling tests and CI
checks protect placeholders, plural forms, formatting, overflow and release packaging.

---

## 10. Backup, sync & updates

**Backup & Restore** writes a single `.own` container: the backup data, the background picture and the
downloaded subtitle files. An optional **backup password** encrypts the whole container
(AES-256-GCM, PBKDF2); without one the file is unencrypted and every secret is omitted — source and
proxy passwords, Stalker identity, TMDB and OpenSubtitles credentials, PIN hashes. Restore accepts
`.own` and legacy `.json` — from a file on this TV or sent from a phone (**Remote**) — detected by
content rather than extension, and **merge-restore remaps
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
| Media | libmpv (FFmpeg) — `tv.own.owntv:libmpv`, OwnTV's own build (newest mpv + FFmpeg 9, monthly) · ExoPlayer/Media3 1.11.1 |
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
