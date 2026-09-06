# OwnTV — App Changelog (minimal)

> Short release notes shown inside the app's update dialog: two parts per version — New features
> (by name) and Fixes. The full, detailed changelog is [CHANGELOG.md](CHANGELOG.md). Hand-maintained —
> edit this file directly alongside CHANGELOG.md, condensing per version (do not copy bullets verbatim).
>
> **Rule: bullet points only — no descriptions.** Each line is a short bolded feature/fix title and
> nothing more. The ONLY extra detail ever allowed is a contribution credit for community work
> (e.g. `(community PR #40 by @codeVerine)`). Issue numbers that are part of a title (e.g. `(#57)`) are
> fine; explanatory parentheticals are not. Descriptions belong in CHANGELOG.md, never here.

## v4.2.5 — unreleased

### ✨ New features

- **🧪 Test a playlist before you trust it**
- **🔄 Auto refresh: choose your own number of days**
- **🪟 A new Glass Effect look: Aurora**
- **🖼️ Trending layout choice: detailed card or posters only**

### 🐛 Fixes

- **Live TV's catch-up list now reaches back a full week**
- **The guide refreshes itself after adding, re-syncing or deleting an EPG source**
- **The guide line under a channel's name no longer goes blank after updating the app**
- **Guide auto-match now works for non-Latin channel names** (community PR by @Sekator778)
- **⚡ Guide auto-match finishes in seconds instead of minutes**
- **Blank poster tiles now show the poster OwnTV already found**

## v4.2.4 — 2026-08-27

### ✨ New features

- **🩺 A crash now leaves a report you can send**
- **⚡ Playlists import and refresh faster**
- **🚀 OwnTV opens faster**

### 🐛 Fixes

- **Live TV no longer crashes in full screen with Animations turned off**
- **The long-press menu editor can be scrolled**
- **An absurd catch-up length no longer breaks tuning**
- **Stopping a stream during a retry no longer crashes the player**
- **A refusing media session no longer takes playback with it**
- **Long-press menu Settings shows the saved order**
- **Home resume no longer trips working Auto surround into stereo or mpv**

## v4.2.3 — 2026-08-26

### ✨ New features

- **🏷️ Every merged library says which provider an item came from**
- **🔎 Customize can show All, Visible or Hidden entries**
- **🎛️ Spare remote buttons can do exactly what you choose**
- **🕰️ The Guide keeps the present in view**
- **↔️ Choose how much of Guide belongs to channels and programmes**
- **🪟 Popup boxes and popup text finally size independently**
- **✨ Glass Effect is now a complete settings screen**
- **🪟 The corner player carries one floating strip of buttons**
- **🎵 The music bar opens up when you move to it**
- **✨ Focus that reads like light, and icons that stop sharing**
- **🗂️ Settings is a spine and a sheet, with your quick switches at the top of it**
- **🔀 Arrange the long-press menus yourself**
- **▶️ The player's transport buttons sit in one pill**
- **🪟 You can reach the docked mini player again**
- **💬 A separate subtitle size for each of the two players**
- **⏱️ A dead channel says so, instead of leaving you with a black screen**
- **🎛️ Every playback setting in one place**
- **🎚️ Per-playlist Live TV player and Live latency**
- **🎧 Remember the audio sync for one channel or one film**
- **🏠 Turn off the video on the Home screen's big card**
- **⏭️ Catch-up plays on to the next programme**
- **🪟 Mini player is one popup instead of three screens**
- **🏷️ The player's buttons tell you what they are**
- **🎨 The player follows your accent colour**
- **⚡ Settings opens faster, and catch-up stops redrawing the screen**
- **🗂️ Settings is two columns, and grouped the way you'd look for things**
- **🎨 Icons that match what they do**

### 🐛 Fixes

- **Entering a Guide programme row keeps the current time in place**
- **Embedded TMDB trailers run with less interface overhead**
- **Backup & Restore preserves every Settings arrangement and global player choice**
- **CH+ / CH− always changes the channel while the Live TV or catch-up player is open**
- **Focus returns directly to the Settings row you left**
- **Home Trending no longer follows popup size and popup font controls**
- **Two settings put the highlight in the wrong place afterwards**
- **Back now goes back one level in Settings, not two**
- **Every series re-sync used to leave a dead resume position behind, for ever**
- **Removing a series from Continue watching now removes it from the Home screen too**
- **The "watch next episode" box no longer pops up for the wrong episode**
- **A deleted profile's favorites can no longer surface in someone else's account**
- **Rewinding a channel with no recording available now returns to live**
- **Choosing a category right after typing a channel number sticks**
- **Your audio sync survives a stream reconnecting**
- **The offline warning reads the connection you are actually using**
- **Nothing from the previous channel or film can survive into the next one**
- **Auto frame rate now respects your TV's own "Match content frame rate" setting**
- **Live latency now admits what a 4K channel can actually buffer**
- **A film with picture-based subtitles is less likely to be given up on early**
- **ExoPlayer no longer drops a working movie or episode into mpv after switching surround to stereo**
- **"App not installed" when updating from inside OwnTV**
- **An update that the system refuses now says why**
- **The first-run "Add a playlist" screen no longer hides Stalker**
- **Fast-forward and rewind from a Bluetooth remote or the system media notification now use your Seek step**
- **A frozen live channel moves to the other player about twice as fast**
- **The offline warning now appears when the internet is actually unreachable**
- **Online subtitle searching now filters correctly for seven more languages**
- **Track menus stop hunting for tracks that will never arrive**
- **The in-player channel list no longer mislabels itself**
- **One less timer running while you watch a recording**
- **Less background work during playback, and less of it on weaker TVs**

## v4.2.2 — 2026-08-19

### ✨ New features

- **🖼️ Episode grid — see a picture for every episode**
- **⚡ A whole show's episode details now arrive in one request**
- **🎯 Choose the colour and thickness of the focus highlight (#121)**
- **👶 Kids profiles hide adult content across OwnTV**
- **🚀 Start OwnTV on a searchable, profile-specific Live TV channel**

- **⏪ Catch-up without a TV guide — a Catch-up category and "Go back to…"**
- **🕐 A clock in the player — and, on catch-up, the time the programme actually aired**
- **🧾 Dedicated Metadata and OpenSubtitles settings**
- **📱 Send TMDB or OpenSubtitles access from another device**
- **🔑 Sign in to OpenSubtitles from another device, on one screen**
- **🔐 Your data no longer leaves the TV without a backup password**
- **🗃️ Backup & Restore now really does back up everything**
- **🔤 Subtitle font selection and Monospace**
- **🎛️ Choose the playback engine — four options, for Live TV and for Movies & Series**
- **🔒 Protected (DRM) channels now play — Widevine and ClearKey (#115)**
- **📁 Send a playlist file from your computer with Remote**

### 🐛 Fixes

- **The advanced TMDB setting is no longer labelled “via remote”**
- **Episode pictures no longer reload when returning to a season**
- **Back from a show returns focus to that show, not the category sidebar**
- **OpenSubtitles errors no longer blame your internet connection**
- **OpenSubtitles sign-in now works on IPv6-advertising networks**
- **Remote companion is no longer described as phone-only**
- **Normal profiles no longer lose adult provider or TMDB results**

- **Restoring a backup gave every Stalker playlist the same MAC address (#114)**
- **DNS choice now survives an app restart**
- **“Audio only” no longer flashes on ordinary TV channels**
- **Ambient Glow now stays dark-mode only and hides its ring when Pulse is off**
- **Preferred subtitle language now turns matching subtitles on automatically**
- **"Watch from start" needed two presses**
- **Per-channel and per-item playback settings attached to the wrong playlist after a restore**
- **Restoring onto a device that already had the playlist discarded the backup's settings for it**
- **Downloaded subtitles are now told apart, and subtitle timing changes the one you selected**
- **The default playlist could be repointed at an unrelated playlist**
- **Startup screen and the Customize PIN lock were filed under "Sources"**
- **Turning "Advanced options" off now actually stops using your own key**

## v4.2.1 — 2026-08-15

### ✨ New features

- **🎭 Cast photos in TMDB details**
- **🏷️ A fair daily share of the built-in metadata service**
- **🎧 "Audio only" — sound with no picture is now labelled, not mistaken for a fault**
- **🔊 Zoom and volume are now remembered per item**
- **Seek step**
- **Live rewind step**
- **Deinterlacing**

### 🐛 Fixes

- **Your resume position is saved reliably again — and never lands in someone else's profile**
- **A dying live channel now ends with a message instead of reconnecting forever**
- **Channels that need a custom User-Agent or Referer survive Retry and the screensaver**
- **Live rewind no longer leaves a phantom "behind live" counter running**
- **The Home screen poster no longer sticks on a spinner**
- **Catch-up you watch inside OwnTV now appears in History**
- **Opening a channel full screen from a catch-up programme keeps CH+/CH− working**
- **Cancelling Move mode restores your sort order**
- **Retrying a live channel keeps everything the first attempt had**
- **Subtitle fixes**
- **"Stay signed in" now covers subtitle downloads**
- **A subtitle search that fails now falls back to a title search**
- **Catch-up URLs build correctly for playlists that already carry a token**
- **Auto frame rate no longer leaves the previous item's refresh rate on the display**
- **The playback error log survives a corrupt file**
- **Audio focus is released while you are paused**
- **Settings → Audio sync now covers the same ±5 seconds the player does**
- **Custom DNS resolves IPv6-only hosts**
- **One channel refusing a request no longer sends a whole provider down the slow path**
- **Holding CH+ or CH− changes channel once**
- **Subtitles stay on the picture when you zoom**
- **A catch-up programme that never opens moves on instead of spinning**
- **The "Match EPG" picker responds as you type**
- **Audio sync moves in 25 ms steps**
- **The volume dialog no longer traps the remote at 0%**
- **Rename, move and delete in Customize return focus to the row you were on**
- **A film or recorded programme that won't start on the hardware decoder recovers more reliably**
- **Smaller player fixes**

## v4.2.0 — 2026-08-12

### ✨ New features

- **🫧 Complete interface and Glass Effect upgrade**
- **✨ Unified browse panels and compact navigation rail**
- **🌟 Optional Ambient Glow for the solid interface**
- **🔤 Separate interface and popup fonts**
- **🔎 Adjustable app text size**
- **💾 Font settings in backup and restore**
- **🌍 OwnTV in 24 languages (community PR #108 by @codeVerine)**
- **✨ Redesigned first-run setup with language choice**
- **🔥 Now Trending on Home — provider-playable TMDB trends**
- **🔐 Advanced Stalker device identification**
- **🧪 Test HLS support — find out whether your provider really serves HLS before you turn it on**
- **📐 Panel Width Adjustment can hide the preview or poster panel completely**
- **⚡ Faster cold start**
- **🧱 Refreshed playback and networking libraries**
- **♻️ Reset saved player choices**

### 🐛 Fixes

- **Your Movies & Series player choice is no longer overridden after a few failed streams**
- **Switching audio language mid-playback no longer makes the sound stutter**
- **The stream information overlay now says correctly when your TV is decoding the audio**
- **TMDB trailers no longer stutter**
- **The Home hero preview stops while a trailer is playing**
- **Stalker imports no longer lose a whole category when the portal drops the connection**
- **Glass Effect focus no longer leaves dark trails during rapid navigation**
- **Focused controls no longer paint a second rounded layer**
- **Now Trending has a dedicated toggle and fixed top position**
- **Guide focus can now move into the docked mini-player (#112)**
- **Stereo-fallback notices no longer remain over the next video**
- **Surround sound no longer switches itself off because of a small timing gap inside a file**
- **The stream information overlay now names the decoder that is actually running**
- **Language changes keep focus on the selected language**
- **Posters and artwork load when a TV's IPv6 route is broken**
- **Test DNS no longer succeeds through the TV's normal DNS (#111)**
- **The top-bar Search and playlist controls are compact again**
- **A channel that is briefly full now waits and starts by itself, instead of dropping you on the error screen**
- **Live channels that switch themselves to the compatibility player play again instead of turning black**
- **Prefer HLS no longer applies to catch-up**
- **Catch-up recordings now get both of their fallbacks instead of one**
- **One channel without an HLS version no longer sends every other channel's preview to the wrong format**
- **A provider that doesn't serve HLS at all is now recognised after three channels**
- **Streams that VLC plays but OwnTV didn't now get one more attempt with error tolerance turned on**
- **The HLS note under the toggle no longer implies an answer it doesn't have**
- **Turning "Prefer HLS" off could stop every channel playing on the standard player until restart**
- **Auto frame rate no longer makes the picture pause several times on one channel**
- **Auto frame rate now prefers a refresh rate the TV can reach without blanking**
- **Auto frame rate is turned off once on TVs below Android 12, and warns before it is switched back on**
- **A Live TV engine handoff is now written to the playback error log**
- **Export in the playback error log is always available**
- **Playback reports now export to the public Download folder**
- **Settings now opens with focus on Profiles, the first row**
- **Playlists whose provider blocks media players now play**
- **When a provider explains why it refused a channel, that explanation is now shown on the error screen**
- **A channel is no longer sent looking for an address that doesn't exist when the provider is simply busy**
- **The standard player can now switch stream format by itself**

## v4.1.7 — 2026-08-04

### ✨ New features

- **📐 Panel Width Adjustment — set how wide the categories, list and preview panels are**
- **🔊 Surround sound rebuilt — Auto, Stereo only, Surround, and it can no longer leave you in silence**
- **⏱️ Live latency really changes the buffer now — and a new Pre-buffer control, per playlist**
- **🎞️ Auto frame rate works out the frame rate by itself — and offers itself when 25 fps judders**
- **🗓️ Guide time offset — for a guide that is hours out, globally or for one channel**
- **🎧 Sound behaves like a TV app now — and the remote's transport keys work**
- **🩺 Diagnostics you can actually send**
- **🧩 M3U playlists: per-item headers, and catch-up that actually builds a URL**

### 🐛 Fixes

- **4K movies that failed to play on some TVs now get a real rescue instead of a wrong error**
- **A film or episode now gets every decoding option before it gives up, whichever player you prefer**
- **A file that repeatedly defeats your preferred player stops re-trying it**
- **The Home screen's background preview no longer holds a channel open on a dead stream**
- **Channels found in Search now play exactly like channels opened from Live TV**
- **Prefer HLS also applies in the TV Guide and to catch-up**
- **Retry on a movie retries the movie**
- **Long films on portal (Stalker) playlists survive their link expiring**
- **Providers that allow one stream at a time recover in the compatibility player too**
- **"Hardware decoding: Off" now applies to the standard player too**
- **Live TV channels that only allow one stream at a time no longer lock themselves out**
- **Audio and video no longer drift apart on live channels in compatibility mode**
- **Faster switch to the compatibility player when a provider refuses the standard player's stream URLs**
- **A provider that refuses playback outright is no longer hammered with the identical request**
- **Catch-up recordings that opened with sound but no picture now recover by themselves**
- **Zapping no longer slows down the Guide, artwork and playlist updates**
- **Audio Mode really switches the picture off — on both players**
- **On providers that allow one stream at a time, the preview pane now says so**
- **Subtitles are drawn in the small docked player**
- **The audio/video sync nudge is available on live channels**
- **Volume boost above 100% now works on Live TV too**
- **…and on films and episodes that play on the standard player**
- **Your preferred audio and subtitle languages now apply on the standard player as well**
- **Default zoom now applies to Live TV, and zoom no longer carries over between channels**
- **Auto frame rate is respected for films and episodes on the standard player**
- **Switching to Audio Mode during a slow start no longer shows "video could not be rendered"**
- **A live channel left in Audio Mode no longer reconnects on a loop**
- **The Home screen's background preview follows Hardware decoding and per-item headers**
- **Detailed logging now records from the moment the app starts**
- **HDR and Audio sync now say which player they apply to**
- **Coming back from Audio Mode no longer drops the picture to a few frames a second**
- **Prefer HLS no longer breaks the odd channel that has no HLS version**
- **A live channel that never opens no longer spins forever**
- **A live channel that starts and then freezes for good is handed over to the other player**
- **A channel that loads video but never starts playing is now spotted in seconds**
- **A channel that can't fill the Pre-buffer opens anyway instead of spinning**
- **Live TV gives up on a stuck channel sooner**
- **A channel that won't play now works its way through all four combinations before giving up**
- **Switching to the standard player by hand no longer strands you on a spinner**

## v4.1.6 — 2026-08-01

### ✨ New features

- **✏️ Bulk editing — rename channels, movies and series in bulk (#86)**
- **🗂️ Custom combined categories (#87)**
- **🌐 Custom DNS server — global, app-wide (#90)**

### 🐛 Fixes

- **Live TV playback switched away from the category it was opened from**
- **Auto frame rate could still cause screen blackouts when turned off**

## v4.1.5 — 2026-07-31

### ✨ New features

- **💬 Subtitle appearance — size, colour, position and background, each optional (#96)**
- **📶 Prefer HLS for Live TV — per source, with format auto-detection** (community PR #97 by @pt5pnzghm6-sys)
- **🆕 Date added — sort Movies and Series by what arrived most recently** (community PR #94 by @cotol1985)
- **↕️ Series sorting — season and episode order, set per show** (community PR #94 by @cotol1985)
- **📺 Channel numbers — type a number on the remote to tune in Live TV**
- **💾 A proper backup file — `.own`, with your wallpaper inside and real encryption**
- **🗂️ Browsing & lists — decide what Live TV, Movies and Series come back to**
- **🌍 Metadata language — descriptions and posters in your language**
- **🎞️ Auto frame rate — match the TV's refresh rate to the video**
- **🖼️ Guide channel logos — take logos from your XMLTV feed**
- **↕️ Span move — reorder a whole block of categories at once**
- **📺 Live TV full screen — redesigned top bar, and a History channel list**
- **🗂️ Category browser in the player — switch Live TV category without leaving full screen** (community PR #95 by @cotol1985)
- **📼 Catch-up from Live TV, and catch-up in the player of your choice**
- **▶️ External player — Live TV support, and a default per section**
- **🛡️ Database recovery screen — Try again or reset, instead of silent data loss**
- **🔁 Resync now vs Resync and remove missing titles**
- **⬇️ Downloads keep running in the background, with a notification**
- **🚀 Faster cold start with a branded splash screen**
- **⚡ Fast from the first launch after install, not after a few days**

### 🐛 Fixes

- **Switching category kept the previous category's scroll position**
- **The + / − buttons became unreachable once a setting hit its maximum (#88)**
- **Choosing ExoPlayer for a channel that had fallen back to mpv did not stick**
- **Auto frame rate did nothing on Android 10 and older devices (Fire OS 7)**
- **A schema problem could wipe your whole library**
- **A half-finished sync could delete your catalog**
- **A provider reorder rewrote the entire catalog**
- **Manual ordering was lost after a re-sync**
- **Interrupted restores and unreadable backups were silent**
- **Live TV gave up after a single hiccup, and stopped retrying too early**
- **Live TV did not resume by itself after a network outage**
- **Raw MPEG-TS channels dropped the connection every 10–15 seconds**
- **4K channels fell back to compatibility mode when tuning from one to the next**
- **Audio-only content showed a false playback error**
- **Compatibility mode and the engine choice did not stick on Stalker portals**
- **Short clips were marked watched at position 0**
- **New episodes never appeared in a series you had already opened**
- **A truncated guide download was trusted for 24 hours**
- **Channels added during a guide sync never got programmes**
- **Paused downloads re-downloaded data they already had**
- **A removed USB/SD card made a download continue into internal storage**
- **Subtitle timing offset froze the interface**
- **Stalker portals with a "virtual" MAC were rejected at setup**
- **Picking a new background image did nothing until the app was restarted**
- **The full-screen ◀ channel list showed the wrong channels**
- **Live channels reported "no external player found" with VLC and MX installed**
- **Switching engine during a catch-up recording jumped to the live programme**
- **"Failed on both engines" on items that played fine on the next try**
- **A live channel opened from the Guide did not appear in History**
- **Live preview played sound on surround channels with preview audio off**
- **The Preview audio setting did not apply to a preview already playing**
- **Resolution badge under-reported wide-format streams (1920×800 read as 720p)**
- **CH+ and D-pad Up went to the previous channel instead of the next in full screen (#84)**

## v4.1.4 — 2026-07-24

### ✨ New features

- **🧊 Glass Effect — frosted translucent interface over your own background photo (panels, dialogs, cards, rows, search bars & action buttons)**
- **⭐ Favorite from the player — add to Favorites without leaving the stream (live, movies & series)**
- **🗂️ Per-section sync scope — Now / Later / Off for Live, Movies & Series (#74)** (community PR #78)
- **🎨 Accent color — full HSV picker (hue bar + saturation/brightness square) with live preview**
- **🎧 Audio Mode — listen with the screen free**
- **📤 Remote Backup & Restore — move a backup between TVs over Wi-Fi**
- **📡 Live TV latency control (#72)**
- **🪟 Configurable mini-player — size & screen position**
- **🖼️ Live TV preview pane — info-only, genre dots & EPG coverage; no more accidental buttons**
- **🔄 Sync completion pill — see sync results, queued notifications** (community PR #73 by @pt5pnzghm6-sys)
- **⚡ Incremental M3U resync — faster, and favorites/history survive resyncs**

### 🐛 Fixes

- **Settings → About shows the updated Telegram group QR code**
- **Editing a source shows only its own type (no more inactive Xtream/M3U/Stalker chips)**
- **Custom accent hex codes now render exactly, not a nearby shade**
- **Accent hex field no longer hidden behind the on-screen keyboard**
- **Restored backups no longer hide all channels on first sync** (community PR #73 by @pt5pnzghm6-sys)
- **Concurrent playlist syncs no longer truncate movies / skip series** (community PR #73 by @pt5pnzghm6-sys)
- **Latency warning popup: focus returns to the Live latency row**
- **Live preview off: audio no longer keeps playing after you leave a channel**
- **4K live channels no longer lag/judder on mpv when a provider sends broken timestamps**
- **Playlists & EPG Sources menus: focus stays inside the list on entry, edit, re-sync, delete**
- **Settings dialogs: D-pad can no longer escape behind the scrim**
- **Settings / Video Player lists no longer scroll-animate from the top when a dialog closes**
- **OpenSubtitles / Network / Metadata settings: focus no longer escapes on entry or state changes**
- **Profiles / Mini-player / Customize / CH+- paging / Weather: focus returns to the row that opened a dialog**
- **Long-press menus in Movies / Series / Live / Guide trap D-pad focus**
- **Downloads: focus moves to the next download when you delete one**
- **Home & Customize category lists trap vertical D-pad focus**
- **Category rail: abbreviation badges removed (#75)**
- **OpenSubtitles sign-in & local-file buttons removed from the subtitle search overlay — sign in from Settings**
- **Catch-up dialog: D-pad focus no longer escapes the popup; picks up Lora popup styling**
- **Category rail highlight: sharper 8dp corners (Live / Series / Movies)**

## v4.1.3 — 2026-07-19

### ✨ New features

- **💬 External subtitles — OpenSubtitles search & local subtitle files**
- **👥 Profile-based backups — pick profiles to export (PIN-protected), restore now merges without wiping**
- **🔐 OpenSubtitles logins now ride in encrypted backups (per profile)**
- **🗂️ Categories grouped by provider + new-category Show/Hide control** (community PR #70 by @pt5pnzghm6-sys)
- **📱 Add a playlist from your phone (Remote setup)** (core idea from @zarga03, PR #66)
- **⏱️ EPG guide sync can Run in background during setup**
- **🔍 Default UI zoom is now 90% for a better out-of-the-box fit**
- **📺 Current programme under each channel in the Live TV list & in-player channel overlay**
- **🎯 Smarter EPG matching — picker suggests related channels first, more robust auto-match**
- **🎬 Better TMDB title cleaning for movie & series matching**
- **🔄 EPG / Guide syncs now show the background status pill**
- **📦 Smaller downloads — split arm + x86_64 APK builds**
- **⬇️ In-app updater picks the APK matching your device**
- **🔒 Customize PIN stored as a salted hash** (community PR #65 by @aravindtri)
- **📊 Measured fps, bitrate & dropped-frame stats for ExoPlayer** (community PR #67 by @pt5pnzghm6-sys)
- **📊 Bitrate in the player top-bar chips for all playback**
- **📊 "Measured stream stats" toggle (Settings → Video Player → Diagnostics)**
- **🔀 CH+ / CH− keys now page the category & item lists — incl. Settings → Customize (skip N, long-press for first/last)**
- **🗂️ One-click full storage access that works on more TVs**
- **🎨 Compact popup menus in a new serif font**

### 🐛 Fixes

- **Storage access grant no longer dead-ends on some Android 11/12 TVs**
- **Storage picker focus can no longer escape the popup**
- **Deleting an EPG source shows a "Deleting…" status and can't orphan guide data**
- **EPG match re-syncs from the network when the cache has no data for the matched channel**
- **Match EPG from Live TV now updates the preview pane immediately**
- **Focus returns to the channel after the Match EPG dialog closes**
- **Match EPG / review popups: buttons on the right, focus stays inside the popup**
- **Customize screen now respects the selected playlist**
- **Customize screen renamed to "Customize Categories & Items"**
- **Hero preview URLs redacted in error logs**
- **Live TV "Now" no longer shows a future programme on short-EPG gaps (#68)**
- **CH+ / CH− long-press disabled on the "All" list; skip-dialog buttons aligned**

## v4.1.2 — 2026-07-14

### ✨ New features

- **⚡ Run in background during the first playlist import**
- **📡 Stalker portals: live TV first, movies & series sync in the background automatically**
- **🚀 Adaptive portal speed & delta-checked faster re-syncs (Stalker)**
- **🔄 Background-sync status pill**
- **📺 Live player guide card — Before / Now playing / Next**

### 🐛 Fixes

- **Guide programme popup: last button was cut off**
- **Guide programme popup: long-press no longer selects in one go**
- **Settings → Home screen: focus lands on the first row**
- **Failed background import keeps the playlist for re-sync**

## v4.1.1 — 2026-07-14

### ✨ New features

- **📡 Stalker / Ministra portal support** — Live TV, Movies & Series from a Portal URL + MAC address
- **🧭 Sidebar Menu Customization** — auto-adapt side icons to your playlist, or hide specific ones
- **Add channels to Favourites from the TV Guide**
- **Subscription expiry shown in Manage sources (Xtream & Stalker)**
- **Deleting a playlist now shows its progress**

### 🐛 Fixes

- **Updating from an older version could crash the app at launch**
- **Hidden categories are now respected in the TV Guide**
- **Download retry & failure polish**

## v4.1.0 — 2026-07-11

### ✨ New features

- **Playback error log in Settings**
- **Custom TMDB names are now in Backup & Restore**
- **Wider interface zoom range (50%–150%)**

### 🐛 Fixes

- **Smaller app, faster cold start (R8)**
- **Less UI work while browsing**
- **Dialogs no longer get cut off on small screens**
- **A–Z sorting now applies to categories too**
- **Grids keep your place through background refreshes**
- **Much faster global search on huge catalogs**
- **Faster playlist import on huge playlists**
- **Big folders page faster**
- **Smoother UI during large syncs**
- **Posters and channel logos are cached on disk**
- **Faster, safer backup restore**
- **Faster first launch when upgrading from v3.2.0 or older**
- **Scheduled syncs now retry after network blips**
- **Player stability hardening**
- **More accurate playback error diagnosis**

### 🔧 Under the hood

- **ExoPlayer updated to 1.10.1**
- **Koin, Coil & WorkManager updated**

## v4.0.3 — 2026-07-09

### ✨ New features

- **Settings: search field & one-press quick toggles**
- **Search: launcher home (Continue / Unwatched / Channels + recent searches), list + detail pane**
- **Downloads: Active / Waiting / Completed / Failed groups, storage bar & clearer failures**
- **Download status strip on movie, series & episode poster panels**
- **Shell: shared "Continue" chip to resume your last movie / episode / channel**
- **Series: watched indicators, "Next up" card, "Hide watched" filter & manual mark-as-watched/unwatched**
- **TV Guide: "now" line, Jump-to-Now, catch-up ↻ badges, genre dots & a bottom preview strip**
- **Movies: watched ✓ & progress on posters, resume label & manual mark-as-watched/unwatched**
- **Player: next-episode countdown card with Play now / Cancel**

### 🐛 Fixes

- **All seasons now reachable on long-running series**
- **Clearer 4K decode-guard message**
- **Player seek bubble now shows the time remaining**
- **Favourite "On Now" mini-guide now covers every favourite channel** (community PR #62 by @codeVerine)
- **Home hero & Continue Watching tiles now use TMDB backdrops, logos & plot** (community PR #62 by @codeVerine)
- **Home now refreshes in place after switching the top-bar playlist** (community PR #62 by @codeVerine)
- **Manual reorder (Move positions) now included in Backup & Restore**

## v4.0.2 — 2026-07-07

### ✨ New features

- 🏠 **Customizable Home screen — reorder/hide rows, dwell-to-expand hero, On Now mini-guide** (community PR #58 by @codeVerine)
- ⚙️ **Settings menu reorganized**
- 🗂️ **Multiple playlists — switch the whole app to one playlist (or all)**
- ✨ **VOD engine fallback (movies & series play on more devices)**
- 🔄 **Per-source Auto Refresh (playlists & EPG)**
- 💾 **Backup & Restore now covers every persistent setting**
- 🎬 **TMDB metadata enrichment (Movies, Series & Episodes)**
- 🎞️ **In-app trailers for Movies & Series**
- 🙈 **Hide individual movies & series — and a Customize PIN lock**
- ✨ **External player — play movies, series & downloads in VLC / MX Player**
- 📺 **Live TV closed captions now work (#57)**
- 🌦️ **Weather settings submenu — Celsius / Fahrenheit**
- ⚠️ **Low-zoom memory warning (#51)**

### 🐛 Fixes

- **Live channel-list overlay now matches the channel you launched from Home (#55)**
- **Active nav section stays visible when focus moves away (#47)**
- **4K Live channels no longer break playback on some TVs**
- **Live engine pill now shows the engine that's actually playing**
- **Live TV zoom / aspect modes now work**
- **Fill / Crop now actually zooms in and crops**
- **Weather chip: VPN-friendly location override + hide toggle (#45)**
- **Modal D-pad focus can no longer escape into the UI behind it (#48)**
- **Focus returns to the right item after a long-press context menu (#46)**
- **Fixed D-pad navigation from the Movies/Series grid to the detail pane**
- **Fixed episode long-press menu losing focus after Refetch TMDB details**
- **Failed TMDB lookups are no longer remembered as "no match" for 7 days**

## v4.0.1 — 2026-07-03

### 🐛 Fixes

- **D-pad focus no longer jumps to the top bar while scrolling long lists**
- **Top-bar Search button now appears only while the highlight is on the left nav panel**
- **Autoplay next episode no longer fails with a "malformed or corrupted" error**
- **Player HUD no longer steals D-pad focus from overlays drawn above it**

## v4.0.0 — 2026-07-02

### ✨ New features

- ⚡ **Much faster syncing & background updates (community PR #40 by @codeVerine)**
- **Backup now covers more settings and encrypts saved passwords**
- **Manually reorder channels, movies and series**
- **Remove a single item from History**
- **Download from long-press menu**
- **Settings → Customize Category**
- **Global HTTP proxy support**
- **Home screen with Continue Watching**
- **Stream technical info overlay**
- **Volume boost to 150%**
- **Fixed, roomy layout — no more "sandwiched" Live TV**
- **Shell redesign — new sidebar, top bar, and rounded panels**
- **Clear watch history**
- **Favorite a channel straight from Search**
- **Detailed channel search results**
- **Move categories to top / bottom**
- **Animations setting (On / Off)**
- **Channel list in the player**
- **Per‑profile startup (default landing)**
- **Remembers where you were in Live TV**
- **Guide by category**
- **Favourites in the Guide**
- **List view for Movies & Series**
- **A/V sync nudge in the player**
- **One-tap guide sync after adding a playlist**
- **Long-press a channel in Live TV**
- **Closed captions (CC) on Live TV**
- **Compatibility mode (per-channel mpv engine)**
- **Movies & Series open instantly**
- **The Guide opens instantly**
- **Much faster EPG sync**
- **Leaner TV Guide internals**

### 🐛 Fixes

- **Live TV could give up reconnecting too early during a real outage**
- **Audio-plays-but-no-video no longer leaves you stuck on a black screen**
- **Favorites could disappear after a source re-sync failed partway through**
- **Live TV no longer freezes silently mid-stream**
- **EPG match no longer removes a channel from the Guide**
- **Show/Hide password toggle on all password fields**
- **Per-source User-Agent for playback**
- **No more false "Playback error" over a movie that's actually playing**
- **Startup focus rests on the nav**
- **Clear watch history now empties Movies/Series from Home too**
- **Live preview shows full stream spec**
- **Startup → Live · Favorites lands inside the list**
- **Long‑press channel menu keeps focus on the channel**
- **Clearer Surround sound warning**
- **Imports survive a provider that errors on the full Movies/Series list**
- **EPG no longer fails on a single malformed tag**
- **Playback survives the screensaver**
- **Live TV no longer freezes with no recovery**
- **No sound when opening a channel very fast**
- **One corrupted file no longer breaks all playback**
- **Audio/video drift on some movies**
- **Long-press to favourite in Movies and Series**
- **Sync no longer wipes data on failure**
- **Sync times out fast instead of spinning forever**
- **M3U VOD entries now route to Movies**
- **Offline banner now works on all devices**
- **Profile dialog focus no longer escapes**
- **Two-stage video watchdog**
- **Guide shows programmes on first open**

## v3.2.0 — 2026-06-22

### ✨ New features

- **Live rewind (timeshift)**
- **Switch profile without leaving the app**
- **Wider category folders**
- **Catch-up defaults to your device timezone**
- **Longer Guide catch-up**
- **Clearer audio-track icon**

### 🐛 Fixes

- **Audio & subtitle selection now works on Live TV**
- **No more silent playback for AC3/DTS files played as live**
- **Live audio no longer keeps playing after you exit/log out**
- **Clearer error for an unplayable movie**
- **Playback errors now show the real reason**

## v3.1.2 — 2026-06-21

### 🐛 Fixes

- **Surround sound is now off by default (opt-in), with a safety net**
- **Live TV recovers from connection drops**
- **Screen no longer sleeps during Live TV**

## v3.1.1 — 2026-06-21

### ✨ New features

- **Near-instant Live TV (two playback engines)**
- **Import a playlist from a local file**
- **EPG is now opt-in**

### 🐛 Fixes

- **Surround sound no longer stutters video**
- **M3U live channels that wouldn't play now work**
- **4K channel zapping no longer hangs**
- **Episodes now appear for every Xtream series**
- **Global search opens the right series**

## v3.1.0 — 2026-06-20

### ✨ New features

- **Catch-up straight from Live TV**
- **Hide/show a whole range of categories at once**
- **Auto-play next episode**
- **Series open on your last-watched episode**
- **Surround sound passthrough**

### 🐛 Fixes

- **Faster channel zapping**
- **Live channels that dropped out every few seconds now play continuously**
- **Smoother video on TVs**
- **Installs on non-TV devices now**
- **EPG sources that failed with a "protocol error" now load**
- **Image-based subtitles now play smoothly**
- **Big-library import no longer gets stuck**

## v3.0.0 — 2026-06-17

### ✨ New features

- **Browse the TV Guide timeline**
- **Catch-up TV (archive)**
- **Auto-match your channels to the guide**
- **Match a channel's EPG from the Guide**
- **See what's coming up in Live TV**
- **Change channels with the D-pad**
- **Sort the TV Guide**
- **See a channel's real resolution before you watch**

### 🐛 Fixes

- **New playlists show up immediately**
- **Huge playlists import fully again**
- **Faster channel switching in Live TV**
- **Left from the channel list returns to your category**
- **"Now watching" card shows the right channel**

## v2.2.4 — 2026-06-14

### ✨ New features

- **Back from a series returns to the right poster**
- **No more sidebar flicker in Settings**
- **…and no category-rail flicker**

## v2.2.3 — 2026-06-14

### ✨ New features

- **Channels that wouldn't load now play**
- **Back hides the player controls first**
- **Smarter playback retries**
- **Channel zapping from the Guide**

## v2.2.2 — 2026-06-14

### ✨ New features

- **Category rail highlight follows your focus**

## v2.2.1 — 2026-06-14

### ✨ New features

- **Search your categories**

## v2.2.0 — 2026-06-14

### ✨ New features

- **Multiple EPG sources**
- **Match a channel to a guide manually**
- **"What's New" before updating**
- **Back up your settings too**
- **Aspect-ratio button in the player**
- **D-pad is now strictly for navigation while watching live**
- **Picture-in-Picture for live TV**
- **Playlists show what's in them**

### 🐛 Fixes

- **Favorites & history survive a re-sync**
- **Hiding a group now hides its channels everywhere**
- **Plays more streams on weak boxes**
- **Movie backdrop no longer looks clipped**
- **Simpler, crash-proof video**

## v2.1.0 — 2026-06-13

### ✨ New features

- **Channel up/down with the remote**
- **TV-friendly text entry**
- **Easier Fire TV install**

## v2.0.1 — 2026-06-14

### ✨ New features

- **Keep the screen awake while watching**
- **Renderer modes**
- **Recovers from a busy decoder**
- **Smoother subtitles, quieter logs**

## v2.0.0 — 2026-06-13

### ✨ New features

- **Playlist-order sorting**
- **Full category names**
- **Content customization (per profile, survives re-syncs)**
- **Custom EPG URL per source**
- **Tune from the Guide**
- **Guide search**
- **Guide lists every channel**
- **Resume, your way**
- **In-app updates**
- **Custom accent colors**
- **Simpler Settings**
- **Selective backup & restore**
- **Restore on first launch**
- **TV-style search bars**
- **About screen**
- **EPG status**
- **Complete backup**

### 🐛 Fixes

- **Runs properly on real TVs**
- **No more freezes (ANRs)**
- **Blank player fixed**
- **Live-drop recovery**
- **Guide fixes**
- **Episode resume actually works now**
- **Crash fixed**
- **Profile PIN locks can now be removed**
- **Restoring a backup keeps you in Backup & Restore**
- **Category rail performance**
- **Layout fixes**
- **Focus fixes**
- **D-pad navigation fixed everywhere**

## v1.0.0 — First public release

### ✨ New features

- Live TV, Movies, Series with folder rail, favorites, history, and per-folder + global search
- Full **EPG guide** (time × channel grid) + now/next in the Live preview
- **libmpv (FFmpeg)**
- Multiple **profiles** with PIN lock & kids flag; sources shareable between profiles
- Offline **downloads** for movies & episodes
- **Backup & Restore**
- Material 3 design (AMOLED dark / light), accent colors, UI zoom, avatars
- Scales to huge playlists (tested ~64k channels / ~169k movies)
