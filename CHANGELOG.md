# Changelog

## v2.2.0 — 2026-06-14

- **Multiple EPG sources** — EPG is now its own thing: **Settings → EPG Sources** lets you add any
  number of XMLTV guide feeds (with **Edit · Delete · Re-sync**), and they merge into the TV Guide.
  Adding a playlist **auto-syncs its EPG** (Xtream `xmltv.php` / M3U `url-tvg`), and the new-source
  message now breaks down what was imported — e.g. *"40K channels · 100K movies · 30K series · 30K
  EPG synced"*. The Guide's manual download button is gone (EPG syncs on add); when there's no EPG it
  shows an **Add EPG** shortcut.
- **Match a channel to a guide manually** — when a channel doesn't auto-match the EPG, open it in the
  Live preview and press **Match EPG** to pick its guide channel (searchable). Saved per profile,
  survives re-syncs; the Guide grid and the now/next card both honor it.
- **"What's New" before updating** — the startup update card now opens the **full changelog** when you
  press *What's New*, matching the manual check — so both paths show what changed before you update.
- **Back up your settings too** — Backup & Restore gained an **App settings** section (theme, accent,
  UI zoom, all Video Player settings, HDR, live-preview, sort orders…), and your **EPG sources** are
  now included with the profiles & sources backup.
- **Aspect-ratio button in the player** — the player's zoom control now works in every mode (live,
  movies and series): **Fit · Fill/Crop · Stretch · Original · Force 16:9 · Force 4:3**. It resizes the
  video surface directly, so it works with the fast direct renderer too. (Fixes #4.)
- **D-pad is now strictly for navigation while watching live** — **D-pad Up/Down** move through the
  player controls (like Left/Right) instead of changing channels. Channel surfing stays on the
  dedicated **CH+ / CH−** keys. (No CH keys on your remote? Go back to the list to pick a channel.)
- **Picture-in-Picture for live TV** — the **PiP** button now works while watching a channel: dock it
  to a corner and keep browsing the app while it streams. **Selecting another channel updates the
  docked window in place**, and its expand button maximizes it again. (Fixes #6.)
- **Playlists show what's in them** — each row in **Settings → Playlists** now lists its **channel /
  movie / series counts** (e.g. *"40K channels · 100K movies · 30K series"*) instead of the old, stale
  "EPG not downloaded" note (EPG lives on its own screen now).

### 🛠️ Fixes

- **Favorites & history survive a re-sync** — content ids change every refresh, which used to orphan
  your data: the Favorites folder showed a count (e.g. *"(2)"*) but listed nothing. Favorites, watch
  history and resume positions now **re-attach to the refreshed content automatically** (and stale
  leftovers are cleaned up), so your starred channels/movies/series and recently-watched stay put —
  including across the refresh-on-startup.
- **Hiding a group now hides its channels everywhere** — hidden categories only dropped the rail
  folder before, so their channels still showed under **All Channels**, in search and in
  recently-watched (hiding the adult groups didn't actually hide the channels). Hidden groups' channels
  now drop out of those lists and counts too.
- **Plays more streams on weak boxes** — when a device's hardware decoder can't start a stream (some
  Fire TV Sticks reject otherwise-fine channels/VOD with *"playback error… unsupported format"*), the
  player now **retries that stream in software automatically** before showing an error — so you no
  longer have to turn off hardware decoding to watch those channels.
- **Movie backdrop no longer looks clipped** — the artwork in a movie's details pane now fills its
  banner cleanly instead of showing letterbox bars (or a thin sliver when only a poster was available).
  (Fixes #5.)
- **Simpler, crash-proof video** — the renderer picker (Smooth/Auto/**Quality**) is gone. The app now
  always uses the direct, *YouTube-style* decoder-to-surface path — the best quality (full native 4K,
  HDR handled by the panel) **and** the lightest on TV hardware. mpv's heavyweight GL renderer, which
  could hard-crash the whole app on some GPUs (e.g. an emulator's translated GL), is no longer a user
  option — it's kept only as the **automatic software-decode rescue**, and is skipped entirely on
  emulators (a clean "can't decode on this device" message shows instead).

## v2.1.0 — 2026-06-13

- **Channel up/down with the remote** — while watching a channel fullscreen, press **D-pad up/down**
  (or the **CH +/−** keys) to zap to the next/previous channel in the list you opened, with a brief
  "now watching" card — no need to go back to the category.
- **TV-friendly text entry** — focusing a text field (Add source, profile creation, dialogs) no
  longer pops the keyboard and traps you; it highlights like any control, **OK** opens the keyboard,
  **Back** closes it — so you can move straight to the Save button. (Fixes #3.)
- **Easier Fire TV install** — releases now also publish a stable `OwnTV.apk` so a fixed
  `…/releases/latest/download/OwnTV.apk` link always serves the newest signed build. Fire TV users
  can install via the **Downloader code `4308278`** (`aftv.news/4308278`); README has full
  sideload instructions.

## v2.0.1 — 2026-06-14

Playback polish and fixes from real-TV testing on top of v2.0.0.

- **Keep the screen awake while watching** — the TV screensaver no longer kicks in during playback
  (live, movies or series); it returns to normal when you pause or stop.
- **Renderer modes** — the renderer picker (Settings → Video Player) now offers **Smooth** (default —
  the direct, TV-optimized path), **Auto** (picks per device), and **Quality** (the full mpv GL
  renderer — heavier on weak TVs). Each option shows a one-line hint.
- **Recovers from a busy decoder** — a stream that doesn't start (e.g. the hardware decoder is still
  busy right after a TV cold-boot) is now retried automatically a few times before any error shows,
  instead of getting stuck. A transient hiccup no longer drops you to the slower renderer for the
  rest of the session.
- **Smoother subtitles, quieter logs** — the app-drawn subtitle overlay is fed more efficiently
  (no more constant background polling).

## v2.0.0 — 2026-06-13

This update delivers the complete, long-term vision for the app. I’ve been working on this feature set for a long time! My original goal was to launch with everything ready, but I decided to get the core IPTV features into your hands early so we could catch and fix any bugs first. Now, the full roadmap is finally here. This update brings you content customization, a smarter guide, resume & complete backup, in-app updates, custom accent colors, and a top-to-bottom D-pad navigation overhaul, plus all the bug fixes from the last update.

### ✨ New features

- **Playlist-order sorting** — sync now preserves your provider's original order (channels, movies,
  series, and category/group order). Each section (Live TV / Movies / Series) has a sort chip next to
  the search bar to toggle **Playlist/Provider order ↔ A–Z**, remembered per section. Live TV defaults
  to playlist order. *(Re-sync a source once to pick up the stored order.)*
- **Full category names** — the category rail expands when focused (like the sidebar) and shows full
  names; Favorites/History show icon + label.
- **Content customization (per profile, survives re-syncs)**
  - Hide, rename, and reorder **categories** in Live TV / Movies / Series (Settings → Customize).
  - Hide and rename **channels** straight from the Live preview pane.
  - Hidden-channels list (top of Settings → Customize) to unhide.
  - Hidden channels disappear everywhere: lists, folders, favorites, section & global search,
    recently watched, and the EPG guide.
- **Custom EPG URL per source** — for **Xtream and M3U**; your own XMLTV link overrides the defaults
  (Xtream `xmltv.php` / M3U `url-tvg`).
- **Tune from the Guide** — OK on a channel name tunes straight to it; programme details have a
  **Watch channel** button.
- **Guide search** — a search bar in the Guide filters channels across the *whole* guide (not just
  the visible rows).
- **Guide lists every channel** — rows load their programmes lazily as they scroll into view, so the
  guide shows your full lineup (no more 300-channel cap) with flat memory use.
- **Resume, your way** — replaying a movie/episode with a saved position now shows a small
  *"Resume at 23:45?"* prompt (Resume / Start over). A new **Resume playback** setting in Video Player
  settings picks the behavior: **Always resume · Ask to resume (default) · Never resume**.
- **In-app updates** — OwnTV updates itself straight from GitHub Releases: automatic check shortly
  after launch (toggleable via **Settings → Check updates on startup**), or manually via
  **Settings → Check for updates**. The startup check shows a small **top-right status card**
  ("Checking… / You're up to date", auto-hides) that stays with *Update now / Later* when a release
  is newer; the manual dialog shows the **full changelog**. Updating downloads the APK with progress
  and hands it to the system installer — no storage permission needed (the APK stays in app-private
  storage).
- **Custom accent colors** — the accent picker grew from 5 presets into a full **palette + hex code**
  input (e.g. `#52DBC8`); the whole Material theme is generated from your color.
- **Simpler Settings** — the Personalization sub-menu was dissolved: **Theme** (picker), **Accent
  color** and **UI Zoom** now live directly under Appearance (avatars are edited per profile in
  Profiles).
- **Selective backup & restore** — exporting asks *what* to include (profiles & sources,
  customizations, favorites, history, resume positions — or everything), and restoring shows the
  file's contents and lets you pick which parts to apply.
- **Restore on first launch** — setup now starts with a choice: create a new profile, or **restore
  everything from a backup file** (profiles included) without creating a throwaway profile first.
- **TV-style search bars** — focusing a search bar no longer opens the keyboard; it highlights like
  any control and the keyboard opens on **OK** (applies to Live/Movies/Series, the Guide and global
  Search).
- **About screen** — Settings gained a proper About dialog (version, license, author, project link);
  the old "Star on GitHub" / "Report a bug" browser links were removed (TV browsers are no place to
  send people).
- **EPG status** — the Guide shows *"Guide loaded: N channels · M programmes"*; each source row in
  Settings shows its EPG state (✓ + count, or "not downloaded").
- **Complete backup** — Backup & Restore now covers *everything*: profiles, playlists/sources,
  customizations, **favorites, watch history, and resume positions**. Favorites/history/resume
  re-attach automatically once the restored sources finish syncing (episode data attaches when you
  open the show).

### 🛠️ Fixes & stability

- **Runs properly on real TVs** — a top-to-bottom playback overhaul for TV-class hardware:
  - **Direct-to-display rendering**: on TV devices the hardware decoder now writes frames straight
    to the screen (the same zero-copy pipeline YouTube/Netflix use) — smooth 4K HDR with the TV's
    own native HDR handling, faster channel starts, and a far lighter memory footprint. Text
    subtitles are drawn by the app Netflix-style; a **Renderer** setting (Auto / Quality) can force
    mpv's full GL renderer (complete ASS/PGS subtitle styling + zoom modes) on devices that can
    afford it, and the app falls back to it automatically where direct rendering isn't available.
  - The player's memory scales to the device (the old emulator-tuned 256 MB stream buffer
    OOM-killed budget 4K TVs): lean buffers and cheaper framebuffers on low-RAM devices.
  - A **decode watchdog** stops playback with a clear message if a 4K/8K stream would fall back to
    software decoding (which overloads TV chips).
  - The image cache is capped, going to the background releases the stream immediately, and the
    app sheds caches when the system signals memory pressure instead of getting killed.
- **No more freezes (ANRs)** — all player commands run off the UI thread; a stalling stream can no
  longer lock up the remote. Fast preview-scrolling coalesces loads (only the channel you land on is
  opened).
- **Blank player fixed** — preview → fullscreen now **reuses the running stream** instead of
  reconnecting (no overlapping connections, which tripped strict 1-connection providers with
  HTTP 509). The transition is seamless now, too.
- **Live-drop recovery** — temporary provider errors (e.g. connection-limit responses right after a
  channel switch) are now retried at the network layer and usually ride over invisibly; if a live
  stream still dies, the player shows the buffering spinner and auto-reconnects, and only then a
  proper error + Retry — never a silent black screen.
- **Guide fixes** — the grid now picks only channels that actually have programmes (was scanning the
  first 300 by number) with case-insensitive EPG-id matching (fixed "guide loaded but empty"); Back
  in the Guide no longer blocks exiting the app.
- **Episode resume actually works now** — resume positions for series episodes were read on play but
  never saved; episodes now save progress every 10s like movies (and track prev/next in the queue).
- **Crash fixed** when hiding a live channel (Paging re-collection).
- **Profile PIN locks can now be removed** — the profile editor gained a *Remove PIN lock* toggle
  (previously a blank PIN field just kept the old PIN forever).
- **Restoring a backup keeps you in Backup & Restore** — it no longer bounced the app back to the
  Settings menu mid-restore (the profile swap briefly emptied the profile list, which reset the UI).
- **Category rail performance** — virtualized list + overlay expansion: buttery smooth with hundreds
  of categories (the channel grid is no longer re-laid-out during the animation).
- **Layout fixes** — the Movies download button no longer stretches; preview-pane buttons reflowed;
  the sort chip matches the search bar height.
- **Focus fixes** — rename dialogs focus their text field; the source edit form focuses the Name
  field; Settings → Sources restores focus after add / edit / re-sync / failed import.
- **D-pad navigation fixed everywhere** — moving between panels no longer lands on whatever happens
  to be horizontally aligned: entering the category rail always lands on the **selected folder**,
  entering the sidebar lands on the **current section**, entering a content pane lands on the
  **last-focused (or first) item — never the search bar**, every Settings sub-screen opens on its
  first control, and closing any dialog returns focus to the row that opened it. Returning from
  playback puts focus back on the **exact item you played** — the channel row in the Guide, the
  episode in a show, the poster in Movies/Series, the row in Downloads.

---

## v1.0.0 — First public release

Native Android TV IPTV **player** (bring your own M3U / Xtream sources):

- Live TV, Movies, Series with folder rail, favorites, history, and per-folder + global search
- Full **EPG guide** (time × channel grid) + now/next in the Live preview
- **libmpv (FFmpeg)** playback — plays nearly anything, full audio/subtitle track support, custom TV
  HUD, mini-player/PiP, HDR passthrough
- Multiple **profiles** with PIN lock & kids flag; sources shareable between profiles
- Offline **downloads** for movies & episodes
- **Backup & Restore** (profiles + sources), per-source User-Agent, refresh-on-startup,
  default source
- Material 3 design (AMOLED dark / light), accent colors, UI zoom, avatars
- Scales to huge playlists (tested ~64k channels / ~169k movies)
