# OwnTV — User Guide

Everything OwnTV can do, **where to find it**, and the remote shortcuts that make it fast.

Each entry is one line about what it does and one line about where it lives. Nothing here is
required reading — skim the headings and stop where something looks useful.

> **The basics:** **D-pad** moves · **OK** selects · **Back** goes up a level.
> The left column is the navigation panel. **Long-press OK** on almost anything opens its menu —
> that is where favourites, rename, hide, record and catch-up live.

---

## 🚀 Start here

A fresh install walks you through this, in order. Nothing here is permanent — every choice can be
changed later in Settings.

```
Welcome  →  Text size  →  Disclaimer  →  Set up OwnTV  →  Profile
                                                            ↓
            Guide offer  ←  Import  ←  Playlist details  ←  Add a playlist
```

1. **Welcome** — the language picker is on this screen: one of 26, or keep **System default**.
   Press **Get started**.
2. **Text size** — set UI Zoom and Font size while a sample sentence resizes, so you judge it from
   your sofa, and pick the **App icon** colour. Changeable later in Settings → Look & Feel.
3. **Disclaimer** — OwnTV is a player; you bring the sources.
4. **Set up OwnTV** — three ways to begin: **New profile**, **Restore a backup**, or
   **From another device** (copy everything off a TV you already have — see below). A restore asks
   which parts of the backup to bring back before it applies anything, so you can take the playlists
   and leave the old box's settings behind.
5. **Profile** — name it, pick an avatar, and optionally make it a **Kids** profile or give it a PIN.
6. **Add a playlist** — **New**, **Existing** (only shown when another profile already has one), or
   **Import** a backup file. **Skip for now** is a valid answer; you can add one later from Settings.
7. **New** then asks **how** you want to enter it — **Remote** (fill it in on your phone) or
   **Manual** (type it with the remote). Then the form: M3U, Xtream or Stalker.
8. **Import** runs, and when it finishes OwnTV **offers to download the TV guide** in one press.

### 🔄 Already have OwnTV on another device?
**Where:** the **Set up OwnTV** screen → **From another device**
Copies your profiles, playlists, favourites, history and resume points straight over your Wi-Fi —
no account, no cloud, no file to find. On the device you already have, open **⋯ More → Local sync**
and turn **Sync mode** on; it stays on while that screen is open. The new television then finds it on
the network, you type its 6-digit PIN once, tick what to bring across, and see exactly what will
change before it changes. A television being set up only ever *receives* — nothing on the old device
is altered.

### 📱 Type the playlist on your phone instead
**Where:** step 7 → **Remote**
The TV shows a QR code and a 6-digit PIN. Scan it with a phone on the same Wi-Fi, fill the form
there — or upload an `.m3u` file straight from a computer — and press **Send to TV**. You still press
**Start Import** on the TV. Available from Settings later too, see
[Remote](#-remote--fill-the-form-on-your-phone). *(Idea from community PR #66 by @zarga03.)*

### ⏳ Don't wait for the import
**Where:** the import screen → **Run in background**
Enter the app immediately. A status pill at the bottom shows progress.

---

## 🧭 Getting around

```
┌──────────┬──────────────┬─────────────────────────────┐
│ Sidebar  │  Categories  │  Channels / posters         │  + preview pane
│ Home     │  Favorites   │                             │
│ Live TV  │  History     │                             │
│ Movies   │  Catch-up    │                             │
│ Series   │  All         │                             │
│ Guide    │  …folders…   │                             │
│ ⋯ More   │              │                             │
└──────────┴──────────────┴─────────────────────────────┘
      ← Left from a list jumps back to the column on its left
```

**⋯ More** is the last sidebar item: Settings, Favourites, History, Backup, Local sync, Error log and
About. Its right-hand panel *describes* each row before you open it — it never takes focus, so
**Right does nothing there on purpose**.

---

## 🎛️ Remote shortcuts — the cheat sheet

| Key | Where | What it does |
|---|---|---|
| **Long-press OK** | any channel, film or show | The options menu — favourite, rename, hide, record, catch-up, move, download |
| **Left** | full-screen live | Channel list for the folder you came from |
| **Left ×2** | full-screen live | All Live TV categories — switch folder without stopping the stream |
| **Right** | full-screen live | The last 30 channels you watched |
| **Left / Right** | full-screen live, a channel you can rewind (catch-up, or saved by **Pause and rewind live TV**), **Left and right rewind live TV** on | Rewind / go forward instead of the lists (the timeline comes up; hold to scrub) |
| **Left / Right** | film or episode, controls hidden | Skip back / forward by your seek step; the seek bar comes up, hold to keep scrubbing |
| **CH+ / CH−** | full-screen live | Next / previous channel. Wraps around. Always works |
| **Last channel** key | full-screen live | Back to the channel you watched before (press again to flip back). Also the clock button on the player bar |
| **Up / Down** | full-screen live, controls hidden | Same as CH+/CH− |
| **0–9** | full-screen live | Type a channel number to tune. OK submits, Back cancels |
| **CH+ / CH−** | any browse list | Page by 10 items. **Long-press** jumps to first/last |
| **Back** | full-screen player | Leaves full screen (there is no exit button) |
| **OK** | full-screen player | Shows / hides the controls |

**Want different keys?** **Settings → Content → Remote Shortcuts** maps spare colour, number, channel
and media keys to 26 actions — **Previous channel** among them. D-pad, Back, OK, volume, Home and power stay protected.

---

## 📥 Playlists & sources

### ➕ Add a playlist
**Where:** Settings → Manage sources → Add source
First choose **Remote** or **Manual** (below), then fill the form: **Xtream** (server + user +
password), **M3U** (URL or a local file), or **Stalker/Ministra** (portal URL + MAC).
Under **User-Agent** there is an optional **Referer** — leave it empty unless your provider asks
for one; it is then sent with every stream of that playlist.

### 📱 Remote — fill the form on your phone
**Where:** Settings → Manage sources → Add source → **Remote**
The same option the first-run wizard offers, and it stays available forever — you do not have to
type an Xtream password with the remote. The TV shows a QR code and a 6-digit PIN; scan it with any
phone on the same Wi-Fi, fill the form in the browser there, or upload an `.m3u` file from a
computer, and press **Send to TV**. The details land in the TV's form and you press **Start Import**.
*(Idea from community PR #66 by @zarga03.)*

### 🧪 Test connection
**Where:** inside the Add/Edit form, and on each saved playlist row
Says whether the account is active, when it expires, whether it's a trial, and how many connections
are in use. Nothing is imported — it only asks.

### ⚡ Choose what to sync
**Where:** while adding an Xtream or Stalker playlist
Per section: **Now** (import first), **Later** (background), **Off** (never fetched). Stalker
defaults to Live now, Movies/Series later, because Stalker VOD has no bulk endpoint.

### 🔄 Re-sync
**Where:** Settings → Manage sources → the playlist
**Resync now** adds and updates. **Resync and remove missing titles** also drops what the provider
has stopped listing. Neither touches your favourites, history, resume points or manual order.

### 🗂️ Several playlists at once
**Where:** the playlist chip in the top-right
Pick **All playlists** or just one — it applies everywhere at once and survives a restart. A
**DEFAULT** badge in Settings marks your default. When two are active, a small provider label appears
beside categories and items so you always know which is which.

### 📶 Prefer HLS (Xtream only)
**Where:** Add/Edit playlist
Ask the panel for HLS instead of MPEG-TS. Try it if live channels are unstable. **Test HLS support**
just above it checks whether your provider really serves it.

### 📡 Stalker portals
**Where:** Add source → Stalker (MAC)
Portal URL plus MAC. Stricter portals also take Serial Number, Device ID, Device ID2 and Signature
under **Advanced device identification**.
> **"Portal refused the login"?** Check the MAC, and check the TV's **date & time** — Stalker
> validates timestamps and a clock a few minutes out fails the handshake.

---

## 📺 Live TV

### ▶️ Watch a channel
**Where:** Live TV → focus a channel → **OK**
The preview pane plays it first; OK goes full screen with no reload.

### ⭐ Favourite, rename, hide
**Where:** long-press **OK** on a channel
The same menu also holds **Match EPG**, **Catch-up**, **Record**, **Move**, **Add to Multiview** and
**Play in external player**. A Favorites folder sits at the top of the category column.

### 🏷️ What the row tells you
Under each channel name: the programme on now, its real category, a genre colour dot, catch-up
availability and guide coverage.

### 🔧 Compatibility mode (swap the player engine)
**Where:** full-screen player → the **⇄ MPV/EXO** pill
If a channel stutters, shows artifacts or won't open, one press flips it to the other engine and
**remembers that channel's choice**. The pill always shows the engine actually playing.

### 🏛️ Which engine channels start on
**Where:** Settings → Video player → **Live TV player**
**ExoPlayer, then mpv** (default) · **mpv, then ExoPlayer** · **ExoPlayer only** · **mpv only**. The
"only" choices switch off the automatic handover, which costs a few seconds of black each time it
happens. **Live TV player per playlist** right below applies a different choice to one provider;
**Movies & Series player per playlist** does the same for films and episodes.

> **A channel that won't play is worked through every combination** — up to four: each engine on each
> stream format, each tried once. Then it stops and tells you, rather than spinning for ever.
> **Settings → Video player → Give up on a channel after** sets how long that may take (30 s default);
> **Give up after, per playlist** gives one provider its own time.

### ⏳ "Too many connections"
The spinner stays up with a live countdown and OwnTV retries by itself. Don't press Retry — just
wait.

### 🔒 Protected (DRM) channels
Widevine and ClearKey channels play with nothing to set up — your TV does the unlocking. They always
use ExoPlayer, so the engine toggle is hidden for them. Some cheaper boxes only allow standard
definition; that is the device's decision, not OwnTV's.

---

## 🗓️ TV Guide

### 📖 Open the guide
**Where:** sidebar → **Guide**
Opens scrolled to now, with an amber **NOW** marker about a third of the way across so the current
programme has context on its left. **Jump to Now** (top-right) returns after browsing.

### ➕ Add guide data
**Where:** Settings → EPG Sources
The guide is opt-in. After importing a playlist you are offered a one-tap sync. Later, add XMLTV
feeds here — **Fill from playlist** takes the URL your playlist already carries — and set each feed's
User-Agent and refresh interval. The refresh interval can also be **every N days**, the same choice a
playlist offers.

> **Playlist has no channel logos?** Turn on **Use this guide's channel logos** on the feed. Logos
> then come from the XMLTV guide instead; channels the feed has no logo for keep the playlist's one.

### 🎯 Match channels to the guide
**Where:** Guide → **Auto-match EPG**, or long-press a channel → **Match EPG**
Auto-match does the bulk; the manual picker lists the most similar guide channels first.

### 📅 Guide days to keep
**Where:** Settings → EPG Sources → **Guide days to keep**
How many days of upcoming guide the app stores, 1–14, seven by default. The same number decides how
much is downloaded, how much is kept, and how far the grid scrolls — so raising it gives you a longer
guide, and lowering it frees space. Old programmes are kept only on channels with catch-up, since
those are the only ones that can play them back.

### 🕰️ Guide time offset
**Where:** Settings → EPG → **Guide time offset** (global) · long-press a channel (just that one)
For a provider publishing its guide in another time zone. A re-sync never undoes it.

### 📋 Other guide touches
- **Sort** — A–Z · Provider · Live TV order · Catch-up first.
- **Cursor preview strip** — the programme under the cursor, without opening it.
- **Channel logos**, and a genre colour dot per channel.
- **↻ badge** — this programme can be replayed from the archive.
- **Long-press a channel label** to favourite it.
- **Settings → Layout → Guide Column Widths** — 10–90% split, must total 100%.

---

## ⏪ Catch-up & recording

### ▶️ Replay a past programme
**Where:** Guide → a past programme → **Watch from start**
Or long-press a channel → **Catch-up** and pick from the list. Up to 7 days, limited by what your
provider actually keeps.

### 🕐 Go back to a time
**Where:** full-screen player → **Go back to…**
Offers times counted back from now — `21:30`, `19:00`, `Sun 20:00`. The last row opens a
day/hour/minute picker. **This works even on channels with no guide at all.**

### ⏸️ Pause and rewind channels without catch-up
**Where:** **Settings → Playback → Video Player → Live TV → Pause and rewind live TV**
Off by default. While you watch a channel full screen, OwnTV saves it on this TV and plays it
from that copy, so it still looks live — and you can **pause**, rewind and go forward on channels
whose provider keeps no archive. Pause, the rewind/forward buttons, the bar, Left/Right and **Go live** work as on a catch-up channel. A dark stretch on the bar is a moment the connection
dropped; playback jumps over it.

- **Rewind length:** 15 (default), 30, 45 or 60 minutes. At least **1 GB** of the TV's storage always
  stays free; the oldest part goes first.
- **Back to the Live screen is not leaving: the preview is the same channel, so it keeps saving.**
- **The delete rules:**
  - Leave the channel and its copy is kept for **5 minutes**. Come back within them and OwnTV asks
    **Continue where you left off?** — **Resume** plays on from where you left, **Go live** jumps to now.
  - Watch another channel for **2 minutes** and the copy you left is deleted at once — you have moved on.
  - After 5 minutes it is deleted anyway, and every copy is deleted when OwnTV starts.
- Catch-up channels still rewind into the provider's archive. Protected (DRM) and encrypted channels
  play as before, without a copy.

### ⏭️ Catch-up plays on
A finished catch-up programme continues to the next one, and hands over to the live channel once you
catch up with the present. Controlled by **Settings → Video player → Auto-play next episode**.

### 🕰️ Two clocks
While replaying, the player shows **Programme time** (when it originally aired) next to **Current
time**, with a **Playing / Then** guide row.

### ⏺️ Record live TV
**Where:** long-press a programme in the **Guide**, or a channel in the **list**, or the **Record**
button in the player
A guide recording follows the programme's times; the others record from now. **Record every showing**
sets a standing rule for that programme on that channel. Recordings appear in
**Downloads → Live TV**.

> A recording costs one of your provider's connections and says so before it starts.

> **Copy-protected channels cannot be recorded.** Where a provider protects a channel with DRM, it
> can be watched but not saved — the protection is theirs, not OwnTV's. Asking to record one stops
> straight away and tells you why, rather than leaving a file that will not play.

---

## 🔲 Multiview — up to four channels

**Turn it on first:** Settings → Playback → **Video player** → **Multiview**. It is off by default, and
the button does not appear in the player until it is on. **Max tiles** on the same screen sets the
ceiling — the grid still opens with two and grows only when you ask.

**Where:** the **Multiview** button in the player, or long-press channels → **Add to Multiview**
The channel on screen becomes the first tile. **One tile carries the sound**, marked with a speaker;
tap another to move it. **Sound only** gives up a tile's picture and keeps its commentary.
**Back stops everything** — nothing is left playing behind it.

A tile that cannot start says why rather than sitting blank.

### 🔌 How many channels your provider allows
**Where:** Settings → Playlists → **Info**
OwnTV measures this once, at a playlist's first sync, and warns before refusing a stream. **Re-test**
is inside Info — it stops anything playing and takes a few minutes.

---

## 🎬 Movies & Series

### 🖼️ Grid or list
**Where:** the top-right button
Swaps the poster wall for a compact list. Inside a show, the same button swaps episode rows for
episode pictures.

### ▶️ Resume
Partly-watched titles offer **Resume**. **Settings → Resume** chooses **Ask**, **Auto** or **Never**.
A series **opens on your last-watched episode**.

### ⏭️ Auto-play next episode
**Where:** Settings → Video player → **Auto-play next episode**
Rolls into the next season too. In the last 30 seconds a card counts down, with **Play now** and
**Cancel**.

### ✅ Watched state
A ✓ once watched to 95%, a progress bar when part-way. Season chips show `8/18`. Long-press →
**Mark as watched / unwatched** to correct it by hand.

### ↕️ Sort & hide
**Sorting** (per show, per profile) flips seasons and episodes oldest/newest first. **Hide watched**
filters the episode list.

### 📥 Download or play elsewhere
**Where:** long-press a title
**Download** queues it immediately; **Play in external player** opens VLC, MX Player and so on, just
this once.

### 🔧 Which engine plays films
**Where:** Settings → Video player → **Movies & Series player**
Same four choices as Live TV, defaulting to **mpv, then ExoPlayer**. Note **ExoPlayer only cannot
play DTS or TrueHD** — those need mpv. The **⇄** pill in the player flips the current film and
remembers it.

---

## 🎬 TMDB metadata

### 🖼️ Posters, plots, cast, trailers
**Where:** Settings → **Metadata (TMDB)**
**Metadata source** picks *Provider only*, *Provider + TMDB* or *TMDB only*. **Language** sets the
language of plots and artwork — separate from the app's own language.

### 📊 Your daily share
The built-in service is shared by everyone using OwnTV, so it has minute/hour/day allowances shown at
the top of the page.

### 🔑 Use your own key instead — recommended
**Where:** Settings → Metadata → **Get advanced TMDB info via remote**
A free personal TMDB key removes the shared limit. It's 32 characters, so send it from your phone by
QR + PIN rather than typing it. A self-hosted Cloudflare Worker also works (one is in the repo).

### 🔎 Fix a wrong match
**Where:** long-press a film or show
**Set TMDB name** corrects the title used for lookups; **Refetch TMDB details** forces a fresh fetch;
**Hide** removes it everywhere; **Play Trailer** appears when TMDB has one.

---

## 🏠 Home

### 🔥 Now Trending
**Where:** the first Home row · Settings → Home screen → **Now Trending**
Current TMDB charts, filtered to titles your provider can actually play. Up to 10, refreshed every
five to eight days. Needs TMDB metadata turned on.

### ▶️ Continue Watching
Partly-watched films, episodes and recent channels, newest first. Hold focus on a card for 3 seconds
and it expands into a preview — switch that off with **Play video in the hero row**.

### ⏭️ The Continue chip
**Where:** the top bar, every screen
One press resumes the most recent thing — a film, the next episode, or your last channel.

### 🧩 Rearrange Home
**Where:** Settings → **Home screen** (per profile)
Reorder or hide rows, filter the hero, and switch channel rows between **Cards** and **On Now** (an
inline mini-guide).

### 🧭 Trim the sidebar
**Where:** Settings → **Sidebar Menu Customization**
**Dynamic** adapts the menu to what the active playlist actually contains; **Static** lets you toggle
each icon yourself.

---

## 🔎 Search · 🕐 History · 📥 Downloads

### 🔎 Search
**Where:** sidebar → Search
Searches Live, Movies and Series together. With the box empty it shows a **Jump to** row. The first
**Back** clears the query; a second leaves.

### 🕐 History
**Where:** ⋯ More → History
Long-press → **Remove from History** for one item, or **Clear** for all, by type.

### 📥 Downloads
**Where:** sidebar → Downloads
Grouped Active · Waiting · Completed · Failed, with a storage bar. Long-press for **Pause · Resume ·
Retry · Delete**. Downloads keep running when you leave the app.
> Removing the USB stick mid-download marks that download failed — reconnect it and retry.

---

## 👥 Profiles

**Where:** Settings → **Profiles**
Each profile has its own favourites, history, resume points and layout. Add a **PIN lock**, or turn on
**Kids mode** to hide adult folders, items and TMDB results everywhere.

### 🚀 Where a profile opens
**Where:** Settings → App → **App startup**
**Home**, **Last channel**, **Live Favorites**, or one chosen channel picked from a searchable list.

---

## 🎛️ The player

| Control | Where |
|---|---|
| Clock (and programme time on a replay) | top centre, always |
| Engine pill **⇄ MPV/EXO** | control bar |
| **Go back to…** | control bar, catch-up channels |
| ⓘ **Stream info** | right-most button — codec, resolution, HDR, bitrate, decoder, audio, buffer; on mpv also **Interlacing** (none, deinterlaced by the player or by the TV, or not deinterlaced) |
| **Record** | control bar, live channels, once enabled |
| **Multiview** | control bar, once enabled |
| **Quality** | control bar, beside Aspect — only when the stream offers several picture sizes; Auto or one size for what is playing now |
| 🌙 **Sleep timer** | control bar, beside Stream info — 15–90 min, end of programme (live), or end of movie / episode (the next episode then does not start); green while running. **Also turn off the screen** puts the TV to standby when it ends — Android asks for permission once |
| Exit | **Back** — there is no exit button |

- **Remote transport keys** work — play/pause, next, previous, from the remote, a headset or a voice
  assistant.
- **A notification won't pause your film** — the sound dips and comes back.
- **Mini-player** — subtitles show in it too. Get back to it from any screen via **Now Playing**.

### 🎧 Audio Mode — listen with the screen free
**Where:** the **headphones** button on the player controls
Drops the picture, keeps the sound, and leaves a slim bar you can navigate with the D-pad.
**Fullscreen** returns; **✕** stops. *(An item that has no picture at all — a radio channel — says so
by itself.)*

---

## 💬 Subtitles & audio

### 🔎 Search OpenSubtitles
**Where:** player → Subtitles → **Search OpenSubtitles**
Needs a free [opensubtitles.com](https://www.opensubtitles.com) account. Sign in at
**Settings → OpenSubtitles** — by remote (QR + PIN) or by typing.

### 📁 Use a file you already have
**Where:** player → Subtitles → **Select local subtitle file**
No account, no internet. Browses USB and internal storage.

### ⏱️ Fix subtitle timing
**Where:** player → ADJUST → **Subtitle timing**
Nudge earlier or later in 0.1 s and 0.5 s steps.

### 🗣️ Preferred languages
**Where:** Settings → Video player → **Preferred audio / subtitle language**
Picks the right track automatically when a stream carries several. Each **profile** has its own, and
the list has 50 languages. **Original language** (audio) plays a film or series in the language it
was made in, when the stream has that track; otherwise the stream's main track. A track or subtitle
you pick yourself is **remembered** for that channel, film or series (subtitles off too), per profile.

### 🎚️ A/V sync
**Where:** player → Audio → **A/V sync**
Nudge until lips match, then **Remember this delay** to keep it for that item. Works on both players,
including sound sent as-is to a soundbar.

### 🔊 Surround sound
**Where:** Settings → Video player → **Surround sound**
**Auto** (recommended — tries surround, falls back to stereo if your TV can't) · **Stereo only** (the
right answer for TV speakers) · **Surround** (send Dolby/DTS to a real receiver).

### 🌙 Night mode, Volume leveling and passthrough
**Where:** Settings → Video player → Sound
**Night mode** turns loud scenes down and quiet dialogue up; **Volume leveling** brings every channel
and film to the same loudness. Both are off by default and work on both engines, ExoPlayer and mpv.
**Dolby and DTS passthrough** (on) sends Dolby/DTS undecoded to the TV or
receiver on ExoPlayer; off, or while Night mode or leveling is on, OwnTV decodes it itself.

### 📺 Picture quality and tunneled playback
**Where:** Settings → Video player → Engine & picture
**Maximum video quality** caps the picture when a stream offers several sizes; the player's
**Quality** button picks one for what is playing now. **Tunneled playback** (experimental, off) only
appears on a TV that supports it, and switches itself off for good if a channel fails with it.

---

## 🎨 Make it yours

### 🎨 Theme & accent
**Where:** Settings → Appearance
Dark, AMOLED or Light, plus an accent colour with presets and a full picker. The accent reaches the
player too — seek bar, active buttons, badges.

### 🃏 App icon
**Where:** Settings → App → **App icon**
Eight colours for the OwnTV icon and logo: Petrol, Sunflower, Cobalt, Tomato, Station
Board, **Eggshell** (default), Olive and Olive on Cream. It changes the app row banner, the launch screen and every
OwnTV logo in the app. **Restart now** switches at once; **Later** switches when you next leave the
app. Some launchers take a moment to show the new banner.

### 🔦 Focus highlight
**Where:** Settings → **Focus highlight**
The colour and thickness of the ring around whatever is selected. Applies everywhere.

### 🪟 Glass Effect
**Where:** Settings → **Glass Effect**
A frosted look with a live preview and six presets from **Ultra Clear** to **Opaque**. Choose which
surfaces get it, and set your own background image (local file or URL).
> Real frost needs a background image and **Android 12+**. Without those, panels are simply tinted.
> **Surface transparency** sets how *solid* a surface is, so a lower number is more see-through.

### 🔤 Font & size
**Where:** Settings → Appearance → **Font customization**
Main text 60–140%, popup text and popup boxes independently, and a choice of bundled fonts.
> Below **85% zoom** OwnTV warns first — very small sizes draw many more items and can exhaust a
> low-memory TV.

### 🎬 Movies & Series layout
**Where:** Settings → Layout → **Movies & Series layout**
Two ways to browse films and shows. **Separate panels** (the default) is categories, the list and a
preview side by side, with the List/Grid toggle. **Cinematic** fills the screen with the focused
title's artwork and puts its details — title lettering, rating, quality badges, genres, plot and cast
photos — above a wide poster grid.
- Cinematic is **grid only**; the List/Grid toggle hides, and your List choice comes back if you
  switch layouts again.
- **Nothing in the details area takes focus.** **OK** on the focused poster plays or opens it, and
  **long-press** opens the usual menu. A half-watched film shows a green *Resume* badge and a sliver
  on its poster.
- Live TV and the episode list inside a show are unchanged either way.

### 📐 Panel widths
**Where:** Settings → **Panel Width Adjustment**
How wide the category rail, list and preview pane are, per section. The third panel can be **0%** to
hide it entirely. Each section must total 100%.
> With **Cinematic** on, Movies and Series have two columns instead of three: the second slider
> becomes **Content area** and moves opposite the first, and the third becomes **Details height** —
> shown *below* the Total size line, because it is a height and is not part of that 100%. Lower it
> for another row of posters, raise it for more plot and cast. Set it to **0%** for posters only.

### 🗂️ Categories & items
**Where:** long-press a category → **Customize**, or Settings → **Customize Categories & Items**
Hide, rename, reorder, and **unhide**. Highlights:
- **Bulk rename** — add or strip a prefix/suffix across a whole category, with **Auto cleanup** for
  country, quality and codec tags, a review step, and **Restore original names** as the undo.
- **Custom combined categories** — **＋ New category**, then **Move to category…** from any item.
- **Span select** — long-press a **Hide** or **move** button to act on a whole block at once.
- **PIN lock** this screen with **Set PIN**.

### 🔀 Reorder the long-press menus
**Where:** Settings → Layout → **Long-press menus**
Put the actions you use most at the top.

### 🎞️ Animations
**Where:** Settings → **Animations**
Turn interface motion off for a snappier feel on a slower box.

---

## ⚙️ Settings worth knowing

Settings is two columns — sections on the left, their settings on the right. **Quick** at the top is
yours to build: **hold OK on any setting** and choose **Pin to Quick**, then Move it up or down. The
**search pill** searches every setting at once, and a result you can simply switch on or off flips
right there in the list.

| Setting | Where | Why |
|---|---|---|
| **Multiview** | Video player | Watch up to four channels at once. Off by default; also sets the tile ceiling |
| **Live latency** | Video player → Live TV | How close to the live edge to play. Lower = less delay, more stutter risk |
| **Pre-buffer live streams** | Video player → Live TV | Collect a few seconds first on a flaky provider |
| **Give up on a channel after** | Video player → Live TV | 30 s default — bounds how long a dead channel can spin (+ per playlist) |
| **Catch-up time zone per playlist** | Sources & guide | One provider's archive on a different clock (quarter-hour zones such as +05:30 included) |
| **Hardware decoder** | Video player | On for smooth 4K; off only to diagnose |
| **HDR (mpv only)** | Video player | Use HDR output when the video and TV support it. ExoPlayer always passes HDR through |
| **Auto frame rate** | Video player | Match the TV's refresh rate to the content. Off by default |
| **Pause during the display switch** · **Match resolution** | Video player, under Auto frame rate | Films only: hold the film while the TV re-syncs (off–5 s); also switch to the film's resolution, never above the TV's own |
| **Seek step / Live rewind step** | Video player | How far the skip buttons jump |
| **Left and right rewind live TV** | Video player → Live TV | Off by default — Left/Right open the channel list and history |
| **Film buffer** · **Network timeout** · **Reconnect attempts** | Video player | Films, episodes and catch-up on a bad line. Auto / 1 = as before |
| **Default volume** | Video player | 0–150%, where everything starts |
| **Reset saved player choices / zoom / volume** | Video player | Forget everything the player remembered per item |
| **Reset saved live TV player choices** | Video player | Every channel follows the Live TV player setting again |
| **Forget learned stream fixes** | Video player | For a provider that has fixed its streams. Catch-up lessons also expire after 14 days |
| **Custom DNS** | Network → DNS | System, Google, Cloudflare, Quad9, or DNS-over-HTTPS |
| **External player** | Video player | Separate switches for Live TV, Movies and Series |
| **Weather** | Appearance → Weather | The top-bar chip, with a custom location and °C/°F |
| **Check updates on startup** | App | Be told when a newer version is on GitHub |
| **Error log** | App, last row | The last crash and recent playback failures — exportable |
| **Detailed playback logging** | Video player → Diagnostics | Turn on before reproducing a playback bug |

### 💾 Backup & Restore
**Where:** ⋯ More → **Backup & Restore**
One `.own` file with your profiles, sources, every setting, your background image and downloaded
subtitles — **optionally encrypted with your own password**. Restore locally or over Wi-Fi.
> Android's own automatic backup is deliberately disabled, so app data only ever leaves the device
> through this screen. Passwords ride along **only** if you set a backup password.

### 🔄 Local sync with your phone
**Where:** ⋯ More → **Local sync**
Swap favourites, history and resume points with the OwnTV mobile app over your own Wi-Fi — no
account, no cloud. **Both devices must open the Local sync screen**; you see exactly what will change
before it changes. On a brand-new television this is offered during setup instead — see
**From another device** under *Start here*.

---

## 🛠️ Build your own M3U playlist

OwnTV sorts each entry by its tags:

```
tagged series  →  Series tab
tagged vod/movie  →  Movies grid
no VOD tag  →  Live TV
```

### One line, explained

```m3u
#EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="http://…/bbc1.png" group-title="UK" ,BBC One
http://your-provider/live/user/pass/1234.ts
```

- **`group-title`** — the category inside the tab.
- **`tvg-logo`** — the logo or poster (optional).
- **`tvg-id`** — the EPG id used to match guide data (Live TV).
- **after the comma** — the title shown in the app.

### Movies

```m3u
#EXTINF:-1 type="movie" group-title="Action" tvg-logo="…/poster.jpg" ,Mad Max
http://your-provider/movie/user/pass/555.mkv
```

### Series — the one that matters

```m3u
#EXTINF:-1 type="series" group-title="Drama" ,Breaking Bad S01E01 Pilot
#EXTINF:-1 type="series" group-title="Drama" ,Breaking Bad S01E02 Cat's in the Bag
```

The text **before** the marker becomes the show name; the text **after** it becomes the episode
title. Markers: `S01E05` (also `s1e5`, `S01 E05`, `S01.E05`, `S01-E05`) and `1x05`.

> **Keep the show name identical across its episodes** — that is what groups them into one show.
> A line with no marker is still added, as a plain sequential episode.

---

## 🩺 Troubleshooting

| Problem | Try this |
|---|---|
| A channel stutters or shows artifacts on 4K | The **⇄ MPV/EXO** pill — compatibility mode fixes most of these |
| One provider glitches, another is fine | Turn on **Prefer HLS** for that playlist, or set **Live TV player per playlist** |
| Channel won't open at all | It already tried all four combinations. Check **Error log** (Settings → App) |
| "Too many connections" | Wait — OwnTV counts down and retries by itself. Don't press Retry |
| Sound but a black picture | OwnTV switches engines by itself; if neither can, it says so |
| Stalker portal refuses the login | Re-check the MAC, and the TV's **date & time** |
| Guide is blank | **Settings → EPG** — add a feed and sync it, then run **Auto-match EPG** |
| Audio out of sync | Player → Audio → **A/V sync**, then **Remember this delay** |
| Update says there isn't enough space | In-app updates need room for the whole APK — free some and retry |

**Still stuck?** **Settings → App → Error log** holds the last crash and recent playback failures, and
exports to `Download/owntv-playback-report.txt`. Bring it to
[t.me/owntvplayer](https://t.me/owntvplayer).

---

## 💡 Tips

- **Long-press OK** is the answer to "where is that option?" nine times out of ten.
- **Left, then Left again** in full screen gets you from channel list to every category without
  stopping the stream.
- Set a **default playlist** if you mostly use one — everything narrows to it at once.
- Turn on **Run in background** during a big import and just start watching.
- A **free personal TMDB key** removes the shared metadata limit; send it from your phone by QR.
