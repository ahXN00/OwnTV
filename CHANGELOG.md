# Changelog

## v4.2.5 — unreleased

### 🖼️ Now Trending, your way

- **Choose how Now Trending looks.** Settings → Home has a new *Trending layout* line: keep the big
  detailed card with its artwork, badges and the panel explaining why a title is there, or switch to
  *Posters only* — a plain row of posters like the rows under it. The detailed card is what you have
  today and stays the default, and the line only appears while Now Trending is switched on. The
  choice is kept with your profile, so the phone app shows trending the same way.

### 📅 The guide keeps itself up to date

- **The guide refreshes itself after a guide feed changes.** Adding, re-syncing or deleting an EPG
  source now updates the guide straight away, instead of showing the old programmes until the app is
  restarted.

### 🪟 A new Glass Effect look: Aurora

- **Aurora** joins Subtle, Balanced, Frosted, Vivid and Opaque in Settings → Appearance → Glass
  Effect. It is clearer than Balanced and frosts a little harder, and it is the look the phone app
  uses by default — so a television and a phone set to Aurora are set to the same thing. Nothing
  already chosen changes: the new look sits between Opaque and Custom, and every existing preset,
  scope and slider is exactly where it was.

### 🌍 The guide now matches channels in any alphabet, and matches them quickly

- **Auto-match works for Cyrillic, Greek, Arabic and CJK channel names** (community PR by
  @Sekator778). Channel names were cleaned down to plain Latin letters and digits before being
  compared, which left a name written in another script as nothing at all — so it could never be
  paired with a guide entry, and the guide stayed behind "channel ids don't match your channels' EPG
  ids" no matter how many times it was re-run. Names are now compared in whatever script they are
  written in, decorative spellings like `ᴴᴰ` are folded away first, and a channel's number is read in
  its own digits, so `MTV ٢` and `MTV 2` are recognised as the same channel while `٢` and `٣` are
  still kept apart.
- **Auto-match finishes in seconds rather than minutes.** The scan compares every channel against
  every guide entry — a 1,786-channel lineup against a 1,907-channel guide is about 3.4 million
  comparisons — and it ran one at a time on a single core, which on older TV hardware meant a
  spinner that outlasted anyone's patience. It now runs across all of the television's cores and does
  less work per comparison: about nine times faster end to end on that catalogue, with exactly the
  same matches in the same order.

### 🧪 Test a playlist before you trust it

- **Every saved playlist has a Test button** on its row in **Settings → Manage sources**. It contacts the
  provider and reports back in a popup: whether the account is active, when the subscription expires,
  whether it is a trial, and **how many connections are in use out of how many are allowed** — the
  figure that explains "why does it say the maximum number of devices is reached?". Xtream accounts get
  all of it, M3U and Stalker portals get reachability and, for Stalker, whether the portal accepted the
  MAC address.
- **The same Test button is inside the playlist form**, so you can fill in a brand-new provider's
  details and check them **before saving**. It tests what is on screen, not what is stored, and it is
  available for M3U, Xtream and Stalker alike. Stalker's old one-line "connection OK" message is gone;
  it now opens the same full result popup as everything else. The Xtream **Test HLS support** button is
  unchanged.
- Nothing is downloaded, synced or written to your library by a test — it only asks and reports.

### 🔄 Auto refresh: choose your own number of days

- **Playlist auto-refresh now offers a custom interval of 1 to 99 days.** The list is **Off**, **Refresh
  at startup**, **6 hours**, **12 hours** and **Custom interval…**; picking the last one opens a day
  picker where **Left and Right change the number by one** and holding the key repeats. No on-screen
  keyboard, and cancelling leaves your previous choice alone.
- **The old fixed 24-hour, 48-hour and 7-day choices are gone, and any playlist already using one moves
  across by itself** — 24 hours becomes 1 day, 48 hours becomes 2 days, 7 days becomes 7 days. You do
  not have to set anything again, and restoring an older backup does the same translation.
- **The companion phone/browser form** offers 1, 2, 7, 14 and 30-day presets; any other figure is dialled
  in on the television.

### 🐛 Bug fixes

- **Live TV's catch-up list now reaches back a full week, like the TV Guide already did.** The list was
  capped at 48 hours by a note that stopped being true when the stored guide grew to seven days, so a
  programme from four days ago could be replayed from the Guide but was not even listed against the same
  channel in Live TV. Both screens now offer the same archive, still limited by what each channel's own
  provider allows.
- **The guide line under a channel's name no longer goes blank after updating the app.** The channel list
  looked up programmes only among the playlists the visible page happened to come from, while the preview
  pane beside it searched everything — and guide entries routinely sit under a different playlist than
  the channel showing them (one provider's guide covering another's channels). The two disagreed until an
  EPG re-sync happened to rewrite the entries. The channel list now searches every playlist and every
  guide feed, so the line under the name matches the panel next to it.
- **Films and shows your provider gave no artwork for now show the poster OwnTV already found.** Their
  tiles stayed a grey placeholder for good, even though highlighting one filled the panel beside it with
  the real poster — the tile only ever used the picture that came with the playlist, and the panel's find
  was never carried back to it. It is most obvious sorted by **Date added**, which puts a whole freshly
  imported batch, artwork or not, at the front of the list. Highlight such a title once and its tile keeps
  that poster from then on, along with every duplicate copy of the same film (the 4K listing and the
  ordinary one fill together). Nothing extra is downloaded while you scroll — only what is already stored
  on the television is reused, so a title nothing has been found for yet still shows the placeholder.

## v4.2.4 — 2026-08-27

### 🩺 A crash now leaves a report you can send

- **The Playback error log is now simply Error log, and it lives under Settings → App, after About.**
  It is no longer a playback-only page: alongside the most recent playback failures it now carries the
  last crash, so one place answers "what went wrong" whatever went wrong. The row keeps its place in
  settings search — searching for *error* or *crash* still finds it.
- **A crash is written to disk the moment it happens** and survives the app being killed, so it is still
  there the next time OwnTV opens. **Export** puts it at the top of the report file it saves to Downloads,
  ahead of the playback history, which is the file to attach when reporting a problem.

### ⚡ Playlists import and refresh faster

- **Downloading a playlist and saving it into the library now happen at the same time.** Previously the two
  took strict turns: reading the provider's list stopped completely while a batch was written, then the
  database sat idle while the next batch was read. Both now run together, on every playlist type — M3U,
  Xtream and Stalker portals alike, on a first import and on a refresh. Nothing about the resulting library
  changes; there is simply less waiting.

### 🚀 OwnTV opens faster

- **The app is usable in roughly 0.7 seconds instead of 1.5, and Home fills in about 0.6 seconds instead
  of 1.6.** Measured on a TCL Google TV over five cold starts before and after, with a full catalogue.
  Nothing was removed or simplified to get there — the same rows, artwork and quality arrive, sooner.
- **The largest single cause was the list of code OwnTV asks Android to prepare ahead of time.** That
  list is recorded by walking the app, and it had been recorded on a fresh install, so it described the
  first-run setup wizard rather than Home, Live TV, Movies, Series and Search. It is now recorded against
  a real catalogue and covers every browse screen, so the first screen no longer has to be worked out
  from scratch while you wait.
- **Home no longer loads itself twice on startup.** Two separate triggers each asked for the same load a
  fraction of a second apart, and the second one restarted work the first had nearly finished.
- **Home's rows are now fetched all at once rather than one after another**, and the Continue watching row
  no longer re-reads the same show from the database once per episode. On a library with a long history
  this was the slowest part of opening the app.
- **Posters load over a faster connection to the artwork service**, so a grid of covers no longer queues
  up behind a handful of connections.
- **Playlists and artwork are read with settings better suited to a television's storage.**

### 🧰 Toolchain

- **Updated to the current release of every build tool and library OwnTV depends on**, including Kotlin,
  the Compose UI toolkit, the image loader and the networking stack. Building the project now requires
  Android SDK 37; the app still runs on Android 8.0 and newer, exactly as before.

### 🐛 Bug fixes

- **Live TV no longer crashes when a channel goes full screen with Animations turned off.** The LIVE badge
  introduced in 4.2.3 pulsed using a duration that the Animations = Off setting reduced to zero, which
  brought the app down a frame later. Because the Animations setting is included in Backup & Restore, users
  who reinstalled and restored hit it again immediately. The badge now simply stays steady when animations
  are off.
- **The long-press menu editor can be scrolled.** With thirteen actions, the Movies list was taller than a
  television screen, so its title and its Save and Cancel buttons hung off the top and bottom with no way to
  reach them. Every menu editor now scrolls within the panel.
- **A playlist that claims an absurd catch-up length no longer breaks tuning.** A nonsense `catchup-days`
  value overflowed the rewind window into a negative number and threw while the channel was being opened;
  the value is now capped at 31 days.
- **Stopping a stream during a retry no longer crashes the player.** Several retry paths re-read the current
  stream address after their backoff delay, which could have been cleared by a stop in the meantime.
- **A television whose media session refuses to start no longer takes playback with it.** The media session
  exists so other apps and the TV's own controls can see what is playing; if it fails, the video now
  continues regardless.
- **Long-press menu Settings now reopens in the order you saved.** The editor briefly treated its empty
  loading value as the real setting, filled itself with the shipped order and then ignored the saved
  order when it arrived. Live TV, movie, series and episode editors now wait for their stored value, so
  the arrangement shown in Settings matches the popup you actually use.
- **Resuming from Home no longer makes working Auto surround fall back to stereo or mpv.** ExoPlayer
  prepared a movie or episode at the beginning and immediately sought to Home's saved position, forcing
  some TCL/Realtek TVs to rebuild a just-created E-AC3 output that then stayed silent. The saved position
  is now part of the initial preparation, so Home resumes directly there with the same working audio path
  as playback started from Movies or Series.

## v4.2.3 — 2026-08-26

### 🏷️ Every merged library says which provider an item came from

- **When two or more playlists are active, Live TV, Movies, Series and the TV Guide now place a compact
  provider label beside each category and item.** The label also appears on poster cards, list rows, Guide
  channel rows, the Guide category picker, and the full-screen channel/history overlays, so identical names
  from different services are no longer a guessing game.
- **Single-playlist browsing stays exactly as clean as before.** Provider labels appear only when they are
  useful; with zero or one active source, OwnTV leaves the existing layouts unchanged.

### 🔎 Customize can show All, Visible or Hidden entries

- **Both the category screen and a category's item screen now have a Filter control.** Choose All, Visible
  or Hidden to narrow the working list without changing anything, making it practical to audit what has
  already been hidden or bring selected entries back.
- **Changing section or opening another category starts from All again**, and the list returns to its first
  row when the filter changes so D-pad focus and range actions have a predictable starting point.

### 🎛️ Spare remote buttons can do exactly what you choose

- **Settings → Content → Remote Shortcuts replaces the old fixed CH+/RW · CH−/FF paging page.** Press or
  hold a spare key, choose one of 25 navigation, browsing and playback actions, and edit or delete that
  assignment later. Short and long presses are independent; one master switch disables every custom
  assignment without deleting it, and Restore default shortcuts puts the shipped paging controls back.
- **The familiar paging behavior remains the default.** CH+ and Rewind page toward the first items and
  jump to the first item when held; CH− and Fast-forward do the same toward the last items. The two skip
  counts remain adjustable, and Jump to last stays blocked on enormous All lists.
- **Shortcuts can open every top-level destination, Search, profile or playlist switching; continue the
  last watched item; control or move Now Playing between fullscreen, mini-player and Audio Mode; page or
  jump through the focused browse panel; and open contextual player controls.** Focus Now Playing chooses
  the mini player or Audio Mode bar intelligently, while Play/Pause works on the current playback.
- **Essential remote navigation cannot be reassigned.** Back, D-pad, OK, volume, Home and power remain
  protected; long-press Back still focuses Now Playing. Number shortcuts work while browsing, but
  fullscreen number entry keeps priority for channel navigation. Assignments are included in Backup &
  Restore.
- **The assignment list now looks like the physical remote.** Rewind, Fast-forward and Channel+/− have
  distinct key symbols, short and long presses carry compact badges, coloured keys look like coloured
  keycaps, and the two skip-count rows use clear first/last paging illustrations.

### 🕰️ The Guide keeps the present in view

- **The Guide now opens with the current time around 37.5% across the timeline**, leaving useful programme
  title space on both sides instead of pinning the live edge against the channel list. It loads two recent
  hours as part of the immediate view while older catch-up history continues to fill in behind it.
- **The live marker updates every 30 seconds without leaving and reopening Guide.** Its old solid red rule is
  now an amber line that fades down each programme row, with a matching amber **NOW · time** badge in the
  time ruler. Jump to Now uses the same live clock.

### ↔️ Choose how much of Guide belongs to channels and programmes

- **Settings → Layout → Guide Column Widths adds an independent two-column split for the TV Guide.** The
  pinned channel list and scrollable EPG timeline can each be adjusted from 10% to 90% in 5% steps, with a
  live diagram showing the result.
- **The two shares must total exactly 100% before Save works.** Turning customization off restores the
  standard 10% / 90% layout without discarding the saved values, and the setting is included in Backup &
  Restore.

### 🪟 Popup boxes and popup text finally size independently

- **Appearance now has separate controls for popup geometry and popup text.** Popup size scales dialog and
  menu boxes from 50% to 120%, while Popup font size changes only their text from 50% to 120%; changing the
  main app font size no longer makes popup panels physically grow or shrink.
- **Large popup text remains reachable by scrolling inside the fixed panel**, and the shared popup treatment
  now covers the previously independent source, backup, EPG, update, player-warning, metadata-name and
  browsing menus while deliberately dense or full-screen panels keep their own base dimensions.

### ✨ Glass Effect is now a complete settings screen

- **Glass Effect moved out of an oversized popup and into a proper Settings page.** A compact live preview
  stays at the top; turning Glass off hides the controls below it, while turning it on reveals Appearance,
  Fine tuning, Behavior and Apply glass to sections without losing any existing function.
- **All six presets, background-image choices, transparency, blur, highlight, full-transparency protection,
  depth effects and per-surface choices remain available.** Panels, Sidebar, Preview, Dialogs, Top bar,
  Cards and Mini player can be toggled inline, and the new **All** button both reflects the complete set and
  enables every surface in one press. **Reset to Balanced** also restores the default highlight, depth and
  all-surface selection.

### 🪟 The corner player carries one floating strip of buttons

- **The mini player's controls are now a single rounded pill floating just above the bottom of the little
  window**, instead of a row of buttons welded to its edge. Play leads the group as a large circle in your
  accent colour; everything else is a small dark tile, with hairlines separating sound (volume, audio mode)
  from window (size, corner) from exit (fullscreen, close).
- **Shrink the window and the strip thins itself out before anything can be cut off.** At the smallest size
  it keeps play, volume, fullscreen and close, and tucks the rest behind a **⋯** tile you press to swap them
  in and out. Nothing ever spills outside the window.
- **The window's progress line moved to its top edge**, so it no longer runs behind the buttons. Live
  channels have no progress and simply don't show it.
- **The window itself lights up with an accent outline while the remote is inside it**, and the title still
  appears — and scrolls, if it is long — only while it holds focus.

### 🎵 The music bar opens up when you move to it

- **The now-playing bar at the top grows when the remote reaches it, and the page below slides down with
  it** rather than being covered. Left alone it is exactly the thin strip it always was.
- **Opened, it shows artwork** — the station's logo when there is one, otherwise a coloured tile with the
  dancing bars in it — plus the station name and whether you are live or how far into a recording you are.
- **Seven controls, not five.** A **favourite heart** was added — it toggles the same favourite as the full
  player and stays coral when set — and **Close** now ends Audio Mode from here, so Back is free to simply
  step out of the bar.
- **The two outer buttons say what they will actually do.** On a live radio channel they change channel; on
  a recording they rewind and fast-forward. Same place, different job.

### ✨ Focus that reads like light, and icons that stop sharing

- **Buttons over the picture now take focus as light**: a thin accent rim, a soft wash of the same colour
  and a gentle glow behind it. With Reduce animations turned up the glow goes and the rim stays, so the
  focus is never lost.
- **Nine settings that used to share a symbol with something else now have their own.** DNS, Remote shortcuts,
  Panel width, Focus highlight and Auto-play next each got a drawn mark of their own, and Zoom and the TV
  guide were redrawn to look like what they do. The live dot in the player is now the same mark everywhere
  it appears — the badge, the Go live button and the marker at the end of the timeline.

### 🔀 Arrange the long-press menus yourself

- **Settings → Layout → Long-press menus** lets you set the order of the actions in the four long-press
  menus — channel, movie, series and episode — each one on its own. Pick a menu and the exact popup you
  see while browsing opens, holding the same actions in the same style.
- **Hold OK on an action to pick it up**, then Up and Down to carry it, then OK to drop it. A ↕ sign marks
  the action you are carrying, and the row stays highlighted so you can see where it lands. Nothing is
  saved until you press Save; Cancel or Back leaves the menu as it was.
- **Reset puts every menu back the way it ships**, and there is a Reset on each menu as well as one for all
  four at once.
- **An order you set now survives later updates.** An action added by a future version appears at the end
  of your arrangement rather than being lost, and an action that goes away is simply skipped — you never
  have to redo the order after an update. **Close** is always last and cannot be moved.

### ▶️ The player's transport buttons sit in one pill

- **Play, pause, skip and the two seek buttons are now grouped inside a single rounded dark pill** in the
  middle of the screen instead of floating loose over the picture, and they are slightly smaller so the
  group reads as one control.
- **The button you are on fills with your accent colour** and gets a thin white outline. The icon inside
  switches between dark and light to stay readable, so a very dark or very bright custom accent still
  shows the symbol clearly.

### 🗂️ Settings is a spine and a sheet, with your quick switches at the top of it

- **The section list on the left is now a proper column of its own.** Each of the nine groups carries
  its icon and how many settings it holds, and the selected one gets an accent bar down its leading
  edge with the highlight fading away from it — so the left column visibly points at the rows on the
  right. It sits on the same panel the settings do, instead of floating loose beside them.
- **The settings themselves are one sheet, not 34 floating cards.** The group is named at the top with
  a one-line summary of what is in it, and every value (`Dark`, `Teal`, `100%`, `On`) lines up in a
  column down the right-hand side as quiet text rather than a filled badge. The highlighted row gets a
  slim accent stripe on its left and a soft wash, which is what separates the rows — there are no rules
  between them.
- **"Quick" is now the first entry in the section list.** The six most-used switches — Live preview,
  Preview sound, Channel numbers, HDR, Auto-play, Check for update — moved out of the strip that used
  to sit across the top of Settings and became ordinary rows you flip with OK. Nothing was lost and
  nothing needs setting up; the header they vacated is now a single line, which is where the extra room
  for the settings came from.
- **Search is a small pill in that header.** Press OK on it and it opens into the field you already
  know; Back clears what you typed, and Back again puts it away. Results still name where each setting
  lives and still open it directly.
- **Arrows now only appear on rows that actually open another screen.** A row that opens a popup, or
  flips in place, no longer promises a page that isn't there.
- **Everything still scrolls.** At 150% UI zoom on a large screen the columns are narrower and taller
  lists scroll, so no setting can ever be out of reach.
- **Coming back from a setting no longer changes the section you were in.** Pressing Left out of the
  settings used to land on whichever section happened to be level with your row — and because the
  highlight *is* the selection there, that quietly switched groups on you. It now always returns you to
  the group you were inside.

### 🪟 You can reach the docked mini player again

- **A "Now Playing" item appears at the top of the side menu** whenever a video is docked in the corner
  or Audio Mode is running. Press OK on it and the remote moves into that little window. It shows the
  channel's logo (or the equalizer in Audio Mode) inside a gently pulsing accent ring, and it disappears
  the moment there is nothing playing.
- **Hold BACK from anywhere to jump into the mini player, and hold it again to come back.** Short
  presses of Back are completely unchanged everywhere in the app.
- **Back from inside the mini player returns you to the exact control you left**, instead of dropping
  you somewhere arbitrary.
- **Play/Pause on the remote works on the docked window wherever you are.** CH+/CH− change its channel
  too, except where those keys already have a job — in a browse list they still page the list, and in
  full screen the player still owns them.
- Why this was needed: the mini window floats above the content panel, so the remote's directional
  focus had no path into it from most screens. It was reachable by luck from some, and not at all from
  Settings.

### 💬 A separate subtitle size for each of the two players

- **Settings → Video player → Subtitle appearance → Subtitle size now holds two rows**, one for
  ExoPlayer and one for MPV, above a preview that shows both sizes at once so you can see the
  difference. Press OK on a row to step it through Small → Normal → Large → Extra large; the preview
  follows as you go.
- **Why:** the two players draw the same size setting differently — ExoPlayer's text is noticeably
  bigger — so a single shared setting could never be right for both. "Small" read as medium on one and
  tiny on the other.
- **Nothing changes on upgrade.** Whatever size you had is kept for *both* players until you decide to
  move one of them, and the setting still travels in backup and restore.

### ⏱️ A dead channel says so, instead of leaving you with a black screen

- **New setting: Settings → Video player → Live TV → "Give up on a channel after".** A live channel
  that will never play used to leave you looking at black for about a minute and a half, because
  OwnTV silently worked through four different player-and-format combinations and each one had its
  own long timeout. Nothing put a limit on the total. There is now a limit on the whole attempt —
  **30 seconds by default**, with 15 and 60 also offered — and when it runs out you get the usual
  error message instead of more black screen. **Never** keeps the old behaviour for a slow provider
  that needs it.
- **A wait your provider asks for is never counted against it.** When a panel answers "your account is
  busy, try again in 20 seconds", OwnTV is counting that down behind the spinner on purpose. That time
  is added back, so a channel queued behind a wait the app agreed to is never mistaken for a dead one.
- **The error now actually appears.** A stream that opens its playlist and then sends nothing produces
  no picture *and* no error, so nothing was there to show — the spinner simply stayed up forever once
  the last combination had been tried. Whichever player is on screen is now told to show the failure.

### 🎛️ Every playback setting in one place

- **Settings → Video player is now the complete list.** HDR, Auto frame rate, Surround sound,
  Auto-play next, Live preview, Preview sound and Mini player used to live only on the main Settings
  page, even though everything else about playback was one level down — so which screen a setting was
  on came down to memory. They are all on Video player now, grouped with the settings they belong
  next to: HDR and Auto frame rate beside Hardware decoding, Surround with the other audio settings,
  Live preview at the top of Live TV.
- **The main Settings page is shorter, and the quick chips still cover the common ones.** The
  duplicated rows are gone from the Playback group, which now holds Video player and the Playback
  error log. The chip row at the top of Settings still toggles Live preview, Preview sound, HDR and
  Auto-play next in one press.
- **Search tells you where a setting lives.** A result now reads *Playback › Video player › HDR*
  rather than just *Playback › HDR*, and typing "video player" lists everything on that screen. You
  can still flip a setting straight from the results without opening anything.
- **The warnings still appear wherever you change the setting.** Turning on Auto frame rate below
  Android 12 still asks first, and turning on Live preview while your layout has no room for the
  preview panel still says so — from the new location and from search alike.

### 🎚️ Per-playlist Live TV player and Live latency

- **Settings → Video player → "Live TV player per playlist" and "Live latency per playlist".** Both
  of these were one choice for everything, so a single provider that needed different treatment forced
  the change on all of them. Each playlist can now have its own answer, chosen the same way the
  per-playlist Pre-buffer already is: pick the playlist, then pick the value. Anything you have not
  touched says **Follow setting** and behaves exactly as before.
- **The order of precedence is unchanged where it matters.** A channel you pinned yourself still wins
  over its playlist's choice, and a protected (DRM) channel still plays on ExoPlayer regardless — mpv
  cannot obtain a licence, so that is not a preference to weigh but a fact.
- **Both settings survive backup and restore**, and a backup made before this version restores as
  "follow setting" on every playlist.

### 🎧 Remember the audio sync for one channel or one film

- **Player → Audio → "Remember this delay".** The A/V-sync nudge could already fix a stream whose
  sound runs ahead of or behind the picture, but it was forgotten the moment you moved on — so a
  channel that is always a quarter-second out had to be corrected every single time. You can now keep
  the correction for that one channel, film or episode. Everything else still follows the global
  setting, which is the point: lip-sync error belongs to the stream, not to you.
- **Nudging further while it is remembered updates what is stored**, and turning it off both forgets
  the value and puts playback back on the global setting immediately.
- **Settings → Video player → "Reset saved audio sync"** clears them all at once, with a count of how
  many are saved — the same shape as the existing saved-zoom and saved-volume rows, and reset
  independently of them. Remembered delays are per profile and ride along with a backup.

### 🏠 Turn off the video on the Home screen's big card

- **Settings → Home screen → Keep Watching → "Play video in the hero row".** The highlighted item on
  Home starts playing after a moment, which keeps a video player running the whole time you browse.
  Live preview has had an off switch for a long time; this one did not. It does now, and on a low-memory
  TV it starts **off**, since that is where a second video pipeline costs the most.

### ⏭️ Catch-up plays on to the next programme

- **A finished catch-up programme no longer leaves a black screen.** Watching something from the guide
  used to simply stop at the end of the programme, with nothing on screen and nothing to say why. It
  now continues down the guide by itself, so an evening's catch-up plays through the way live
  television would.
- **Catching up with the present hands over to live.** If the programme that follows is the one on the
  air right now, OwnTV tunes the live channel instead of asking the provider for a recording that is
  only half made — that recording would end again within seconds and drop you straight back. Where the
  guide has nothing after the programme, or a gap of more than three hours, playback stops as before.
- **It uses the switch you already have.** *Settings → Video player → Auto-play next episode* now
  governs catch-up as well as episodes, so turning it off turns this off too.

### 🪟 Mini player is one popup instead of three screens

- **Settings → Video player → Mini player is now a single panel.** It was a page of its own holding two
  rows, and each row opened another box on top of it — three levels deep to set a size and a corner.
  Size and position are now on one small popup and apply as you press, rather than after confirming.
- **The position choices are laid out like the TV screen.** Six cells in two rows — top and bottom,
  left/centre/right — so the option you highlight sits where the mini player will. **Reset** returns
  both the size and the position to their defaults in one press.

### 🏷️ The player's buttons tell you what they are

- **Every button on the player bar names itself when you highlight it.** The bar can hold twelve
  unlabelled icons, and some of them were only identifiable by pressing — including the engine
  toggle, which restarts the stream. The name now appears just above the row as you move along it.
  The line is always there, so the bar never jumps as focus arrives or leaves, and with **Reduce
  Animations** on the name appears instantly instead of fading.
- **Nothing moved.** The buttons are in exactly the same order and the same left/right groups as
  before.

### 🎨 The player follows your accent colour

- **The player was always teal, whatever accent you had chosen.** Pick violet and you got a violet
  app with a teal player: the seek bar, the active buttons, the speed and engine pills, the subtitle
  count badge and the channel-number card were all a fixed colour. They now use your accent, the same
  as the rest of the app. Custom hex accents are included.
- **It stays readable on the light theme.** The player's controls sit on dark video, so they use the
  brighter version of your accent rather than the deeper one the light theme uses on its own pages.

### ⚡ Settings opens faster, and catch-up stops redrawing the screen

- **The Settings page only builds the rows you can see.** It used to build all forty rows and headings
  every time it opened, and again on every change. Everything is where it was; it just arrives
  quicker, which is most noticeable on a slower TV.
- **A rewound catch-up programme no longer redraws the whole screen once a second.** The two clocks in
  the player tick every second, and the entire screen behind them was being rebuilt each tick just to
  move them along. Only the clocks update now.

### 🗂️ Settings is two columns, and grouped the way you'd look for things

- **The sections are on the left, their settings on the right.** Settings used to be one long column
  you scrolled through — everything from Profiles down to About in a single list. It is now the same
  shape as the rest of the app: pick a section on the left, and only that section's settings show on
  the right. Nothing is hidden and nothing moved to a different place; there is just far less
  scrolling to reach anything.
- **Nine sections instead of six.** The old grouping had one very large "Appearance" bucket and put
  unrelated things together. The sections are now Profile, Sources & guide, Appearance, Layout &
  navigation, Content & metadata, Playback, Network, Data & backup, and App. Every row kept its own
  wording, chip and behaviour — only which heading it sits under changed.
- **Coming back from a settings page returns you to the exact row**, in the right section, the way it
  always did.
- **Search is unchanged.** Typing in the search box still replaces the page with a flat list of
  matches, each labelled with the section it came from.

### 🎨 Icons that match what they do

- **Eighteen settings rows got the right icon.** Several rows were sharing a picture with something
  unrelated — Catch-up showed the guide icon, DNS showed a magnifying glass, Weather showed the guide
  icon, Network showed a share arrow, Refresh now showed a clock. Ten new pictures were drawn for the
  rows that had nothing suitable to point at: weather, network, text size, backup, refresh, power,
  motion, glow, warning and list layout. The search results show the same icon as the row they open.
- **Settings pages keep their colour.** A row's icon used to change colour the moment you opened it —
  Weather was a grey tile on the settings list and a coloured one inside. Each page's rows now use the
  colour of the row that opened it, so nothing changes underneath you.
- **One mark for "make this bigger".** The mini player and the settings list were using two different
  pictures for the same action. There is now one.
- **The favourite heart is the same colour everywhere.** It was coral on posters and in Live TV, your
  accent colour in Movies, Series and Search, and your accent colour again in the player. It is coral
  in all five places now, so one colour always means "favourite".
- **The Backup screen's buttons have icons**, like the buttons everywhere else.

### 🐛 Fixes

- **Entering a Guide programme row no longer pushes the current time to the left edge.** Guide still
  opens with Now around 37.5% across the timeline; pressing OK to browse that row keeps the same view
  and scrolls only after the highlighted programme reaches an edge.
- **Embedded TMDB trailers do less work on lower-memory TVs.** The in-app YouTube surface no longer
  builds its own controls, captions, annotations or related-video UI behind OwnTV's controls, and the
  progress display updates once per second instead of forcing ten interface updates per second.
- **Backup & Restore now preserves every Settings arrangement and global player choice.** The Live TV
  and Movies & Series player modes, Quick pinned rows and their order, and all four arranged
  long-press menus now return with the rest of App settings. Existing backups remain compatible;
  create a new backup to carry these newly covered values.
- **CH+ / CH− now always changes the channel while a Live TV or catch-up player is open.** The
  dedicated channel keys could be claimed by a focused on-screen control just after playback started,
  or briefly stop responding while the channel list or playback engine changed underneath them. They
  now belong to the player for the entire session, including channels opened from the Guide, Home,
  startup and launcher shortcuts. Browse screens keep their configurable CH+/CH− paging, media and
  colour-key shortcuts are unchanged, and D-pad Up/Down still navigates whenever the controls are visible.
- **Focus no longer slides across the screen when you leave a settings page.** Coming back from the
  bigger panels — Glass Effect, About — the highlight appeared somewhere else for a moment and then
  travelled to the row you had opened. Full settings pages such as Playlists had the same visible jump:
  the two-column Settings screen briefly rebuilt its first section before restoring the row you left.
  Both return paths now keep their section and list position still until focus has landed directly on
  the opener row.
- **Home's Now Trending showcase no longer follows the popup controls.** Trending is part of the main
  interface, but it was wrapped in the popup styling, so Popup size resized the entire showcase and
  Popup font size changed its text while the general font setting was ignored. It now follows the
  general interface font and size, while popup controls affect only actual popups.
- **Two settings put the highlight in the wrong place afterwards.** Closing **Focus highlight**, or the
  remote background-image picker, restored nothing at all — and left a marker set that sent focus to
  the wrong row the *next* time you opened Settings from the sidebar.
- **Back now goes back one level in Settings, not two.** In the per-playlist settings (Live TV player,
  Live latency, Pre-buffer) you pick a playlist and then a value; Back from the value closed both and
  left you two rows above where you started, so setting a second playlist meant beginning again.
  Back — and choosing a value — now returns to the playlist list, where each playlist's current setting
  is shown. The same applies to the background-image and OpenSubtitles sign-in steps.
- **Every series re-sync used to leave a dead resume position behind, for ever.** After a re-sync the
  app re-attaches your favorites, history and resume points to the content's new ids and drops the old
  rows. The dropping step only ever considered channels, movies and shows — never *episodes*, which is
  what almost every resume position and half the watch history actually is. So each re-sync added a
  fresh row and kept the stale one, and the table grew for as long as you owned the app. The stale rows
  are now dropped like every other kind; an episode that has not finished loading is still held safely
  and re-attached when it arrives.
- **Removing a series from Continue watching now removes it from the Home screen too.** A show is
  watched one episode at a time, so what the Home screen's Continue watching row actually shows is the
  episode you stopped in the middle of — not the show's history entry. Removing the show cleared only
  the history entry, so the episode stayed on Home and the top-bar Continue chip still offered it.
  Both now go with it. Shows removed before this version need removing once more to clear the leftover;
  and because the resume points go too, the show opens at episode 1 again afterwards.
- **The "watch next episode" box no longer pops up for the wrong episode.** Clicking *Continue* at the
  end of an episode started the next one and immediately offered the episode after that — and resuming
  a part-watched episode sometimes showed the box straight away as well. The player was still reporting
  the previous item's elapsed time and length for a second or so after a new one began, which reads as
  "eight seconds from the end". Each new item now starts on a clean clock.
- **A deleted profile's favorites can no longer surface in someone else's account.** When re-attaching
  records after a sync or a restore, a record whose profile no longer existed was given to whichever
  profile happened to be first — so deleting a profile could push its favorites, history and resume
  points, including a Kids profile's, into another person's account. Such a record is now discarded.
- **Rewinding a channel with no recording available now returns to live.** If the provider had nothing
  to serve, OwnTV kept showing "behind live" and a rewind counter over a picture that had never left
  the live edge, and hid the player's engine switch behind a rewind that did not exist. It now slips
  back to live. Opening a catch-up programme that cannot be served tunes that channel live instead of
  naming it over the previous channel's picture.
- **Choosing a category right after typing a channel number sticks.** Entering a channel number
  rebuilds the zap list in the background. If you opened the in-player category browser and picked a
  category while that was still running, the older rebuild landed on top a second later and silently
  replaced the category you had just chosen.
- **Your audio sync survives a stream reconnecting.** A channel that dropped and recovered — or that
  moved to the other decoder — reset the A/V-sync offset to the global default and only put your own
  value back once it had been re-read from storage, which was audible as a sync jump. An adjustment you
  had not asked OwnTV to remember was lost outright. Zoom and volume already survived a reconnect;
  audio sync now behaves the same way.
- **The offline warning reads the connection you are actually using.** On a TV with both an Ethernet
  cable and Wi-Fi connected, a change on the idle one could publish its state over the working one,
  showing an offline warning over a perfectly good connection until the next check corrected it.
- **Auto frame rate now respects your TV's own "Match content frame rate" setting.** If you had
  turned frame-rate matching off in Android TV's display settings, OwnTV changed the display mode
  anyway — it was using an older method that the system does not police. Set to **Never**, OwnTV now
  leaves your display alone and stops offering to switch; set to **Seamless only**, it restricts
  itself to changes your TV can make without the brief black gap of an HDMI re-handshake. **Always**
  behaves exactly as before, as does any TV on Android 11 or older, which has no such setting.
- **Nothing from the previous channel or film can survive into the next one.** Everything the player
  had to forget when you changed channel was a long hand-written list, and anything left off such a
  list bleeds into whatever you open next — a stale error, a stale resolution badge, a retry the new
  channel had not actually used. Both players now forget in one step that cannot be half-done. The
  distinction that matters is preserved: a silent retry of the *same* film still remembers what it has
  already tried, or it would retry the same failing trick forever.
- **Live latency now admits what a 4K channel can actually buffer.** Asking for a long buffer on a
  very high-bitrate channel gives you less time than the number suggests, because there is a limit on
  how much video can be held in memory at once. That was always true and correctly handled; the
  setting simply never said so, which made it look broken. It now explains it.
- **A film with picture-based subtitles is less likely to be given up on early.** The player waited 8
  seconds for the first frame, measured from before it had even connected — inside the normal opening
  time for a large 4K file on a slow provider. It now waits 12, the same as the Home screen preview
  has always used, so a slow-starting film is no longer restarted or reported as "audio, no picture"
  when it was about to play.
- **ExoPlayer no longer drops a working movie or episode into mpv after switching surround to stereo.**
  Rebuilding ExoPlayer's audio output also replaces its hardware video decoder; on some TCL/Realtek TVs,
  doing that immediately on the same video surface left the replacement decoder playing sound over a
  blank picture. Recovery now releases the old decoder, allows it to settle, recreates the video surface,
  and resumes at the saved position in ExoPlayer. The same safe restart protects its hardware-to-software
  decoder rescue, while mpv's independent stereo recovery remains unchanged.
- **"App not installed" when updating from inside OwnTV.** The app downloaded the new version and
  handed it straight to Android without checking anything, so a download that arrived incomplete —
  easy on a TV that is low on storage or on a weak connection — was rejected by the system with a bare
  "App not installed" and no way to tell why. OwnTV now checks there is room before it starts, checks
  the download arrived whole, and checks Android can actually read the file as OwnTV before offering
  it for installation. If any of those fail you get a plain message saying what to do — free up space,
  or check your connection and try again — and the bad file is deleted rather than retried as-is.
- **An update that the system refuses now says why.** Installing goes through Android's own installer
  service instead of opening the downloaded file as a document, which means OwnTV can read back the
  real reason a package was turned down (not enough space, a signing mismatch, a damaged file) and show
  it. Cancelling the system's confirmation prompt is treated as a choice, not an error.
- **The first-run "Add a playlist" screen no longer hides Stalker.** The **New** card described itself
  as "Add an M3U or Xtream source" even though the form behind it has offered Stalker portals for
  several releases. It now reads "Add an M3U, Xtream or Stalker source", in every language.
- **Fast-forward and rewind from a Bluetooth remote or the system media notification now use your Seek
  step.** Those two routes had a fixed 30 seconds built in, so a remote's skip buttons moved by a
  different amount than the on-screen ones — with Seek step set to 10 seconds, the buttons jumped 10
  and the remote jumped 30. Both now read the setting.
- **A frozen live channel moves to the other player about twice as fast.** The wait before handing a
  stalled channel over was a flat 30 seconds, while the player underneath had already declared the feed
  dead at 12 seconds and quietly retried twice in the meantime. The wait is now worked out from that
  verdict — about 15 seconds — which leaves room for one genuine recovery and not for two that have
  already failed.
- **The offline warning now appears when the internet is actually unreachable.** OwnTV only checked
  that a network interface claimed to carry internet, which an Ethernet cable plugged into a dead
  router does forever. It now also requires Android's own confirmation that traffic reached the
  outside, so unplugging the router shows the warning instead of silently failing every request.
- **Online subtitle searching now filters correctly for seven more languages.** Bengali, Czech, Danish,
  Malayalam, Norwegian, Polish and Swedish were missing from the code-conversion table, so choosing one
  as your search language quietly returned unfiltered results.
- **Track menus stop hunting for tracks that will never arrive.** Opening the audio or subtitle menu on
  a stream that has none — a radio channel, most often — left OwnTV re-checking three times a second
  for as long as the menu stayed open. It now stops after six seconds, which is well past the slowest
  stream that has ever reported its tracks late.
- **The in-player channel list no longer mislabels itself.** Opening a channel from the catch-up
  programme dialog reset the list's heading to "All channels" while the list underneath was still
  showing Favorites, History or whatever rail you came from. The heading now always names what is
  actually in the list.
- **One less timer running while you watch a recording.** Rewinding a live channel after watching a
  catch-up programme left two identical once-a-second counters running side by side for the rest of the
  session, both working out the same thing. There is one now.
- **Less background work during playback, and less of it on weaker TVs.** Several separate timers
  driving the progress readout, the stream-info panel and the frame-rate chip have been merged into a
  single once-a-second tick, and on low-memory devices the "is this channel frozen?" check now runs
  every 4 seconds instead of every 2.5 (reaching the same verdict in 12 seconds instead of 10). The
  still picture shown while the app swaps video players is also captured at a fraction of its old size
  — on a 4K stream it used to allocate about 33 MB for a placeholder shown for a quarter of a second.

## v4.2.2 — 2026-08-19

### 🖼️ Episode grid — see a picture for every episode

- **A new Grid / List button in a show's episode view.** Grid replaces the text rows with a wall of
  16:9 episode stills, so you pick an episode by what it looks like rather than by its number. The
  choice is remembered globally and applies to every show, and List stays the default so nothing
  changes until you switch.
- **Episodes TMDB doesn't have still get a usable tile.** IPTV catalogues name episodes in ways TMDB
  often can't match, and providers supply no episode artwork at all. Those tiles fall back to the
  show's own wide banner (or its poster), with the episode number drawn large across the middle so
  the grid stays navigable when every tile looks alike. In **Provider only** metadata mode every tile
  uses the show's artwork this way, and no lookups are made.
- **Watched ticks, the "last watched" badge and the part-watched progress bar all carry over** from
  the list, so nothing is lost by switching layout.

### ⚡ A whole show's episode details now arrive in one request

- **Opening a series used to cost one metadata lookup per episode you scrolled past** — roughly 120
  for a five-season show, and a third of the daily allowance for a single title. Details for every
  season now arrive in **one** request, folded into the show lookup that already happens. A grid of
  episode pictures is only affordable at all because of this.
- **Switching between seasons is instant and free** once a show has been opened; only shows longer
  than ten seasons fetch again, and only if you actually browse that far.
- **The shared metadata service now remembers a title for six months instead of one**, matching how
  long the app keeps it, and requests are grouped so one viewer's lookup serves everyone else's.
- **Duplicate entries no longer pay twice.** IPTV catalogues routinely list the same show or film in
  several categories; the second copy now reuses the details the first one downloaded.

### 🎯 Choose the colour and thickness of the focus highlight (#121)

- **Settings → Appearance → Focus highlight sets the ring drawn around whatever the remote is
  pointing at.** The old ring was a thin 2 dp accent line, which is easy to lose from sofa distance
  on a wall of bright posters. Pick a colour from eight presets, from the full palette, or by typing
  an exact hex code, and pick a thickness — Thin, Normal, Thick or Extra thick. A live sample inside
  the dialog shows the result before you commit.
- **One setting covers the whole app.** Live TV, Movies, Series, Home, the TV Guide, Downloads,
  Search, Settings rows, the category column, the navigation rail, buttons, text fields and every
  popup all follow it. Thicker rings also open up the surrounding glow, so *Extra thick* reads as a
  halo rather than just a fatter line.
- **It works with the Glass Effect on.** In glass mode the frosted rim is the focus ring, so the rim
  now takes your colour and thickness instead of always being white.
- **Only the highlight changes.** The accent colour still owns buttons, chips and panels, so a loud
  focus colour does not repaint the rest of the interface. The default is unchanged, and the choice
  is included in Backup & Restore.

### 🐛 Fixes

- **The advanced TMDB setting is no longer labelled "via remote".** It was called *Get advanced TMDB
  info via remote*, but you can equally type the key in on the TV, so the label described only half
  of what it does. Inside it, the option that hands the details over from a phone or computer had the
  same name as the panel it sat on; it is now *Get key from another device*.
- **Episode pictures no longer reload when you return to a season you have already opened.** The
  cached information was being read one episode at a time, on the same thread that draws the screen —
  so it queued behind the very grid it was filling. Seasons now appear immediately.
- **Back from a show now returns focus to that show, instead of jumping to the category sidebar.**
  Only the poster-grid layout was being scrolled back into view, so in the list layout the show was
  never on screen to receive focus. The identical fault in Movies — returning from a film in list
  view — is fixed too.

### 👶 Kids profiles hide adult content

- **Kids mode now hides adult provider categories and their items throughout OwnTV.** The same
  profile rule covers Live TV, Movies, Series, Home, Search, the TV Guide, Catch-up, Downloads,
  custom categories, Android TV recommendations and direct/deep-link playback. The Guide is hidden
  completely for a kids profile. Normal profiles keep the provider's full catalogue.
- **TMDB search now follows the active profile.** Adult TMDB results are excluded only while Kids
  mode is on; a normal profile never has them silently removed.
- **Adult folders are recognized from the names supplied by the IPTV provider**, using common
  multilingual adult markers plus labels such as `18+` and `XXX`. `Adult Swim` is explicitly not
  treated as adult content. IPTV formats do not provide a trustworthy universal adult flag, so
  misleading or uncategorized provider content cannot be identified perfectly.

### 🚀 Start OwnTV on a specific Live TV channel

- **App startup has a new Specific channel choice for each profile.** Its D-pad-friendly picker has
  a search bar, and the saved channel is resolved by the provider's stable ID, then its name, with
  the local row ID as a final fallback. The choice is included in Backup & Restore.
- **A single unlocked profile can start playing its chosen channel immediately.** Multiple profiles
  still show “Who's watching?”, and a PIN-locked profile still waits for authentication before any
  channel starts.
- **Unavailable or restricted startup channels fail safely.** Hidden channels, channels blocked by
  Kids mode and channels from a disconnected source are never auto-played. If the saved channel no
  longer exists, OwnTV opens Home and explains what happened.

### ⏪ Catch-up without a TV guide — a Catch-up category and "Go back to…"

- **Live TV has a new Catch-up category**, between History and All, listing every channel your
  provider keeps a recording for. It appears only if you actually have such channels, and it is a
  filter over the channels you already have — nothing extra is downloaded, and it stays correct after
  every playlist refresh. Sorting, the inline search and the in-player channel list all work in it.
- **You can now jump straight to a time instead of holding rewind.** Catch-up channels get a
  **Go back to…** button in the player, and the same list appears when you open Catch-up on a channel
  with no guide data. It offers times counted back from now — 21:30, 19:00, `Sun 20:00` — so reaching
  three hours back is one press instead of holding a key while a counter crawls. Times, not "3 hours
  ago", because you are usually looking for the programme that started at a particular clock time.
- **"Choose exact time…" reaches any moment your provider still holds.** The last row of that list
  opens a day, hour and minute picker, so "yesterday at 10:31" is reachable and not just the round
  offsets. Press **OK** on the day, hour or minute to step into it, change it with up and down, then
  **OK** or **Back** to step out; left and right move between the three. The wheels stop at both ends
  of your archive — you cannot scroll past the live edge into the future, and you cannot scroll off
  the far end into a request that could only fail.
- **None of this needs a TV guide.** Rewinding a channel never did, but the only thing named
  "Catch-up" was the programme list, which does — so opening it without a guide produced a dead end
  advising you to go and fix your EPG, and the feature looked missing. That screen now offers times.
  With a guide, the programme list is still shown: it has titles, which is better.

### 🕐 A clock in the player — and, on catch-up, the time the programme actually aired

- **Every player now shows the time and date**, centred at the top: Live TV, Movies, Series and
  catch-up alike. It sits in a band that was empty in every mode, so nothing else on screen moved to
  make room for it.
- **While you replay a recording, a second clock appears beside it.** The panel then reads
  **Programme time** on the left — when what you are watching originally aired, counting forward as it
  plays — and **Current time** on the right. Without the pair, a clock reading 10:00 over a picture
  from yesterday afternoon would be worse than no clock at all. Both columns are labelled, so a lone
  time is never mistaken for a wrong device clock.
- **The guide card gains a matching row during catch-up.** Above the live programme it now shows
  **Playing** and **Then** — what was on air at the moment being replayed, and what followed it — so
  you can see what you are watching even when you jumped to a bare time. The live row stays where it
  is, dimmed, and returns to full strength the moment you go back to live. Channels whose guide comes
  only from the provider's now/next API get no archive row: that API cannot describe the past.
- **The live guide labels now read "Live now" and "Live next"** rather than "Now" and "Next", so they
  cannot be confused with the archive row sitting directly above them.

### 🧾 Dedicated Metadata and OpenSubtitles settings

- **Metadata and OpenSubtitles now have separate, purpose-built settings pages.** OpenSubtitles sits directly below Metadata in the main Settings list instead of being buried under Video Player. Both pages use compact status cards and keep secondary setup inside shared, D-pad-safe popups.
- **The shared metadata allowance is easier to read.** Built-in service users see separate minute, hourly and daily remaining cards plus the refill time. Personal keys and custom servers are identified as the active source without exposing a full key.
- **Both services now support advanced custom access.** TMDB and OpenSubtitles each accept a personal API key or Worker/server URL in a compact popup; a URL takes priority over a key, while leaving both blank uses OwnTV's built-in service. OpenSubtitles custom access is included in Backup & Restore.

- **Your daily share is laid out as a proper status panel.** Source, then the remaining lookups for
  each window and the refill time, each as a label on the left and a value on the right — the same
  shape as the OpenSubtitles account panel, instead of a loose block of text wedged between two
  settings.
- **The active source is always shown, whichever one you use.** With your own TMDB key it shows the
  key masked to its last four characters, so you can tell two keys apart without exposing one on
  screen; with a self-hosted server it shows the address. The daily-share rows appear only on the
  shared service, which is the only one that is metered.

### 📱 Send TMDB or OpenSubtitles access from another device

- **OpenSubtitles sign-in can be filled in from another device, password included.** Choose **Remote**
  and the browser page now asks for your OpenSubtitles username and password as well as the optional
  API key and Worker/server URL — so none of it has to be typed with the remote. The details land in
  the sign-in panel on the TV and wait there; you still press **Sign in** yourself.
- **Setting up OpenSubtitles is one screen instead of several.** **Sign in** first asks how you want to
  enter your details — **Remote** or **Enter here** — and then shows a single compact panel holding your
  username, password, "Stay signed in", and the optional API key and server URL underneath, marked
  optional. The advanced fields are no longer a separate popup, and the duplicate "Advanced options"
  row that appeared both on the screen and inside the form is gone.
- **The Remote companion now accepts complete service access.** For TMDB, open Advanced options, scan
  the QR code, enter the PIN, and send an API key plus an optional Worker/server URL from any browser
  on the same Wi-Fi — phone, tablet or PC. The TV fills the fields but waits for **Save**, so
  configuration never changes behind your back.

- **A personal TMDB key no longer means typing 32 characters with a remote.** Under
  **Settings → Metadata → Advanced options** there is now **Get key from another device**: the TV shows a
  QR code and a PIN, you open it on a phone, tablet or computer where typing is easy, sign in to TMDB, paste the key
  and send it across. It lands in the key field on the TV; you still press Save, so nothing is
  changed behind your back. This matters because a personal key is free and has practically no daily
  limit, while the built-in shared service has to be rationed between everyone.
- Uses the same Remote link as the other remote-companion features, with the same protection: the QR carries
  only the address, never the PIN, and the listener closes as soon as the panel does.

### 🔐 Your data no longer leaves the TV without a backup password

- **Android's automatic backup is switched off.** The app was letting Android copy its own database
  and settings to Google Drive, and to a new device during setup transfer — including playlist
  passwords, the proxy password and API keys, all in plain form, with no backup password anywhere in
  the process. That directly contradicted the promise the app already made: without a backup password,
  secrets are left out. **Settings → Backup & Restore is now the only way OwnTV data moves between
  devices**, and it is explicit and encryptable. Device-to-device transfer is closed too — it keeps
  running on Android 12 and newer even when Drive backup is off, so switching one off was not enough.
- **Profile and Customize PINs are treated as secrets.** A PIN is stored as a scrambled value, but a
  four-digit PIN has only ten thousand possibilities, so that value is trivially unscrambled by anyone
  holding an unencrypted backup file. PINs now follow the same rule as every other secret: included
  when you set a backup password, left out entirely when you don't. Restoring a passwordless backup
  never *removes* a PIN you already have on the device — it simply doesn't carry one in.
- **Per-channel "compatibility mode" settings stay out of an unencrypted backup when they identify a
  stream by its address**, because provider addresses routinely contain the account's username and
  password.

### 🗃️ Backup & Restore now really does back up everything

- **Two per-playlist settings were being lost.** **Prefer HLS** and the per-playlist **Pre-buffer**
  override were never written into the backup, so a restore silently returned them to their defaults —
  quietly reintroducing whatever streaming problem you had already fixed on that playlist.
- **Downloaded subtitles are included, files and all.** Your saved subtitle choice per film or episode,
  the timing offsets you nudged by hand, and the subtitle files themselves now travel in the backup, so
  a restored film plays with the same subtitle, already in sync. Previously none of it was backed up:
  the app remembered a subtitle whose file did not exist on the new device.
- **The profile you were using is remembered.** A restore onto a fresh device used to land on whichever
  profile happened to come first, which in a household with a kids profile could be the wrong one.
- **Where you left off in each section is remembered**, for the sections whose position can be
  meaningfully restored.
- **The active profile now starts ticked when choosing what to back up.** Every section was selected by
  default but every profile was not, so it was possible to tick "everything" and still produce a backup
  containing no profile data at all.

### 🔤 Subtitle font selection — plus Monospace throughout the app

- **Subtitle typography can now be chosen independently in Settings → Video Player → Subtitle
  appearance.** Choose Default, System Sans, Monospace, Lora, Playfair Display, Dancing Script or
  Poppins. Default preserves the stream's authored or broadcaster styling.
- **The selected font is applied consistently across both playback engines and app-drawn subtitle
  overlays:** mpv, ExoPlayer/Media3 and the docked-player overlay all use the same preference.
- **Monospace is also available for the main interface and popup menus.** The subtitle-font choice
  survives restarts and is included in Backup & Restore with the other appearance settings.

### 🎛️ Choose the playback engine — four options, for Live TV and for Movies & Series

- **Settings → Video Player now has a Live TV player setting, and the Movies & Series player setting
  has grown from a switch into the same four choices:** *ExoPlayer, then mpv* · *mpv, then ExoPlayer* ·
  *ExoPlayer only* · *mpv only*. Each list marks its own default, because the two sections differ on
  purpose: Live TV starts on ExoPlayer, which opens channels far faster and is the only engine with
  live subtitles, while Movies & Series starts on mpv, which supports more formats. Nothing changes
  for anyone who leaves them alone — the old Movies & Series switch carries its setting over.
- **The two "only" choices stop the automatic switch between players.** Handing a channel from one
  engine to the other costs a stop, a release and a re-open — several seconds of black screen — which
  is wasted on a TV or a provider where the second engine was never going to work anyway. "Only" means
  only: no switch after a decode failure, an account-busy refusal or anything OwnTV worked out for
  itself. It still tries that engine's own `.m3u8` and `.ts` variants, which is what rescues most
  channels; what it drops is the other engine.
- **The compatibility-mode button in the player now stays where you put it.** Switching a live channel
  back to ExoPlayer by hand could be undone by OwnTV about two seconds later, over and over, on TVs
  where neither engine can decode a channel's audio — the button looked broken and there was no way to
  remain on the chosen engine. Your choice now holds for that channel, and is remembered for next time
  in both directions (previously only a choice of mpv was remembered).
- **A channel or item you switch by hand always outranks the setting**, including the "only" choices,
  so a single awkward channel never leaves you stuck: pick the other engine for that one and the rest
  keep following your setting. Live TV's per-channel choices travel in Backup & Restore alongside the
  Movies & Series ones.

### 🔒 Protected (DRM) channels now play — Widevine and ClearKey (#115)

- **OwnTV can now play channels and films protected with Widevine or ClearKey.** Some providers —
  including self-hosted setups such as JioTV-Go — publish MPEG-DASH channels that are locked, with the
  unlock address written into the playlist. Until now OwnTV threw that address away while reading the
  playlist, so those channels simply failed with a general playback error and looked broken.
- **Nothing to set up, and nothing to buy.** The unlocking is done by the component already built into
  every Android TV and Fire TV device; OwnTV just has to ask it. There is no key to enter, no account,
  and no licence for you to purchase — if your playlist carries the details, the channel plays.
- **Protected channels always use ExoPlayer.** mpv has no way to request an unlock key, so a protected
  channel goes straight to the player that can, without the usual switch between players first. The
  compatibility-mode button is hidden for those channels, because its other position could only fail.
  Everything else keeps following your engine settings exactly as before.
- **Works for Live TV, Movies and Series**, and for both `.mpd` (DASH) and protected HLS streams.
- **Two things are worth knowing.** Older or cheaper TV boxes may only be allowed to play protected
  channels in standard definition — that is decided by the device, not by OwnTV. And a protected item
  always plays inside OwnTV even if you have chosen an external player, because no external player can
  be given the unlock address.

### 📁 Send a playlist file from your computer with Remote

- **The Remote page can now upload a playlist file.** Its M3U tab said “Playlist URL or local file” but
  offered no way to choose a file — the wording came from the TV's own screen, where a file picker does
  exist. There is now a real **Or upload a playlist file** button next to the address box.
- **Useful when the playlist only exists on your computer**, with no web address to point at and no
  wish to copy it onto a USB stick. Choose the file, press Send, then press Start Import on the TV as
  usual. Typing an address still works exactly as before.
- The uploaded playlist is kept on the TV until you import it, so there is no rush between sending it
  and picking up the remote. Only the few most recent uploads are kept.

### 🐛 Fixes

- **"Couldn't reach OpenSubtitles" no longer blames your internet when the connection is fine.** Every
  possible failure showed that one message — a refused request, a rate limit, a server error, and a
  genuinely dead connection alike — which sent people looking in the wrong place. A server that answers
  and declines now says so and shows its error number, and the cause of a real connection failure is
  written to the log, so a report can actually be diagnosed instead of guessed at.
- **OpenSubtitles sign-in now tries the server's other address.** OpenSubtitles is reached through an
  address that resolves to both IPv4 and IPv6. On a network that advertises IPv6 but cannot route it,
  the first attempt failed and OwnTV gave up instantly — so signing in was impossible while everything
  else on the TV worked normally. Subtitle requests now fall back to the next address.
- **The Remote companion is no longer described as a "phone" feature.** It works from any browser on the
  same Wi-Fi, so the app, the guide and the setup pages now say phone, tablet or computer throughout.

- **Restoring a backup no longer gives every Stalker playlist the same MAC address (#114).** Several
  Stalker playlists usually share one portal address and have no username, so a restore treated them
  all as the same playlist: they were merged onto one, each overwriting the previous one's MAC, and
  their favourites, history and folder customizations were merged onto it too. Restore now also looks
  at the MAC, and each saved playlist can only match one playlist on the device. A backup saved
  *without* a password still carries no MAC at all — MACs are treated as secrets — so those restores
  keep the MACs already on the device instead of duplicating the playlist.

- **Your DNS choice now survives an app restart.** Selecting Google, Cloudflare, Quad9 or entering a
  custom DNS server was saved correctly, but the settings screen opened from an empty startup value
  and never refreshed when that saved choice arrived. The screen now restores the saved server and
  enabled state as soon as settings load, without overwriting normal editing.
- **“Audio only” no longer flashes while an ordinary TV channel is starting.** Some providers announce
  the audio track before the video track, so v4.2.1 briefly treated every channel as a radio station
  and removed the message only when the picture arrived. OwnTV now waits for a ready stream to remain
  audio-without-video for five seconds; any video announcement cancels the message immediately, while
  genuine radio channels still receive the persistent explanation.
- **Ambient Glow now appears only with the explicit Dark theme while Glass Effect is off.** Turning
  Slow pulse off leaves the soft glow in place without the distracting outline circle; turning it on
  adds the moving pulse ring.
- **A preferred subtitle language now turns a matching subtitle track on automatically on both
  players.** Language variants such as `eng`, `en` and `en-US` match correctly, including when the
  audio uses the same language, while streams without a match do not enable an unrelated subtitle.

- **"Watch from start" needed two presses.** Opening a programme from the Guide or from the Live TV
  catch-up list, the first press of **Watch from start** did nothing and only the second one played.
  The dialog swallows OK until it knows the button that opened it has been released, so that a held
  press cannot instantly trigger whatever is focused — but the press that opens the dialog is acted on
  as the button goes *down*, so the release happened while the dialog was still appearing and was
  never seen. It waited forever, and ate the next real press. It now also treats a moment's silence as
  proof the button is up, which a held button cannot produce. Affects every dialog with that guard.

- **Per-channel and per-item playback settings attached to the wrong playlist after a restore.**
  "Compatibility mode" for a channel, and the zoom or volume boost you saved for a particular film,
  are stored against the playlist the item came from. A restore was not translating that playlist's
  internal number to its number on the restored device, so those settings either did nothing or —
  when the number happened to belong to a different playlist — were applied to somebody else's
  channels entirely.
- **Restoring onto a device that already had the playlist discarded the backup's settings for it.**
  Only credentials were applied; the playlist's name, its Live/Movies/Series scope, Prefer HLS and
  Pre-buffer were left at whatever the device already had. The same applied to a guide feed already
  present under the same address, which kept its local name and user agent.
- **The default playlist could be repointed at an unrelated playlist.** When the backup's default
  playlist was not part of the restore, its stored number was used as-is, and it matched whichever
  unrelated playlist happened to hold that number on the device.
- **Backup data belonging to playlists that were not part of the backup is no longer written into the
  file**, where it could not be translated on restore and could collide with unrelated playlists.
- **Startup screen and the Customize PIN lock were filed under "Sources".** Deselecting Sources —
  described as playlists, guide feeds and credentials — silently dropped both. They now travel with
  Settings, where they belong. Backups made by older versions still restore from either place.
- **Turning "Advanced options" off now actually stops using your own key.** It previously just hid
  the fields while quietly leaving the saved key in force, so the screen still reported "Your TMDB
  key" with nothing on screen to explain why. It now asks for confirmation, then deletes the saved
  key and server address and returns to the built-in shared service.

- **Downloaded subtitles are now told apart, and subtitle timing changes the one you selected.**
  Every downloaded subtitle was named only by its language, so three Korean downloads appeared as
  three identical rows — and because that name was also how OwnTV identified them internally, it
  could only ever find the first. Selecting the second and adjusting its timing shifted the first
  one's file and switched you to it, which made subtitle timing unusable whenever you had more than
  one subtitle in a language. Downloads now read `OS_Korean · WEB-DL.NF`, showing the release they
  came from, with locally imported files marked `LOCAL_` instead. Because the name is checked against
  every subtitle already in the video, a track inside the file can no longer be mistaken for a
  download either — previously it could be selected in place of the subtitle you just downloaded, be
  labelled as coming from OpenSubtitles, or, on the mpv engine, stop your download from reappearing
  at all when the film reloaded. Timing offsets you saved before this fix were stored against the
  wrong subtitle; reset the timing once on those and it will stay correct.

## v4.2.1 — 2026-08-15

### 🎧 "Audio only" — sound with no picture is now labelled, not mistaken for a fault

- **A radio channel, or a music-only file filed under Movies, now says so on screen.** These items have
  no picture at all, which is perfectly normal — but sound over a black screen looks exactly like a
  broken player, so it was reported as one. The player now draws a small **Audio only** plate in the
  middle with a one-line explanation, and it stays there for as long as the item plays: a message that
  disappears after a few seconds leaves the same black screen behind it for whoever looks next. Docked
  in the mini-player it shrinks to just the music icon. It appears on every engine and on Live TV, so a
  radio station in a TV playlist is covered as well as a movie.
- **A music-only movie no longer fails at about six seconds.** OwnTV used to watch for "loaded, but no
  picture" and treat it as a broken file. That check now stands down once it can see the item genuinely
  has no video track and the sound is playing — the same rule Live TV already used for radio channels.

### 🔊 Zoom and volume are now remembered per item

- **A zoom or a volume you set is kept for that film, episode or channel.** Stretch a 4:3 channel to
  fill the screen, or push a quiet film up to 130%, and it opens that way every time from then on. It
  is remembered per profile, and only for the item you changed — the channel next to it is untouched.
  Only a change *you* make counts: the player lowering its own volume for a notification, or moving a
  stream to the other engine, never teaches it a preference. Mute is deliberately not remembered, so a
  channel can never open silent.
- **Settings → Video player → Default volume** sets the starting level (0–150%) for everything you
  haven't adjusted individually — useful if a whole provider runs quiet.
- **Two separate resets, right below it.** **Reset saved zoom** and **Reset saved volume** each show how
  many items they will clear and ask before clearing, and each leaves the other list alone.
- Both lists are carried in **backup and restore**.

### 🎭 Cast photos in TMDB details

- **Cast is now shown as photos, not just a list of names.** The full-screen TMDB details window
  now shows each credited actor as a portrait with their name underneath, wrapping onto as many
  rows as needed so the whole cast is reachable with the same up/down scrolling as the rest of the
  window. Actors TMDB has no photo for show their initials instead of an empty box. The photos come
  from TMDB's image server, which needs no API key and does not touch the shared metadata service,
  so this costs nothing against anyone's allowance and adds no extra lookups — the photo
  addresses were already arriving with the details the app downloads and were simply being discarded.

### 🏷️ A fair daily share of the built-in metadata service

- **Each device now has its own daily allowance, and can see it.** The built-in metadata service is
  shared by everyone using OwnTV, and previously nothing stopped a single device consuming all of it.
  Each installation now gets its own allowance, shown under **Settings → Metadata** along with the
  time it refills. The row appears only when you are using the built-in service — your own TMDB
  API key or your own server is your resource and is never counted.
- **Running out now tells you, instead of failing silently.** If the allowance is used up, a message
  appears once per app start explaining what happened and pointing at Settings → Metadata. Posters
  and descriptions from your own playlist keep working exactly as before; only the extra TMDB
  information pauses until the allowance refills.
- **Far fewer lookups in the first place.** Scrolling through a grid no longer fires a lookup for
  almost every card it passes — the app now waits until you settle on something. Downloaded details
  are kept for 180 days instead of 60. And changing the metadata language no longer throws the whole
  cache away: details are now stored per language, so switching language, or switching back, is
  instant and costs no re-downloads at all.

### ✨ New features

- **Seek step** (Settings → Video player) — how far the rewind/forward buttons and the seek bar move in
  a film or episode: 5, 10, 15, 30 or 60 seconds. Default 10 seconds, exactly as before.
- **Live rewind step** (Settings → Video player) — the same choice for the catch-up archive buttons on a
  live channel: 10, 15, 30, 60 or 120 seconds. Default 30 seconds, as before. They are two separate
  settings because stepping through a film and stepping back through a live archive are different jobs.
- **Deinterlacing** (Settings → Video player, Off by default) — smooths the comb-shaped lines some old
  interlaced channels show on movement. It only has an effect when OwnTV draws the picture itself
  (hardware decoding off, or after a software fallback); on the normal direct-to-screen path the video
  goes to the TV untouched and no filter can run. The setting says so where you turn it on.

### 🐛 Fixes

- **Your resume position is saved reliably again — and never lands in someone else's profile.** OwnTV
  matched "what is playing" against a web address. That broke in three ways: open a different series
  while an episode plays and it quietly stopped saving; on Stalker/MAC portals the address is minted
  fresh on every play, so those movies never saved a position at all; and switching profile mid-item
  wrote your position into the *new* profile's Continue Watching. Playback identity is now pinned when
  you press Play, so none of that can happen. Your place is also saved the moment you pause and when
  you leave the player, not only every ten seconds.
- **A dying live channel now ends with a message instead of reconnecting forever.** A channel that
  dropped mid-programme reconnected without any limit, behind a spinner, with nothing on screen ever
  explaining why — Back was the only way out. It now gets the same bounded number of attempts as every
  other recovery path and then says the connection was lost. A film that is cut off mid-stream gets one
  silent retry from where it stopped and then an honest error, where before the picture simply froze.
- **Channels that need a custom User-Agent or Referer survive Retry and the screensaver.** The first
  open sent those details; pressing Retry, or coming back from the screensaver, re-opened the channel
  with the address only — so the provider refused exactly the channels that needed them most. Both
  paths now replay the full request. The same applies to a movie restored after the screensaver,
  including Stalker items, which can now mint a fresh link instead of replaying an expired one.
- **Live rewind no longer leaves a phantom "behind live" counter running.** Rewinding into a channel's
  archive and then changing the playback engine threw you back to the live edge while the counter kept
  ticking upward against a stream that was no longer the archive. Compatibility mode is now hidden
  while you are rewound (matching how channel-number tuning already behaved), the rewind state is
  cleared whenever the channel restarts at the live edge, and leaving full screen stops the counter.
- **The Home screen poster no longer sticks on a spinner.** A hero preview that connected but never
  produced a picture disarmed its own timeout, so the spinner stayed over the poster indefinitely.
- **Catch-up you watch inside OwnTV now appears in History.** Only the external-player path recorded it,
  so replaying a programme in the app left the channel out of History and Recently watched.
- **Opening a channel full screen from a catch-up programme keeps CH+/CH− working.** That route started
  playback with no channel list behind it, leaving the zapping keys and the channel-list button dead
  until the next ordinary tune. The in-player list also names **Favorites** and **History** properly
  instead of calling both "All channels".
- **Cancelling Move mode restores your sort order.** Starting a manual reorder switched the list to
  playlist order — and Cancel left it there, silently changing a setting you never touched. Applies to
  Live TV, Movies and Series.
- **Retrying a live channel keeps everything the first attempt had.** Four of the retry paths never
  re-armed the "opened but never started" timeout, so a stalled retry could hang with no error; a
  decoder rebuild came back without your audio/subtitle language preferences and with the picture
  re-enabled behind Audio Mode; a re-tune of the same channel dropped its volume boost; and stopping a
  channel left the previous one's resolution badge on screen.
- **Subtitle fixes.** Rejecting a bitmap subtitle (the audio format can't be handed over) no longer
  reports your working text subtitle as switched off; picking a bitmap subtitle on Live TV, where it
  cannot be drawn, now says so instead of showing it as selected and displaying nothing; clearing a
  preferred audio or subtitle language now actually clears it instead of applying until restart; and a
  subtitle picked on one engine is no longer silently replaced with an arbitrary one in another
  language when the track lists don't line up.
- **"Stay signed in" now covers subtitle downloads.** An expired OpenSubtitles token failed the download
  with "session expired" even when the stored password could renew it. Deleting all subtitles for
  Movies no longer removes the same file from Series, and a failed search no longer writes the title
  and file fingerprint into the device log.
- **A subtitle search that fails now falls back to a title search.** Only an *empty* result did, so a
  network blip on the id lookup showed "no subtitles" for a title that has them.
- **Catch-up URLs build correctly for playlists that already carry a token.** A catch-up template
  appended without its own `?` was joined straight onto the existing address, producing a malformed
  URL the archive answered with "not found".
- **Auto frame rate no longer leaves the previous item's refresh rate on the display,** and the
  one-time "Auto frame rate would help here" suggestion no longer returns after you enable it and
  later turn it back off.
- **The playback error log survives a corrupt file.** One unreadable entry silenced all further logging
  for good; clearing the log could also race an entry being written. Exporting the report on Android
  8–9 without storage permission now saves to the app's own folder and names that location, instead of
  failing with nothing to show.
- **Audio focus is released while you are paused,** so other apps are no longer left ducked for as long
  as the player sits paused, and a volume change made while OwnTV was ducked by another app is kept
  instead of being undone.
- **Settings → Audio sync now covers the same ±5 seconds the player does** (it stopped at ±2), the
  guide time-zone chip no longer flashes "Manual" before your real setting loads, and backing out of
  the **Custom** live-latency dialog no longer leaves you on Custom with a value you never chose.
- **Custom DNS resolves IPv6-only hosts,** which the plain-DNS path could not (it asked for IPv4
  records only, while DNS-over-HTTPS asked for both), and each query now carries a random, verified
  identifier instead of a fixed one.
- **One channel refusing a request no longer sends a whole provider down the slow path.** When a panel
  answered "too many requests" during quick channel-hopping, OwnTV read that as "this channel has no
  HLS version" and wrote it down — and after three channels it had condemned the entire playlist to the
  slower format for the rest of the session. A refusal is now understood as a refusal: it never becomes
  a lesson about the format, and what OwnTV *does* learn about a channel now stays with that channel.
- **Holding CH+ or CH− changes channel once.** A held key repeated about six times a second and every
  repeat opened a stream — thirteen channels opened in under four seconds, measured. Steps are now
  gathered up and only the channel you land on is opened.
- **Subtitles stay on the picture when you zoom.** On a zoomed live channel they were positioned against
  the whole screen instead of against the video, so they drifted away from the image. Both players now
  anchor them the same way.
- **A catch-up programme that never opens moves on instead of spinning.** If the archive stalled without
  ever actually failing, the spinner stayed indefinitely; the programme is now handed to the other
  player, which is what every other stall already did.
- **The "Match EPG" picker responds as you type.** On a large guide each keystroke took about two
  seconds to produce a list; it is now immediate.
- **Audio sync moves in 25 ms steps** in both the player and Settings — 50 ms could bracket a small
  mismatch but never land on it. The setting now also states that it applies to the compatibility
  player only, which was always true but never said.
- **The volume dialog no longer traps the remote at 0%.** Muting from the dialog disabled the "−" button
  while focus was still sitting on it, leaving the D-pad stuck there. Focus now moves to "+".
- **Rename, move and delete in Customize return focus to the row you were on** instead of jumping back
  to the top of the list.
- **A film or recorded programme that won't start on the hardware decoder recovers more reliably.** Two
  of the fallback paths reopened the item before the switch to software had actually taken effect — so
  they retried on exactly the setup that had just failed.
- **Smaller player fixes.** The still frame held over a player switch is released afterwards instead of
  kept (tens of megabytes on a 4K stream), the live buffer readout no longer measures itself on the
  interface thread, and now-playing information is only republished when it actually changes.

### Internal

- Playback identity, request headers and recovery state are now carried on the restore records rather
  than rebuilt per layer — the structural cause behind most of the fixes above.
- New `playback_prefs` table (database 31 → 32) holding the per-item zoom and volume above, keyed by the
  same stable content key the engine pins use, so a re-sync doesn't lose them.
- The player HUD and the Live TV screen are split into smaller files, the three mpv decode-rescue paths
  now share one implementation, and a round of dead code and stale comments was removed.

## v4.2.0 — 2026-08-12

### 🫧 Complete interface and Glass Effect upgrade — unified panels, clearer presets, cleaner focus

- **Live TV, Movies and Series now use one rounded browse container.** Category, list/grid and raised
  preview/poster regions sit inside the same surface with deliberate gaps and separators instead of three
  competing background boxes. The Guide, Home, Settings and dialogs use the same surface language, while
  saved per-section panel widths—including a hidden 0% preview/poster—continue to work.
- **The navigation rail now has its own matching plate and a compact beacon selection.** The logo, menu and
  profile stay aligned inside it; the top strip runs cleanly across the shell; and Search remains at the
  left of the top-bar controls. Normal browsing uses a tighter top bar and rail start, expanding to the
  roomier height only while Audio Mode shows its player controls.
- **Glass now has six appearance choices:** **Ultra Clear** (24% tint / 35% frost), **Clear** (38% / 62%),
  **Balanced** (56% / 78%, recommended), **Tinted** (74% / 88%), **Opaque** (92% / 100%), and **Custom**.
  Transparency reaches 100%; Frost uses ten real blur levels; Highlight strength controls edge light; and
  a live row/card/chip preview shows changes before the popup closes. Reset restores Balanced, 55%
  Highlight strength and every glass surface.
- **Adaptive readability and optional depth keep the clearest presets usable.** A legibility floor
  strengthens floating/container material over bright wallpaper unless **Allow full transparency** is
  enabled. **Depth & shadows** controls focus-arrival light, restrained wallpaper parallax and focus depth;
  the global Animations Off setting disables that motion too.
- **The solid interface received the same quality pass.** Panels now have cleaner tonal separation,
  consistent edges and restrained shadows in light and dark themes. A separate **Ambient Glow** popup adds
  an optional teal aura and optional slow pulse; it is off by default, the pulse appears only after Glow is
  enabled, and the setting is hidden while Glass Effect is active.
- **Focus no longer paints a second rounded layer over rows or drags dark shadows while scrolling.** The
  shared idle/selected/focused/pressed ladder now applies consistently to list rows, buttons, settings,
  navigation and browse rails in both solid and glass modes, without changing their hit area or D-pad path.
- **The richer material stays lightweight during navigation.** Gradients and rim geometry are cached,
  backdrop frost updates without recomposing each surface, idle cards use a lighter path, and full frost is
  promoted only after focus settles. The aligned backdrop remains stable while content scrolls.
- **All new controls and descriptions are available in every packaged interface language.** Glass scope,
  preset, custom values, wallpaper, readability/depth choices and Ambient Glow survive restarts and are
  included in backup/restore. Glass and Ambient Glow both remain opt-in for existing and new users.

### 🔤 Font customization — size the whole interface and choose separate main and popup fonts

- **A new Settings → Look & Feel → Font customization popup controls app text from one place.** Text
  size adjusts from 60% to 140% in 5% steps, and separate selectors choose the font used by the main
  interface and by popups. The five choices are System Sans, Lora, Playfair Display, Dancing Script,
  and Poppins.
- **The existing appearance remains the default:** 100% text size, System Sans for the main interface,
  and Lora for popups. Changes are staged until **Apply** is pressed, **Reset** restores those defaults,
  and **Back** closes the popup without saving its staged changes.
- **Font customization is available in every supported interface language.** Characters supplied by the
  chosen font use it, while Android safely falls back to a compatible system font for any missing script
  glyphs. Subtitle text is intentionally excluded and remains controlled by the separate subtitle
  appearance settings.
- **The choices survive restarts and travel in backup and restore.** All bundled fonts are open-source,
  and their license notices are included with the project.

### 🔐 Advanced Stalker device identification — support portals that require more than a MAC

- **Stalker sources now accept four optional device-identification values:** Serial Number, Device ID,
  Device ID2, and Signature. They appear together under **Advanced device identification** when adding
  or editing a Stalker source, including during first-run setup.
- **Remote setup supports the same fields.** Enter them from the Stalker tab on a phone or laptop and
  they arrive pre-filled on the TV with the Portal URL, MAC, and User-Agent. **Test connection** uses the
  complete form, so a strict portal can be verified before the source is saved.
- **The identity follows every later portal login.** Syncs, lazy episode loading, catch-up, short EPG,
  subscription checks, and playback-link renewal all reuse the stored values. Blank advanced fields are
  omitted completely, preserving the existing MAC-only flow for portals that do not require them.
- **Existing installations upgrade without re-adding a source.** The new values are optional, and
  password-protected backups encrypt them alongside the Stalker MAC. Backups without a password omit
  device identifiers rather than writing them in plain text.

### 🌍 OwnTV in 24 languages — choose before setup, change anytime (community PR #108 by @codeVerine)

- **OwnTV's complete interface is now available in English plus 23 fully translated languages:** Arabic,
  Bangla, Brazilian and European Portuguese, Simplified and Traditional Chinese, Czech, Danish, Dutch,
  French, German, Hindi, Italian, Japanese, Korean, Malayalam, Norwegian Bokmål, Polish, Russian, European
  and US Spanish, Swedish, and Turkish. Missing text safely falls back to English.
- **A fresh installation asks for the app language before Get Started.** The selected language and its
  description are shown in a compact TV-friendly control; the full list opens in OwnTV's standard popup,
  with isolated D-pad focus, scrolling, and focus restored after selection. The welcome, disclaimer, setup
  choice, and add-playlist pages now share a modern OwnTV design with a subtle animated teal glow and ring.
- **The language can be changed later from Settings → Look & Feel → Language.** Rows show each language in
  its own script, include a searchable English name, and keep a durable System default option that follows
  the TV's locale. The choice is independent of profiles and of the separate TMDB metadata-language setting.
- **RTL and locale-aware presentation are built in.** Arabic mirrors logical navigation where appropriate;
  dates, times, numbers, counts, plurals, launcher text, notifications, setup, companion pages, playback
  messages, and diagnostics use localized resources without changing the underlying player or sync logic.
- **Translation maintenance is now part of the project.** Six resource domains, a generated locale catalogue,
  Hosted Weblate contribution and new-language request paths, 19 catalogue-only future languages, 132 tooling
  tests, pseudolocale checks, and CI guards protect placeholders, plurals, formatting, overflow, and release
  packaging. The initial translated catalogue covered all 1,771 source entries; newly added text safely
  falls back to English until translations catch up.

### 🔥 Now Trending on Home — TMDB discovery that only recommends titles your provider can play

- **Home can now open with a large Now Trending showcase built from the current TMDB movie and TV charts.**
  OwnTV publishes up to 10 entries, only after matching TMDB's candidates to exact, playable rows from the
  active provider. The target is an even five/five mix, but either type can fill unused places; a result is
  shown from four matches upward.
- **The chart is downloaded rarely; your row is rebuilt after every sync.** TMDB's trending list is the same
  for everybody and changes slowly, so each playlist re-downloads it only once every five to eight days, and
  playlists falling due on the same day share a single download. Matching runs again after every sync and
  costs nothing, so a title that arrives in your provider's catalogue today can appear in Trending today. A
  second page of candidates is fetched only when the first leaves places unfilled, and title details reuse
  the metadata already cached for the detail screens. Changing the metadata language does not force an early
  download either — the showcase adopts the new language at its next scheduled refresh.
- **Matching is indexed, language-aware, and safe against remakes.** Provider titles are normalized and
  backfilled once, exact title/year candidates are checked before the narrow FTS fallback, and the final
  variant prefers the selected provider language, then English, then an untagged title. TMDB rank remains
  global across movies and series, while the provider row is revalidated immediately before playback.
- **The showcase explains every choice.** It shows TMDB rank and media type, provider match, rating, preferred
  language or fallback, advertised 4K/HDR/audio signals parsed from the provider name, and the number of
  provider seasons already available for a series. Play/Open Episodes, Trailer, More Details (including cast
  and genres), and All Versions lead into the same established playback, metadata and search flows.
- **Remote navigation behaves as one complete Home row.** Moving among any showcase controls keeps the entire
  panel fixed; Home scrolls only when focus crosses into another row. Auto-advance pauses on the four main
  action buttons, continues on Previous/Pause/Next, and can still be paused manually.
- **The background build is visible instead of mysterious.** The sync status pill can show a second line for
  candidate fetch, indexed-catalog preparation, per-candidate movie/series matching, provider-season loading,
  enrichment and publishing. The same detailed stages and timing totals are available through the
  `TrendingRefreshWorker` / `TrendingRepository` logcat tags.
- **The feature follows Home and metadata settings.** It is on by default and has a dedicated On/Off option
  in **Settings → Home screen**, separate from the reorderable Home cards. When enabled it is always the top
  Home row; when disabled it is not shown. Its translated note explains that the row appears only when 4–10
  TMDB trends match playable movies or series in the provider catalogue. Metadata mode must be **Provider +
  TMDB** or **TMDB only**. The per-profile choice travels in backup/restore; fetched snapshots and metadata
  do not.
- **Existing data upgrades in place.** Room migrations add the atomic Trending snapshot, item and indexed
  provider-title structures without rebuilding playlists, favourites, history, progress or customizations.
  All new interface text is translated across every packaged OwnTV language.
- **The bundled/self-hostable Cloudflare gateway now supports Trending traffic robustly.** Trending responses
  use a short 15-minute freshness window, ordinary metadata keeps its 30-day window, equivalent query strings
  share one cache key, transient TMDB failures retry once, upstream calls time out, and a still-valid stale
  response can be served during an outage. Responses include cache/request diagnostics without exposing keys.

### 🧪 Test HLS support — find out whether your provider really serves HLS before you turn it on

- **New button directly above "Prefer HLS for Live TV"**, on every Xtream playlist — when adding one,
  when editing one, and in first-run setup. Until now the only way to find out whether HLS worked was to
  turn the setting on, sync, and watch channels fail.
- **It asks the provider, then checks the answer.** First it reads what formats the account claims to
  support, then it actually requests an HLS channel and looks at what comes back. The claim and the
  reality disagree in both directions often enough that the claim alone is not worth much: plenty of
  providers serve HLS without listing it, and a few list it without serving it. What actually came back
  wins.
- **No sync needed.** On a brand-new playlist it takes the first channel straight from the provider and
  stops reading there, so it costs one short request instead of downloading a channel list. If the
  playlist is already synced it uses a channel you already have and skips even that.
- **The toggle is never hidden, disabled or overruled by the result.** A provider that under-reports its
  formats must not be able to veto your choice — the test refines the note under the toggle, nothing
  more.
- **It won't tell you "no" on a dead channel.** Many providers list a placeholder entry ("### INFO ###")
  as their first channel. A failed HLS test is only reported as a real "no" once the same channel is
  confirmed to play over MPEG-TS; otherwise it says the test channel looks dead. If your account is out
  of connections it tells you that instead of guessing.
- A confirmed result is remembered and is not overwritten by the next sync's weaker claim.

### 📐 Panel Width Adjustment — hide the preview or poster panel completely

- **The third browse panel can now be set to 0%.** In Live TV this hides the Preview panel; in Movies
  and Series it hides the Poster panel. Category and List keep their 10% minimum, the saved total still
  has to be exactly 100%, and the hidden panel is removed from the layout together with its unused gap —
  the other two panels receive the full remaining row instead of leaving an invisible strip behind.
- **Live Preview video cannot stay on without a Live Preview panel.** Saving a 0% Live Preview width
  while preview video is on explains what will happen and asks first; confirming turns the video off as
  the panel is hidden. Trying to turn preview video back on shows where to restore the panel width and
  leaves the toggle off. The same rule is enforced when settings are restored from a backup.

### ⚡ Faster cold start — OwnTV now tells Android what it needs before it needs it

- **Opening the app from cold is about twice as fast.** OwnTV now ships a startup profile: a list of
  the code paths used while the app is starting, which Android can prepare ahead of time instead of
  working them out on every launch. Measured on a Realtek Android TV box with a full catalogue, cold
  start went from roughly 1.0s to roughly 0.55s.
- **The speed-up arrives on Android's schedule rather than immediately.** OwnTV hands the profile to
  the system a few seconds after the first launch following an install or update, but Android does the
  actual preparation during its own idle maintenance — normally overnight. The first launches after
  updating still feel like the old version. Nothing has to be done to trigger it, and closing the app
  early neither interrupts it nor makes it start over.

### 🧱 Refreshed playback and networking libraries

- **Playback moved to Media3 1.11.0**, bringing audio-output initialisation fixes, Live HLS timestamp
  and playlist-refresh fixes, subtitle timing corrections, and DTS-HD support in MPEG-TS streams.
- **Networking moved to OkHttp 5**, which now opens IPv4 and IPv6 connections in parallel and keeps
  whichever answers first. On TVs that advertise an IPv6 route they cannot actually use, connections
  no longer have to wait for the broken address to time out — the same class of problem as the artwork
  fix below, now handled for every request rather than images alone.
- Build toolchain refreshed alongside them: Gradle 9.7, AGP 9.3.1, Kotlin 2.3.21, KSP 2.3.11 and
  Compose BOM 2026.06.01.

### 🐛 Fixes

- **Your Movies & Series player choice is no longer overridden after a few failed streams.** Once three
  items in a row had fallen back from ExoPlayer to mpv, OwnTV switched the rest of the session to mpv —
  and a run of dead links counted exactly as much as a file this TV genuinely cannot decode, so on a
  large public playlist the setting could be retired within a minute of browsing. A decode failure also
  saved a permanent, invisible player choice for that item. Both behaviours are gone: every movie and
  episode now starts on the engine you picked, every time, and only that one playback falls back if it
  actually fails. Live TV already rebuilt its full engine ladder for every channel and is unchanged.
- **New — Settings → Video Player → Reset saved player choices.** Switching a single movie or episode
  between mpv and ExoPlayer inside the player still saves that choice, for that one item only. Because
  older builds could also write such choices automatically, this row shows how many are stored and
  clears them all in one step; saved choices for live channels are kept.
- **Switching audio language mid-playback no longer makes the sound stutter.** On soundtracks the TV
  decodes itself — Dolby Digital, DTS and similar, passed through untouched — choosing a different
  language left the audio output half-restarted, and the sound snapped and skipped for the rest of the
  item. Selecting that very same track *before* playback started was always fine, and mpv was never
  affected. OwnTV now re-primes the audio output once when it changes a passed-through track, costing
  a brief re-buffer; soundtracks OwnTV decodes itself are untouched. Live TV gets the same fix, applied
  only where the stream can be re-primed cheaply rather than reconnected.
- **The stream information overlay now says correctly when your TV is decoding the audio.** Passthrough
  was detected by looking for a decoder name that never appears on that path, so any soundtrack sent to
  the TV untouched was described as decoded by OwnTV.
- **TMDB trailers no longer stutter.** Trailers played inside a rounded, part-screen window, and that
  containment forced every decoded frame to be copied through the graphics processor instead of going
  straight to the screen — the picture dropped frames continuously even though the video itself was
  being decoded in hardware without difficulty. Trailers now play full screen, which removes the
  copying altogether.
- **The Home hero preview stops while a trailer is playing.** It previously kept decoding behind the
  trailer window, running a second video at once for a picture nobody could see.
- **Stalker imports no longer lose a whole category when the portal drops the connection.** OwnTV
  already retried a portal that answered "too busy", but treated a reset connection or a half-sent
  reply as final — and because a category whose first page fails is left out of that import, the next
  sync removed its items as though the provider had dropped them. Broken connections, timeouts and DNS
  blips during paging are now retried the same way, while genuine refusals and malformed replies still
  fail straight away.
- **Guide focus can now move from a programme row into the docked mini-player (#112).** Opening
  Picture-in-Picture from the Guide returns focus to the channel as before; Right moves to the whole
  EPG row, and a second Right now reaches the mini-player controls instead of being swallowed by the
  Guide. Per-programme timeline browsing keeps its existing Left/Right behaviour.
- **The stereo-fallback notice no longer remains over the next video.** The surround safety net still
  explains when a TV audio output fails and OwnTV switches the item to stereo, but repeated notices now
  replace one another instead of queuing. Stopping playback or opening another item clears the notice,
  and a late callback from the previous item cannot put the old message over the new video.
- **Surround sound no longer switches itself off because of a small timing gap inside a file.** The
  audio safety net treated every complaint from the audio output as proof that the output had failed.
  One of those complaints only means the file's own audio timestamps jumped — the player re-syncs by
  itself and carries on — but OwnTV read it as a failure, dropped the whole session to stereo and
  restarted the item. Forcing stereo cannot repair timing that lives in the file, so the restarted item
  reached the same spot and did it again: one imperfect film could interrupt playback several times and
  leave a real 5.1 receiver in stereo for the rest of the session. Genuine faults — an output that
  rejects the format, or produces no sound at all — are still caught exactly as before.
- **The stream information overlay now names the decoder that is actually running.** It reported the
  Hardware decoding *setting* rather than the decoder in use, so an item that had quietly fallen back to
  a software decoder still read "hardware". The overlay now says hardware or software based on the
  decoder itself, and a silent fallback is recorded in the playback log that the diagnostics export
  collects — which is what makes a "picture is breaking up" report answerable.
- **Changing the interface language in Settings now keeps focus on the selected language.** OwnTV
  also preserves the open Language screen when switching between writing systems requires Android to
  refresh the Activity, instead of dropping focus back onto the main navigation menu.
- **Posters and other remote artwork no longer stay blank on TVs with a broken IPv6 route.** Image
  hosts such as TMDB publish both IPv6 and IPv4 addresses, but some Android TVs advertise IPv6 even
  though they cannot actually reach it. OwnTV stopped after that first failed address while the TV's
  browser quietly tried IPv4, so valid provider and TMDB artwork opened in a browser but not in the
  app. Image downloads now try the next resolved address; playlist sync, EPG and playback keep their
  existing network behaviour.
- **Test DNS now tests the server you selected instead of sometimes reporting success through the
  TV's normal DNS (#111).** The temporary check starts with the displayed Google, Cloudflare, Quad9
  or custom address active immediately, and a failed custom resolver is no longer hidden by the
  normal system-DNS fallback. A successful check now shows only **Test passed** and its response time,
  instead of listing the Google destination addresses returned for the test hostname; success means
  the selected resolver actually answered without presenting technical details that look like the
  selected DNS server.
- **The top-bar Search and playlist controls are compact again.** A localization safety change made
  short labels expand to their maximum allowed widths, so the Search button looked like a text field and
  the playlist selector occupied too much of the right side. Both now size themselves to their content,
  as in v4.1.7, while genuinely long translations and playlist names remain bounded and scroll on focus.

- **A channel that is briefly full now waits and starts by itself, instead of dropping you on the error
  screen.** Some providers answer a channel change with "too many connections right now, come back in N
  seconds" — typically because the channel you just left is still counted as open. OwnTV treated that as a
  dead channel, so you landed on the error screen and had to press **Retry** repeatedly until the
  provider's own timer ran out. It now reads the waiting time the provider sends, keeps the loading
  spinner up with a line saying why (for example *HTTP 429: Channel limit has been reached. Retrying in
  10s.*) and counts down to an automatic retry. If the provider answers with a shorter time, the countdown
  updates to it. The channel is re-requested exactly as before — same player, same stream format, same
  address — so this never causes an unwanted switch to the compatibility player or to the other format.
  Changing channel, stopping or leaving cancels the wait immediately, and an account that stays full still
  reaches the normal error screen after a few attempts.
- **Live channels that switch themselves to the compatibility player play again instead of turning
  black — this fixes a v4.1.7 regression.** When OwnTV decides by itself that a channel needs the
  compatibility player (no picture, an error before the first frame, audio the TV can't decode, a
  provider refusing the standard player's stream URLs), the screen switched over correctly — and then
  nothing was ever loaded. The result was a channel that showed a frame, went black and stayed black
  forever, with no error, no retry and no further fallback, on channels that played perfectly in
  v4.1.6. Only the automatic switch was affected: choosing **compatibility mode** by hand from the
  player always worked, and that was the only way to watch those channels. Every automatic handoff now
  completes, and if it is ever abandoned because you changed channel mid-switch, the playback log says
  so instead of going silent.
- **Prefer HLS no longer applies to catch-up — this reverses a v4.1.7 change.** Catch-up recordings are
  requested as MPEG-TS again, always. Tying the two together was wrong: "Prefer HLS" describes the live
  edge, which providers remux to HLS on demand, while the timeshift server is a different thing that
  serves recordings off disk with no HLS repackager in front of it. Asking it for HLS reliably returns an
  error page, so v4.1.7 silently broke catch-up for accounts whose live TV was perfectly fine. Live TV
  and the Guide keep the v4.1.7 behaviour.
- **Catch-up recordings now get both of their fallbacks instead of one.** The alternate `timeshift.php`
  request shared a "already tried an alternate" marker with Live TV's format swap, so whichever ran first
  used up the other's turn — archives on providers that need the alternate form got a single attempt and
  then an error.
- **One channel without an HLS version no longer sends every other channel's preview to the wrong
  format.** The "this channel has no HLS" note was a single global flag, so after one such channel every
  preview in the list was tuned in the other format until the next channel change — and pressing OK then
  rebuilt the stream from scratch instead of simply using the preview that was already playing.
- **A provider that doesn't serve HLS at all is now recognised after three channels.** Before, every
  single channel paid two dead attempts before falling back, for the whole session. Three different
  channels failing is enough to tell "this one channel isn't remuxed" apart from "this provider doesn't
  do HLS".
- **Streams that VLC plays but OwnTV didn't now get one more attempt with error tolerance turned on.**
  Re-streamed feeds often carry damaged or malformed data that the player's strict defaults reject
  outright. There is now a final attempt that ignores those errors and rebuilds timestamps, and a channel
  that needed it is remembered so it doesn't pay the failed strict attempt again that session.
- **The HLS note under the toggle no longer implies an answer it doesn't have.** On a playlist that had
  never synced it read "your provider does not report HLS support", which was indistinguishable from a
  real "no". It now says nothing until the provider has actually been asked.
- **Turning "Prefer HLS" off could stop every channel playing on the standard player until the app was
  restarted.** While Prefer HLS was on, any channel that fell back to the compatibility player taught
  OwnTV that the provider serves HLS behind its `.ts` addresses — a lesson that applies to the whole
  provider. It was the wrong lesson: that channel was only playing HLS because the setting had asked for
  it. With the setting switched back off, every channel on that provider was then read as a playlist
  instead of a stream, failed instantly ("Input does not start with the #EXTM3U header") and handed off
  to the compatibility player. The lesson is now only learned when a `.ts` address really does answer
  with a playlist.
- **Auto frame rate no longer makes the picture pause several times on one channel.** Live TV streams
  almost never state their frame rate, so the app measures it — and a measurement wanders, reading 24.6
  then 25.1 for a channel that is plain 25fps throughout. Each of those readings picked a different
  display mode, and every mode change blanks the TV for a second or more while HDMI re-negotiates, so a
  channel whose frame rate never changed could black out three or four times in a row. Readings are now
  pulled onto the nearest rate content is really made at, and a second mode change can't follow the first
  within five seconds — it waits, and a newer reading replaces the waiting one. The first switch on a
  channel is still immediate.
- **Auto frame rate now prefers a refresh rate the TV can reach without blanking.** On Android 12 and
  newer the panel reports which rates it can slide to seamlessly; when two of them suit the content
  equally, the seamless one is chosen. Where no seamless mode fits, the switch still happens as before —
  the preference never removes an option, so 25/50fps content on a 60Hz TV keeps working.
- **Auto frame rate is now turned off once on TVs running Android below 12, and warns before it is
  switched back on there.** Only from Android 12 does a TV tell the app which refresh rates it can move
  to without blanking, so on older sets every switch risks blacking the picture out mid-programme — the
  cause behind most "the stream pauses when the frame rate changes" reports. On those devices the setting
  is reset to off a single time on the first launch after updating; turning it on afterwards explains
  what may happen and asks you to confirm, and that choice is then left alone for good. Nothing changes
  on Android 12 and newer, and the reset never repeats. The one-time suggestion to *enable* Auto frame
  rate for a juddering 25fps channel no longer appears on those older devices either.
- **A Live TV engine handoff is now written to the playback error log.** When a channel moves from
  ExoPlayer to mpv, or runs out of fallbacks entirely, the log records what happened and why. Until now
  that whole sequence left no trace anywhere a TV user could read, so a channel that failed this way
  produced a completely empty log.
- **Export in the playback error log is always available.** It used to be hidden whenever the list above
  it was empty — but the export carries more than that list, including the recent Live TV diagnostics,
  so it was withheld in exactly the case that needed it most.
- **Playback reports now export to the TV's public Download folder.** The report is written as
  `Download/owntv-playback-report.txt`, replacing the previous app-private Android/data location, so it
  is visible to normal file managers and easy to copy without ADB. OwnTV creates the Download folder if
  the device does not already have one and replaces its previous report instead of accumulating numbered
  copies.
- **Opening Settings now focuses Profiles, the first row, instead of Language.** D-pad navigation starts
  where the visible settings list starts; returning from a sub-page still restores the row that opened it.
- **Playlists whose provider blocks media players now play.** Some providers sit behind a protection
  service that decides whether to answer by looking at what the request calls itself — and a request that
  calls itself a media player is turned away with a "checking your browser" page, while the very same
  address is served normally to anything else. Every channel on such a playlist failed instantly on both
  players, which looked exactly like a dead provider. OwnTV now notices that kind of refusal and asks once
  more as an ordinary desktop browser; if that works, the rest of the playlist opens straight away with no
  second attempt. Playlists that set their own User-Agent are untouched — your setting is always used as-is.
- **When a provider explains why it refused a channel, that explanation is now shown on the error
  screen.** Providers often answer with a plain sentence — for example "Channel limit has been reached.
  Stop one of your active streams before opening a new channel." — and OwnTV used to throw it away and
  show a generic message instead. The provider's own words are now shown, whichever player hit the wall,
  and refusals caused by having too many streams open are named as such rather than reported as an
  offline channel.
- **A channel is no longer sent looking for an address that doesn't exist when the provider is simply
  busy.** If a provider refused because the account already had a stream open, OwnTV treated it as a
  format problem and started guessing other addresses for the same channel — every one of them refused
  too — so a channel that would have played on the second press spent nearly a minute failing and then
  blamed itself. Refusals of that kind now stop the guessing immediately.
- **The standard player can now switch stream format by itself.** Trying a channel's other address form
  (`.m3u8` ⇄ `.ts`) was something only the compatibility player could do, so a channel published in one
  form and served in the other could only be rescued by switching players — a visible interruption for
  what is a one-character difference. It is now tried in place first, and only when the failure really is
  about format.

## v4.1.7 — 2026-08-04

### 📐 Panel Width Adjustment — set how wide the categories, list and preview panels are

- **New setting: Settings → Panel Width Adjustment**, with a separate popup for **Live TV**, **Movies**
  and **Series**. Each panel gets its own **−/+** control, so you can widen the channel list on a small
  screen, shrink the category rail you never scroll, or give the preview more room for artwork.
- **The numbers are shares of the screen and always add up to 100%.** Category 30% + List 20% +
  Preview 50% is the whole row. A running **Total size** line shows where you are, and saving while it
  doesn't read 100% is refused with a note in red telling you what to fix — so a panel can never quietly
  eat another one.
- Each section has its own **Customize panel** switch and a **Reset** back to the standard widths, and a
  section left off keeps the layout exactly as it is today. Movie and series posters re-flow by
  themselves, so a narrower list simply shows fewer per row. The settings travel with your backup.

### 🔊 Surround sound rebuilt — Auto, Stereo only, Surround, and it can no longer leave you in silence

- **The setting is now three choices instead of an on/off switch: Auto (new default), Stereo only, and
  Surround.** They answer one question — who decodes Dolby/DTS. **Surround** sends it to your TV or
  receiver to decode; **Stereo only** decodes it inside OwnTV and sends plain stereo, which is the right
  answer for TV speakers and stereo soundbars; **Auto** starts like Surround and drops back to stereo by
  itself the moment your audio output is caught failing. Your existing choice is kept: if you had
  surround on you stay on Surround, if you had turned it off you stay on Stereo only, and if you never
  touched it you get Auto.
- **The setting now actually applies to Live TV.** It only ever reached the compatibility player, and
  Live TV normally uses the standard one — so on live channels the switch did nothing at all in either
  position, and a TV that mishandles Dolby got handed Dolby regardless of what you had chosen. This is
  the cause behind live channels that played picture with no sound, or whose sound drifted, no matter
  what you changed.
- **New safety net: if your TV or soundbar accepts the sound and then doesn't play it, OwnTV notices and
  switches to stereo for you.** It watches for the audio output going silent, erroring, or repeatedly
  starving, then falls back, tells you, and gets sound back within a few seconds. It runs in **all three
  modes, including Surround** — asking for 5.1 is not asking for silence — and once it has fired, every
  player in the app stays on stereo for the rest of the session, so switching channels or engines can't
  lose the sound again. Restart the app, or change the setting, to give your equipment another go.
- **Stream info now tells you what the audio is actually doing.** A new **Audio out** row shows whether
  your TV/receiver is decoding the sound (passthrough) or OwnTV is, whether surround is currently
  allowed, and — if the safety net fired — why. Useful when you have no receiver display to check
  against.

### ⏱️ Live latency really changes the buffer now — and a new Pre-buffer control, per playlist

- **The Live latency setting finally drives the actual buffer.** On the standard player it was mostly
  decoration: the buffer sizes were fixed no matter what you chose, and the only thing the setting fed
  was a hint that HLS understands and a plain MPEG-TS stream ignores completely — which is what many
  providers serve. Choosing "most stable" therefore changed nothing for a lot of people. Both players
  now size their live buffer from the setting.
- **New setting: Pre-buffer live streams (off / 2 / 5 / 10 s).** It collects that much video before a
  live channel starts playing — and again after a stutter — instead of starting on the first frame. It
  is an *amount of video*, not a countdown: on a fast provider 10s of video arrive in well under a
  second, so the channel still starts instantly. On a provider that hiccups every few seconds, it holds
  the picture until there is enough to play through.
- **Pre-buffer per playlist.** Most people have one troublesome provider and several fine ones, so any
  playlist can override the global value (or keep following it). Settings → Video Player → Live TV.
- **Stream info shows what the player actually applied** — a **Live buffer** row with the pre-buffer
  amount, the buffer depth, and whether a playlist override is in force. Defaults are unchanged: Balanced
  with Pre-buffer off reproduces the previous behaviour exactly.

### 🎞️ Auto frame rate works out the frame rate by itself — and offers itself when 25 fps judders

- **Auto frame rate now works on channels that don't declare their frame rate.** Most live streams
  don't, and the feature had nothing to act on — so on exactly the content it exists for (25 fps
  European channels on a 60 Hz TV) it did nothing at all. The frame rate is now measured from playback
  when the stream doesn't state one, and only used when two readings agree and land on a real broadcast
  rate — a wrong guess would ask the TV for the wrong mode. On the standard player the measurement no
  longer depends on the *Measured stream stats* toggle being on.
- **A one-time suggestion when it would help.** If a channel judders because its frame rate doesn't
  divide into your TV's refresh rate, and your TV really has a better mode, OwnTV offers to turn Auto
  frame rate on — naming the actual numbers. Shown **once ever**, never on a TV that has nothing better
  to switch to, and it can be turned off again in Settings → Video Player.

### 🗓️ Guide time offset — for a guide that is hours out, globally or for one channel

- **New setting: Settings → EPG → Guide time offset.** Shifts the whole guide by up to −12 h/+14 h in
  15-minute steps. It is for the common case where a provider publishes one XMLTV feed in its own time
  zone, so every programme in the Guide sits a few hours away from what is actually on screen.
- **Per-channel override in the long-press menu**, in both Live TV and the Guide. Networks routinely hang
  their East and West feeds off the same guide data, so one of the two is always wrong; now you can
  correct that channel alone. An explicit "no shift" on a channel is an override too — it pins that
  channel to the feed's own times while the global offset moves everything else.
- **The correction applies everywhere the guide is read** — the Guide grid, Now/Next, the "On now" rows,
  the catch-up picker and the archive URLs built from those programmes — and it never rewrites stored
  data, so a resync or a guide refresh cannot undo it.

### 🎧 Sound behaves like a TV app now — and the remote's transport keys work

- **A notification or a system sound no longer plays straight over your film.** The app now asks for
  audio focus and ducks: the sound dips briefly and comes back. Only another app taking the audio
  permanently pauses playback.
- **Play/pause, next and previous from the remote, a headset or voice now reach the player**, through a
  system media session that works with both players. It publishes what is playing, and seek/skip are
  offered for films and episodes only. With the player closed, those keys do nothing to OwnTV.

### 🩺 Diagnostics you can actually send

- **New: Settings → Video player → Detailed playback logging.** The full live playback trace used to
  exist only in development builds, so a report from a normal install came back with nothing in it. It
  can now be switched on in any build, and it only affects what is written down — never playback.
- **The playback log now records events, not just failures** — a decode rescue, a handoff between
  players, the stereo safety net firing, a provider that only allows one stream. A juddering picture or
  drifting sound is not a crash, so previously there was nothing in the log to send.
- **New: "Report this stream"** in the player. Open the stream info overlay (ⓘ) and a share button
  appears; it saves exactly the readout you are looking at — codec, resolution, HDR, bitrate, decoder,
  audio, buffer, engine, position — into the playback log.
- **New: Export**, in Settings → Playback error log. Writes the whole log plus the live trace to a file
  and shows you the path, so it can be pulled off the TV with
  `adb pull /sdcard/Android/data/tv.own.owntv/files/owntv-playback-report.txt`.

### 🧩 M3U playlists: per-item headers, and catch-up that actually builds a URL

- **Per-channel HTTP options in an M3U playlist are honoured.** `#EXTVLCOPT:http-user-agent`,
  `#EXTVLCOPT:http-referrer`, `#EXTHTTP`, `#KODIPROP` stream headers and the
  `http://host/x.ts|User-Agent=…&Referer=…` suffix were all ignored — and the pipe suffix was worse than
  ignored, it was sent as part of the URL. A playlist where one restream needs its own User-Agent or
  Referer (routine for CDN-token playlists) answered 403 with nothing the user could do. Both players
  send them now, including across the automatic switch between players. A per-channel User-Agent
  overrides the playlist-wide one.
- **Catch-up on M3U playlists works for the common `catchup="append"` style**, plus `shift`,
  `flussonic` and `xc`. The catch-up *type* was parsed and thrown away, so the most widespread form built
  a broken URL and "Watch from start" played nothing at all. `{lutc}`, `{now}` and `{timenow}` are now
  substituted instead of being sent to the provider literally.
- **Per-item HTTP options now work on movies and series too, not just live channels.** A playlist that
  gives a film or an episode its own User-Agent or Referer had those options dropped, so exactly the
  same 403 that used to hit live channels hit VOD instead. They are now stored per item and sent by
  both players — and by an external player, which previously received no headers or User-Agent at all
  from anywhere in the app. In a series they are applied per episode, so a season that mixes
  header-carrying and plain episodes plays right through.
- These need one playlist refresh before they take effect.

### 🐛 Fixes

- **4K movies that failed to play on some TVs now get a real rescue instead of a wrong error.** Four
  things stacked up: the "out of memory" decoder error was not recognised, a decoder failure was
  reported as a malformed file from the provider, there was no fallback between the direct hardware path
  and software decoding (which is capped at 1080p, so 4K had none at all), and nothing ever asked the TV
  what it can actually decode. Playback now steps down through an extra hardware rung before software,
  and when the TV genuinely cannot decode a video the message says so — instead of blaming the file.
- **A film or episode now gets every decoding option before it gives up, whichever player you prefer.**
  Playback tries the standard player's hardware decoder, then its software decoder, then the
  compatibility player's hardware decoder, then its software decoder — the same four rungs in the same
  order, mirrored, whichever player you set as your preferred one. The standard player's software rung
  existed only for catch-up recordings before this, so a video that its hardware decoder could not
  handle skipped straight to the other player and, on the way, lost the setting you had chosen.
- **A file that repeatedly defeats your preferred player stops re-trying it.** When a video fails on the
  standard player for a decoding reason, that film or episode is remembered and opens on the
  compatibility player next time; and if three items in a row have had to switch, the rest of the
  session starts on the working player straight away. Changing the preferred-player setting clears this,
  and nothing is remembered for a failure that was the network's fault rather than the decoder's.
- **The Home screen's background preview no longer holds a channel open on a dead stream.** It had no
  time limit of its own, so a stream that connected but never produced a picture kept the connection —
  which matters on providers that allow one stream at a time. It also decoded and discarded the audio
  it never plays; that is switched off at the source now.
- **Channels found in Search now play exactly like channels opened from Live TV.** Search had its own
  minimal way of starting a channel, so it skipped the Prefer HLS setting, the automatic switch to the
  compatibility player, per-channel compatibility pins and the channel list for CH+/CH−. A channel that
  needed any of that failed in Search while playing fine elsewhere. There is now one shared path for
  every place a live channel can be started.
- **Prefer HLS also applies in the TV Guide and to catch-up.** A playlist whose provider only serves
  this account HLS could fail to open from the Guide, and its catch-up recordings were always requested
  in the other format.
- **Retry on a movie retries the movie.** The Retry button always restarted playback as if it were a
  live stream: back to the beginning, with live-stream settings — and on a portal playlist it could even
  end up on the last live channel you watched. It now resumes the film where it stopped.
- **Long films on portal (Stalker) playlists survive their link expiring.** Those playlists hand out
  stream links that expire after a couple of hours, which is well inside a long movie; a retry then
  failed on the dead link. The app now asks the portal for a fresh link, for movies and episodes as well
  as live channels.
- **Providers that allow one stream at a time recover in the compatibility player too.** It treated the
  provider's "your one connection is already in use" answer as a flat refusal and gave up almost
  immediately, while the standard player waited and reconnected — the reason a channel could play on one
  player and not the other. It now backs off and retries, and remembers that provider for the session.

- **"Hardware decoding: Off" now applies to the standard player too.** It only ever reached the
  compatibility player, so turning it off to work around a TV whose decoder mishandles a stream still
  left that decoder in charge whenever playback used the standard player — including all normal Live TV.
  Both players now prefer software decoders when it is off, with the hardware decoder still available as
  a backstop, so nothing that used to play can stop playing. The **Decoder** row in stream info also
  reports the decoder that is really in use instead of always claiming hardware.

- **Live TV channels that only allow one stream at a time no longer lock themselves out.** Some providers permit a single connection per account and refuse the second one. Switching between the standard and compatibility player, or opening a channel right after another, could leave the app competing with itself: the engine that had just been asked to stop was still connected, so the new one was refused and the channel showed an error or an endless spinner. Each engine now fully releases its connection before the other one starts, the app waits briefly and retries once when a provider says the account is still in use, and on such providers the muted preview no longer runs while a channel is playing full-screen.
- **Audio and video no longer drift apart on live channels in compatibility mode.** A workaround for a handful of feeds with broken timestamps — letting the picture run on its own clock — was being applied to every live channel, which slowly pushed the sound ahead of or behind the picture on perfectly healthy streams. Live playback now keeps accurate audio-synced timing, and the workaround switches on only for a channel that actually reports broken timestamps.
- **Channels whose provider refuses the standard player's stream URLs are handed to the compatibility player sooner.** Some panels sign every segment with a short-lived token and then reject it; the standard player cannot recover from that by design. Two refusals are now enough to switch engines, and the provider is remembered for the rest of the session so its other channels start on the working engine right away.
- **A provider that refuses playback outright is no longer hammered with the identical request.** The retry ladder stops repeating a request that was already rejected, while the alternative-format and player fallbacks still get their turn.
- **Catch-up recordings that opened with sound but no picture now recover by themselves.** A recording
  starts in the middle of the video stream, which some TV decoders cannot begin from; playback now
  reopens it in software decoding and remembers that provider, so its other recordings start on the
  working path straight away. That memory is now kept across app restarts as well — re-learning it cost
  a whole failed recording every time you opened the app, since the fault is in the provider's archive
  and does not come and go.
- **Zapping no longer slows down the Guide, artwork and playlist updates.** Stopping a live channel
  releases its connections — necessary, because many providers count them — but everything else in the
  app was sharing those connections and had to reconnect from scratch. Playback now has its own pool.
- **Audio Mode really switches the picture off — on both players.** It stopped drawing the video but
  kept decoding every frame, so it saved neither power nor heat. The video track is now switched off at
  the source on the compatibility player and on the standard player alike, and comes back cleanly when
  you leave Audio Mode.
- **On providers that allow one stream at a time, the preview pane now says so** instead of sitting
  blank, which read as a broken channel. OwnTV also reads the limit straight from the provider's account
  info when a playlist syncs, so it no longer has to find out by failing a channel first — and the Home
  screen's own preview stops competing for that single stream too.
- **Subtitles are drawn in the small docked player.** They only ever appeared full-screen, so docking a
  subtitled film silently dropped the dialogue. They are sized to the docked window.
- **The audio/video sync nudge is available on live channels** in compatibility mode, not just on films.
  A live feed can arrive with the provider's own drift baked in, and mpv can correct it.
- **Volume boost above 100% now works on Live TV too**, up to 150%, like films and series. Live TV
  stopped at 100% because its player cannot amplify by itself; the boost now comes from the system's own
  audio effect. A TV whose audio hardware refuses the effect simply stays at 100%.
- **…and above 100% now works on films and episodes that play on the standard player.** The same limit
  applied there for the same reason: only the compatibility player can amplify by itself, so a film the
  app had handed to the standard player quietly stopped getting louder at 100%. Both now use the
  system's audio effect, so 150% means 150% wherever the video ends up playing.
- **Your preferred audio and subtitle languages now apply on the standard player as well.** They only
  ever reached the compatibility player, so a live channel — which normally uses the standard player —
  and any film or episode that ended up there started on whichever track the provider happened to list
  first. Both settings are honoured now on Live TV, films and episodes, they follow a change made while
  something is playing, and when a subtitle track is picked for you the Subtitles button correctly shows
  as on.
- **Default zoom now applies to Live TV, and zoom no longer carries over between channels.** The setting
  was read by the compatibility player only, so live channels always started at Fit no matter what you
  had chosen; and a zoom you set on one channel stayed on the next one you zapped to. Every channel now
  starts at your default, and a per-channel zoom lasts only for that channel.
- **Auto frame rate is respected for films and episodes on the standard player.** Turning it off did not
  stop that player from asking the TV to change refresh rate part-way through a file — the black flash
  the setting exists to prevent. With it off, the refresh rate is now left alone; with it on, the change
  only happens when the TV can do it without blanking.
- **Switching to Audio Mode during a slow start no longer shows "video could not be rendered".** The
  no-picture watchdog kept running after you had deliberately switched the picture off, so on a file
  that was still opening it fired and reported a decoding failure that had not happened — and could
  drop the rest of the session onto a slower decoding path.
- **A live channel left in Audio Mode no longer reconnects on a loop.** The compatibility player's
  no-picture watchdog counted an intentionally switched-off picture as a dead stream and kept
  reopening the channel, which on a provider that allows one connection could lock you out of it.
- **The Home screen's background preview follows Hardware decoding and per-item headers.** It ignored
  the Hardware decoding setting, so a TV whose decoder mishandles a stream still met that decoder on the
  home screen; and it dropped the per-channel User-Agent/Referer from an M3U playlist, so those items
  showed a black panel instead of a preview.
- **Detailed logging now records from the moment the app starts.** The switch only took effect once Live
  TV had been opened, so a diagnostics file sent after a session spent in Movies or Series came back
  empty or partial — exactly when it was needed.
- **HDR and Audio sync now say which player they apply to.** Both only ever affect the compatibility
  player: the standard player hands HDR straight to the TV and cannot shift audio against video at all.
  The rows say so instead of implying they apply everywhere.
- **Coming back from Audio Mode no longer drops the picture to a few frames a second.** On the standard
  player, going back to full screen from the now-playing bar switched the video on again a moment before
  the screen it draws into existed, so the decoder was set up against a stand-in and then re-pointed at
  the real one — which several TV chipsets survive only by rendering at a crawl. The picture now waits
  for the real screen and comes back at full speed. The compatibility player was never affected.
- **Prefer HLS no longer breaks the odd channel that has no HLS version.** The setting asks for every
  channel of a playlist in the HLS format, but a provider does not necessarily offer it for *every*
  channel — and those few came up black, or sat on a spinner that never cleared, after working perfectly
  before the setting was turned on. OwnTV now spots that and reopens just that channel in its original
  format, remembering it for the session, while every other channel keeps using HLS.
- **A live channel that never opens no longer spins forever.** If the standard player neither started
  playing nor reported an error — a stream that connects, and then simply never sends any video — nothing
  ever timed out, so the spinner stayed up until you pressed Back. There is now a time limit, after which
  the channel is handed to the compatibility player like any other failure.
- **A live channel that starts and then freezes for good is handed over too.** Every automatic switch to
  the compatibility player used to be decided in the first seconds; after that, a channel that died was
  left to the reconnect ladder, which is deliberately patient — over two minutes of frozen picture behind
  a spinner before it admits the connection is lost, and the other player, which often plays that very
  channel, never got a turn. A stall that lasts half a minute now switches players. Ordinary re-buffering
  is untouched: only a stall that does not recover counts.
- **A channel that loads video but never starts playing is now spotted in seconds.** Some streams — traced
  on a 4K channel in HLS whose provider mixes up its audio and video timing — fill the buffer completely and
  still produce no picture, because the player has plenty of data and nothing it can actually play at that
  moment. Everything OwnTV watched for was armed by the first frame, which is precisely what never arrived,
  so the spinner stayed. It is now detected about four seconds in and treated as a failure straight away, so
  the channel goes on to its original format or the compatibility player instead of holding the screen. A
  related case is covered too: one HLS stream part that cannot be lined up with the rest used to stall the
  player indefinitely, and now times out like any other loading problem.
- **A channel that can't fill the "Pre-buffer" opens anyway instead of spinning.** The setting asks the
  standard player to collect a few seconds of video before starting, but a live stream can only be loaded as
  far ahead as its provider publishes — and a heavy 4K channel on a short window may never get there. The
  wait then had no end: either a spinner that stayed up, or a picture that started and stopped several times
  a second, which no stall detection could see because every restart looked like a recovery. OwnTV now
  watches whether the buffer is still growing and, if it has stopped short, reopens that one channel without
  the pre-buffer and remembers it for the session. Every other channel keeps its pre-buffer, and the memory
  the setting is allowed now grows with it instead of being quietly capped.
- **Live TV gives up on a stuck channel sooner.** A channel that never opens is handed on after twelve
  seconds rather than twenty-five, plus whatever pre-buffer you asked for — the slow opens that needed that
  much blanket patience are the ones the two fixes above now identify properly.
- **A channel that won't play now works its way through all four combinations before giving up.** There are
  two players and, with "Prefer HLS" on, two stream formats — and a channel that one pairing cannot play is
  often perfectly fine on another. Until now only some of those steps existed and they ran in no particular
  order: the compatibility player had no failure detection of its own at all, so a channel it could not open
  simply spun; and once the standard player had established that a channel has no working HLS version, the
  compatibility player was pushed onto the original format too — losing the one combination that would have
  worked. A failed channel now walks a fixed ladder, each step tried at most once, starting from whichever
  player it opened on: standard+HLS → standard+original → compatibility+HLS → compatibility+original, or
  the same list led by the compatibility player if that is where it started. With "Prefer HLS" off, or on a
  channel whose playlist has no HLS version, the HLS steps are simply skipped, so it is just one player to
  the other. What each player learns about a channel is now kept separately, so neither one's failure
  disqualifies the other.
- **Switching to the standard player by hand no longer strands you on a spinner.** Choosing it from the
  channel menu turned off the automatic checks entirely — deliberately, so that a manual choice was not
  immediately undone — which meant a channel it genuinely cannot play sat there with no picture and no
  retry. The checks now stay on for a manual choice as well, so that channel falls through the ladder like
  any other and ends up somewhere that plays.

## v4.1.6 — 2026-08-01

### ✏️ Bulk editing — rename channels, movies and series in bulk (#86)

- **The Customize screen got a proper item list.** A category's name is now a focusable button (one
  D-pad press per row, instead of walking past the arrows first): OK opens that category's items —
  every row visible, hidden items marked and recoverable, paged so a category with tens of thousands
  of rows still opens instantly.
- **Per-row Rename on Live TV** renames one channel from the item list; Movies and Series get bulk
  rename only (one film exists in ten languages, so per-row renames are meaningless there).
- **Bulk rename with rules.** Long-press Rename (or use the header pill on Movies/Series) to select a
  span with the same remote-friendly flow as Hide/Move. Build an ordered rule list that adds or
  removes prefixes and suffixes, supports several removable alternatives separated by semicolons,
  and optionally ignores case and trims leftover separators. The live review shows every proposed
  name before anything is written. Applied names are stored per profile and section, survive
  re-syncs, and appear everywhere — Live TV, Movies, Series, search and recently-watched.
- **Auto cleanup** creates an editable rule preset for country/provider tags, quality and codec tags,
  emoji, symbols and stray separators, then opens the same review before applying anything.
- **Restore original names** undoes a bulk rename for the whole span — the only undo for a bulk
  apply, so it is one tap away and always available.
- Renamed names travel in backups and are per profile, like every other customization.

### 🗂️ Custom combined categories (#87)

- **Create your own combined categories** from the Customize screen — a "＋ New category" pill on
  every section. A combined category can hold channels, movies or series from any source folder,
  side by side, in one rail at the top of Browse.
- **Move to category…** is available in the Live/Movies/Series context menu and on the Customize
  item list. Items leave their origin folder by default (they stay findable in All Channels and
  search); tick "Keep in <origin> as well" to keep a copy. Moving out of Favorites un-favourites
  the item; moving out of another combined category removes it there.
- **Combined categories behave like folders**: rename them, delete them (items stay in their
  original categories), reorder their items manually, hide them from Browse, and they remember
  their own last-focused item. They are per profile and survive re-syncs and backups.
- **"Hide new categories by default"** is now set from the add-source window too — a profile-wide
  default so categories that arrive on a future sync start hidden until you show them from
  Customize.

### 🌐 Custom DNS server — global, app-wide (#90)

- **Settings → Network → DNS** is a new screen, a sibling to the existing Proxy screen. Configure a
  custom DNS server and all OwnTV domain lookups — playlist sync, Xtream/Stalker API, EPG, images,
  ExoPlayer streams — resolve through it instead of the system resolver.
- **Two modes, auto-detected from what you enter.** A plain IP like `8.8.8.8` or `1.1.1.1:53` sends
  standard DNS-over-UDP queries. A URL starting with `https://` — e.g. `https://dns.google/dns-query`
  — uses DNS-over-HTTPS (RFC 8484), which encrypts your lookups so they can't be snooped or tampered
  with between your device and the server.
- **One-tap presets for Google, Cloudflare and Quad9** fill the DoH URL instantly and save it. They light
  up to show which one is active.
- **The DNS server is live-updated** — flip it on and off, or swap servers, and every future lookup uses
  the new setting immediately. No client rebuild, same pattern as the global proxy.
- **"Test DNS"** resolves `dns.google` through your configured server and reports the resolved IPs and
  round-trip time, so you can confirm it's working before saving.
- **The toggle alone is not enough.** Turning it on reveals the server field and preset buttons. Only
  when a server is entered and saved is DNS actually enabled — a red warning reminds you while the
  toggle is on but no server is configured. Toggling it off immediately disables custom DNS.
- Custom DNS is backed up and restored alongside the other settings. It does not contain secrets, so it
  travels in plaintext in the settings section.
- **mpv/FFmpeg is not affected** — it uses the system resolver internally and has no configurable DNS
  option. The setting covers everything that goes through OkHttp, which is most of the app's traffic.
- Built-in support for Android's `org.json` parser so the DoH JSON responses (per RFC 8484) are parsed
  with zero additional dependencies.

### 🐛 Fixes

- **Live TV playback switched to the channel's provider category after opening it from Favorites,
  History, All Channels or a custom category.** The preview pane still shows the channel's real
  provider category as metadata, but full-screen playback now keeps the browse context it was launched
  from. D-pad Up/Down, CH+/− and the Left channel-list overlay all stay within that same context. A
  category explicitly chosen from the in-player category browser becomes the new playback context.
- **Auto frame rate could still blank the TV when the setting was Off.** Media3/ExoPlayer and mpv each
  had their own surface-level frame-rate request in addition to OwnTV's window-level controller, so
  disabling the toggle did not stop every display-mode request. All three paths now obey the setting,
  and turning it off clears an already-applied surface hint. AFR defaults to Off in v4.1.6; existing
  installs are reset to Off exactly once, while any choice the user makes afterward is preserved.

## v4.1.5 — 2026-07-31

### 💬 Subtitle appearance — size, colour, position and background, each optional (#96)

- **Settings → Video Player → Subtitle appearance** is now a menu rather than a single row: a live
  preview of how subtitles will look, a **Customize subtitles** master switch, and — once it's on —
  **Size**, **Text color**, **Position** and **Background transparency**, each opening its own popup.
- **Every option starts at "Default", and Default means "don't touch it".** Turning the master switch
  on changes nothing by itself. Set only the background transparency and only the background changes;
  everything else keeps the look the stream or the renderer gives it, including the styling
  broadcasters embed in Live TV captions and the fonts and colours authored into ASS subtitles.
- **Background transparency** answers the original request: a ±10% stepper from **None** (fully
  transparent) to **Solid**, so subtitles over a bright scene can get a readable backdrop without
  blacking out a strip of the picture.
- **Text color** offers quick presets, a full colour picker and a hex code.
- **Position** is six fixed anchors — top/bottom × left/center/right — drawn as miniature screens so
  you can see where the text lands before choosing. Useful when a channel burns a ticker or a logo
  into the exact spot subtitles normally sit.
- **Size** moved here from its own settings row, and now sits under the master switch alongside the
  rest. If you had already changed subtitle size, the switch is turned on for you at upgrade so your
  size is preserved.
- The look is applied by **all three subtitle renderers** — mpv's own, the Compose overlay used for
  mpv direct rendering, and the Media3 view used for Live TV and image subtitles — so it doesn't
  change depending on which engine happens to be playing. Image subtitles (PGS/VOBSUB/DVB) are picture
  data and are drawn as authored.
- Settings are per profile and travel in backups.

### 🆕 Date added — sort Movies and Series by what arrived most recently

- **"Date added" is a new sort mode for Movies and Series**, alongside Playlist order, A–Z and Rating.
  Press the sort button on the Movies or Series list to cycle to it. Newest titles come first, so a
  provider that keeps adding films puts the new ones where you'll actually see them.
- The date comes from your provider. **Xtream playlists** carry a real added/last-modified date for
  every movie and show, and that is what's used. **Stalker portals** carry one too, and OwnTV now reads
  it — though on a freshly built portal it is often the date the reseller bulk-imported the catalog
  rather than a real release date.
- **M3U playlists have no date to give** — the `#EXTINF` format simply has no such field. Rather than
  inventing one, "Date added" falls back to **reverse playlist order** for them: the titles at the
  bottom of your playlist, which is where providers append new ones, come first.
- Titles with no date always sort last, so a mixed catalog puts everything OwnTV knows a date for
  ahead of everything it doesn't, instead of scattering them.
- The sort is index-backed, so it stays instant on very large catalogs.
- Your choice is remembered per section and travels in backups, exactly like the other sort modes.

### ↕️ Series sorting — season and episode order, set per show

- The series episode view has a **"Sorting" button** that opens a small popup with two independent
  choices: **Seasons** and **Episodes**, each **Oldest first** or **Newest first**.
- **It's set per show, not globally.** A long-running series you're catching up on can stay oldest-first
  while a weekly show you follow shows the newest episode at the top — and neither affects the other.
- The two are genuinely independent: newest seasons first with oldest episodes first is a valid
  combination, and OwnTV keeps it.
- Changes apply the moment you pick them; **Back** closes the popup. Both default to **Oldest first**.
- **Playback order is never affected.** Autoplay still runs episodes in their natural order whatever
  you choose here — this only changes what you see.
- Opening a part-watched show still lands you on the last episode you watched, in either order.
- Your per-show choices are kept per profile and are included in backups with the manual-ordering
  section, so they come back on the right shows after a restore.

### 📺 Channel numbers — type a number on the remote to tune

- **Type a channel number while watching Live TV full screen** and OwnTV tunes straight to it, the way a
  set-top box does — no list, no guide, no holding CH+ through fifty channels. Works with the **number
  row and the numpad** on your remote.
- The number appears top-left as you type, with a **bar that drains over the two seconds** before it
  submits, so you always know how long you have to add another digit. Press **OK** to tune immediately,
  or **Back** to cancel. Five digits submit on their own.
- Once it resolves, the same card becomes the **channel OSD** — logo, name and number — and stays up
  until the new channel is actually on screen. If nothing matches you get **"Channel not found"** on that
  card with the number you entered.
- The number searched is your provider's own channel number, **from the playlist you're currently
  watching**. Only if that playlist has no channel with the number do your other active Live playlists
  get searched, so a number in your current playlist is never hijacked by another one.
- **Hidden channels and hidden categories are skipped**, and renamed channels show your name. If a
  playlist genuinely uses one number for several visible channels, the one from the list you opened wins;
  if that's still not decisive you get **"Multiple channels"** rather than a guess.
- **CH+/− keeps working right after a numeric jump**, even when you land far outside the list you opened.
- Channel numbers are now shown wherever you'd look for one, so you can learn the ones you use: the
  **Live TV channel list** and the **channel-list overlay** show the number in a fixed-width column ahead
  of the name — so every name still lines up whether the number is 7 or 101, and channels without a
  number keep their place in the column — the **full-screen top bar** shows it before the channel name,
  and the player's channel card shows **#number** under the name.
- Numeric tuning is only active on a live channel in full screen — during **catch-up or timeshift** the
  number keys are left alone.
- All of it is governed by one setting, **"Channel numbers"** (on by default) — in **Settings → Video
  Player → Live TV**, as a **quick-toggle chip** at the top of Settings, and in settings search. Off hides
  every number and ignores the number keys during playback; your playlist's numbers are untouched, so
  turning it back on restores them immediately.

### 📶 Prefer HLS for Live TV — per source, with format auto-detection (community PR #97 by @pt5pnzghm6-sys)

- **Xtream sources have a new "Prefer HLS for Live TV" option.** Xtream panels can serve a live channel
  two ways: as a raw MPEG-TS stream, or as an HLS playlist. OwnTV asked for MPEG-TS; some panels are
  markedly more stable over HLS, and on those the choice is now yours.
- The option is on the **Add source** screen and in **Edit source**, so you can flip it on an existing
  playlist without re-adding it, and it is stored **per source** — a setup with two providers can prefer
  HLS on the one that needs it and leave the other alone.
- **Nothing changes unless you turn it on.** MPEG-TS stays the default, which is what the great majority
  of panels serve best.
- **The ⓘ stream info overlay now shows a `Format` row** — *HLS* or *MPEG-TS* — so you can confirm what
  you're actually receiving rather than guessing from behaviour.
- **Catch-up and timeshift follow the source's setting** too, rather than being pinned to one format.
- The setting is part of the source record, so it survives backup and restore and is applied in remote
  mode as well.

### 💾 A proper backup file — `.own`, with your wallpaper inside and real encryption

- Backups are now written as **`owntv-backup.own`** instead of a plain `owntv-backup.json`. It is one
  container holding the backup itself plus any files that belong with it.
- **Your background image travels with the backup.** The Glass Effect wallpaper lives in OwnTV's own
  storage, and a backup only ever carried its file *path* — which means nothing on another TV, so the
  background silently came back blank after a restore. The picture's actual data now rides inside the
  `.own` file and is put back in place on restore. It's included whenever the **App settings** section
  is ticked.
- **A backup password now encrypts the whole file, not just the passwords in it.** Before, only the
  saved secrets (source & proxy passwords, TMDB key, OpenSubtitles login) were encrypted — your
  playlist URLs, usernames, profile names and watch history sat next to them in readable text that
  anyone opening the file could see. With a password, nothing in the file is readable without it, not
  even the list of what's inside.
  - Keep that password safe: **a `.own` backup encrypted with a password you've lost cannot be opened
    at all.** Without a password the file isn't encrypted and saved secrets are left out, exactly as
    before.
  - Because a protected backup can't be read until it's unlocked, restoring one asks for the password
    **first** and then shows what it contains. There is no "Skip" for these — there is nothing to
    restore without the password. Older encrypted `.json` backups are unchanged: sections first,
    password after, and Skip still restores everything except the saved passwords.
- **Old backups still restore, and always will.** Restore accepts both `.own` and any `owntv-backup.json`
  from an earlier version. The file is identified by its contents rather than its name, so a renamed
  file works too. Restoring an old `.json` no longer leaves a dead background-image path behind.
- Sending a backup between TVs over Wi‑Fi works exactly as before and transfers `.own` files, in both
  directions.
- Note for anyone downgrading: an older OwnTV build cannot read a `.own` file. Keep a `.json` backup if
  you plan to go back to an older version.

### 🗂️ Browsing & lists — decide what Live TV, Movies and Series come back to

- A new **Settings → Browsing & lists** popup with **six toggles**, two for each of Live TV, Movies and
  Series:
  - **Remember last category** *(on)* — reopening the section lands on the category you left instead of
    jumping back to *All*. Live TV has always worked this way; **Movies and Series now do too**.
  - **Remember last item** *(off)* — each category keeps **its own** scroll position instead of starting
    at the top. The Live TV toggle also restores the last focused channel when you re-enter Live TV.
- **Fixed: switching category kept the previous category's scroll position.** Picking a new category in
  Live TV, Movies or Series left the list wherever the last one had been scrolled to, so a fresh
  category could open halfway down. Every category now starts at the top by default, and only keeps its
  place if you turn "Remember last item" on for that section.
- The separate **App startup → Last channel** setting is untouched and independent of all six toggles.
  All six are included in backups.

### 🌍 Metadata language — descriptions and posters in your language

- **Settings → Metadata (TMDB) → Language** picks the language TMDB descriptions, titles and artwork
  come back in: **Default (English)**, **Device language**, or one of 40 languages (Greek, Arabic,
  Spanish, French, German, Hindi, Portuguese (BR/PT), Spanish (MX), Turkish, Vietnamese and more). The
  list is searchable.
- Changing the language **clears the cached metadata** so existing movies and series are re-fetched in
  the new language on next view. Title→TMDB matches are kept (they don't depend on language), so nothing
  has to be re-matched.
- Logo/artwork selection prefers your language, then English, then language-neutral art.
- Default is unchanged (English), so upgrading changes nothing until you pick a language.

### 🎞️ Auto frame rate — match the TV's refresh rate to the video

- **Settings → Video player → Auto frame rate** *(on)*: in full screen, OwnTV now asks the TV to switch
  to a refresh rate matching the video (24 / 25 / 30 / 50 / 60 fps) and hands the display back when you
  exit — so 24fps films and 25/50fps broadcasts stop juddering on a fixed 60Hz panel.
- **Fixed: auto frame rate did nothing on Android 10 and older devices** — including Fire TV Stick 4K /
  4K Max on Fire OS 7. OwnTV only used `Surface.setFrameRate()`, which doesn't exist before Android 11,
  so on those boxes there was no frame-rate matching at all, in **Live TV or VOD**. It now also requests
  the display mode at the window level, which works from Android 6 up.
- Applies to **both playback engines** (ExoPlayer and mpv) and to Live TV as well as movies and series.
  Only the full-screen player switches the display — the mini-player and the Live preview pane never do.
- Resolution is never changed: only the refresh rate varies, so a 4K output stays 4K. If the TV has no
  matching mode, or ignores the request, playback is unaffected.
- Turn it off if your TV or AV receiver re-handshakes HDMI noisily on every channel change.

### 🖼️ Guide channel logos — take logos from your XMLTV feed

- **Settings → EPG Sources → Add / Edit an EPG source → "Use this guide's channel logos"** *(off)*: that
  feed's own `<icon>` logos replace the ones your playlist supplies, in Live TV, the channel lists, the
  Guide, Search, Home and the player.
- It is set **per EPG source**, not app-wide, so one feed can supply logos while another only supplies
  programmes.
- Channels the feed has no logo for keep their playlist logo, so a partial guide never leaves blank tiles.
- Your provider's logos are never overwritten in the database — this is a display override. Turn it off
  and the playlist logos come straight back, and a catalog re-sync can't undo your choice.
- Logos are stored when the feed is parsed, so **re-sync the EPG source once** after switching it on.
- The setting is included in backups, alongside that source's Auto refresh choice.

### ↕️ Span move — reorder a whole block of categories at once

- **Settings → Customize Categories & Items** already let you long-press **Hide** to select a *span* of
  categories and hide them all together. The same span selection now works on the four **move** buttons
  (**⤒ ↑ ↓ ⤓**), so a block of categories can be reordered in one go instead of one row at a time.
- **How it works**: long-press any arrow on the first category to anchor the span, then press an arrow on
  the last category — every category in between moves as one block, keeping its internal order: **↑ / ↓**
  step it one place, **⤒ / ⤓** send it straight to the top or bottom of the list.
- The block **stays selected after the move**, so the arrows can be pressed repeatedly to walk it further
  up or down. **Back**, the banner's **Cancel**, or switching section clears the selection.
- Every category in the selected block is **tinted**, not just the anchor, so the span is visible at a
  glance; the banner shows how many categories are selected and what the arrows will do.
- A move that would run off either end is ignored, and while a *move* span is active the **Hide** button
  goes back to a plain single toggle — the two span modes never fight over the same press.
- Single-row **⤒ ↑ ↓ ⤓** behaviour is unchanged; internally a single move is now just a block of one.

### 📺 Live TV full screen — redesigned top bar, and a History channel list

- **The top bar is now one strip**: back · channel logo · quality/audio chips · channel name ·
  **Now / Next guide**. The floating channel card that repeated the channel name and the tall guide
  card pinned to the right edge are both gone, so the picture is far less covered.
- The **Now** line shows a thin accent progress bar and how many minutes are left, and refreshes on its
  own while you watch; **Next** sits beside it, dimmed.
- **Fixed: the ◀ channel list showed the wrong channels.** Pressing Left in full screen listed whatever
  rail you happened to launch from (History, or *All channels*), not the channel's own category. It now
  always lists the playing channel's category — with its name as the heading — no matter how you got
  there. Uncategorised channels fall back to *All Channels*.
- **New: press ▶ in full screen for a History channel list** — the last 30 channels you watched, with
  what's on now, so you can hop back to a recent channel without leaving full screen. Press ▶ again, or
  Back, to close it. Both lists respect hidden channels, hidden categories, renames and manual order.
- **Player controls tidied**: the redundant *exit full screen* button is gone (Back already does it),
  **stream info** moved to the far right with a clearer ⓘ icon, and the speed button shows just `1.0x`
  without the extra `»` glyph.
- The top and bottom control bars now sit on a **soft dark gradient**, so white icons and text stay
  readable over a bright scene.
- **Favorites now use a heart everywhere** — Live TV, Movies, Series, Search, posters and the player —
  instead of a star, which on a poster reads as a rating. The star is still used for ratings and for
  selection ticks.

### 🗂️ Category browser in the player — switch Live TV category without leaving full screen

- **Press ◀ a second time** inside the full-screen channel list and a **category browser** slides in over
  the picture, listing every Live TV category. Pick one with **OK** and the channel list reloads with that
  category's channels — the stream you're watching keeps playing throughout.
- The category you're currently in is **highlighted and focused first**, so a second Left followed by OK
  puts you back where you were. **Back** or **◀** returns to the channel list without changing anything.
- The list respects your customizations: **hidden categories are left out, renames are shown, and your
  manual order is kept** — the same categories you see in the Live TV rail.
- After you switch, **CH+/− follows the new category**, so channel surfing continues in whatever you just
  browsed to.
- Community contribution — PR #95 by @cotol1985.

### 📼 Catch-up from Live TV, and catch-up in the player of your choice

- **The Live TV catch-up picker now opens the same programme popup the Guide does.** Long-pressing a
  catch-up channel and picking a past programme used to start it immediately. It now opens the
  programme details — description, times, and the choice of **Watch from start**, **Watch channel**,
  favourite the channel, or close — exactly as the Guide has always done. The popup is drawn compact
  here, since it sits on top of the channel picker.
- **New: play a catch-up recording in an external player.** Archive recordings are the hardest streams
  for any in-app engine (providers serve them mid-GOP), so VLC or MX Player is now an option for them.
- **Settings → Playback → Catch-up** (renamed from *Catch-up time*) gained **Play catch-up in**:
  - **OwnTV player** *(default — unchanged behaviour)*
  - **External player** — every recording goes straight to VLC/MX Player
  - **Always ask** — pressing *Watch from start* asks which player to use, each time
- Sent to an external player, a recording loses the OwnTV HUD, resume position and the engine toggle.
- The timezone/offset controls are unchanged and still live in the same popup. The new setting is
  included in backups.

### ▶️ External player — Live TV support, and a default per section

- **Live TV can now be played in an external player.** Long-press any channel → **Play in external
  player**. It is always offered, whatever your default is, as the escape hatch for a channel neither
  in-app engine can open.
- **The single external-player switch is now three.** *Settings → Video Player Settings → External
  player* opens a popup with an independent **On/Off for Live TV, Movies and Series**, so you can send
  live channels to VLC while keeping movies in OwnTV (or the other way around). Downloads follow the
  Movies or Series setting depending on what was downloaded.
- Your existing setting is carried over to **Movies and Series**; **Live TV starts off**, so upgrading
  never silently starts throwing channels at another app. All three are included in backups.
- **Fixed: "no external player found" for live channels.** Live streams ending in `.ts` or `.m3u8` were
  offered to other apps under a MIME type VLC and MX Player don't advertise, so nothing matched even
  with both installed. OwnTV now widens the type until a player accepts it.

### 🛡️ Your library can no longer be wiped by a database problem

- **A schema problem no longer deletes everything.** OwnTV used to be built to drop every table and
  start empty if the database didn't look the way it expected — the failure mode behind the 4.1.0
  upgrade reports. That is gone. The database is now opened on a background thread before the UI, and
  if it can't be opened you get a **recovery screen** with **Try again** and a **Reset app data**
  button behind a second confirmation. Nothing is erased unless you ask for it.
- **A half-finished sync can no longer delete your catalog.** If a provider returns a short or broken
  response, OwnTV now refuses to remove titles when that would delete more than half of a source, and
  Stalker cross-checks the channel dump against the portal's own item count before removing anything.
- **Xtream panels that are too big to answer in one request** ("response too large") fall back to
  per-category loading, and now clean up correctly — only inside the categories that actually
  answered, so uncategorised titles are never dropped.
- **Provider reorders no longer rewrite your whole catalog.** A changed sort order is now detected on
  its own, so a 170,000-item library updates the rows that moved instead of all of them.
- **Backups are written safely.** A backup is written to a temporary file, flushed, and only then
  swapped in — with the previous copy kept as a fallback that is used automatically if the newest file
  is unreadable. An interrupted restore is no longer silent: OwnTV notices on the next start and tells
  you.
- **Manual ordering survives a re-sync.** Items you moved by hand in folders and Favorites are now
  included in the pre-sync snapshot, like favorites, history and resume positions already were.
- **A backup containing a source type this build doesn't know is skipped and reported**, instead of
  being silently imported as an M3U playlist.

### 📡 Live TV that recovers instead of giving up

- **A single hiccup no longer kills a live channel.** The "stop immediately" shortcut meant for VOD was
  firing on live streams too; live now goes through the full retry ladder.
- **Reconnect keeps trying.** The retry ladder is 1.5 / 3 / 6 / 10 / 15 s and then holds at 15 s
  instead of giving up after roughly half a minute, and it only resets once playback has actually held
  for a minute.
- **Outages recover by themselves.** If the network drops long enough for the channel to stop, OwnTV
  now resumes it as soon as the connection is back — no matter how long it was gone.
- **The error log stopped clearing itself** after an internal player reset, so playback problems can
  actually be diagnosed.
- **Raw MPEG-TS channels no longer drop the connection every 10–15 seconds.** A live MPEG-TS channel is
  one long-lived HTTP response, so it is the *gap* between reads that matters, not how much video is
  buffered — and OwnTV's buffer settings left that socket idle long enough for providers and middleboxes
  to cut it, producing a glitch every few seconds on channels that stream fine elsewhere. The live buffer
  now keeps the connection being read often enough to stay open, without holding more memory. HLS
  channels were never affected, because each segment is its own request.

### 🎬 Playback fixes

- **Audio-only content no longer shows a playback error.** Radio stations filed under Movies, music
  videos and audio-only catch-up used to fail after 8 seconds with a "video could not be rendered"
  message. Content that really does declare video and fails to show it still reports the error.
- **"Compatibility mode" and the player-engine choice now stick on Stalker portals.** Those pins were
  stored against the stream link, and Stalker issues a brand-new link every time you press play, so
  the pin never matched again. They are now stored against the item itself. Existing pins on
  Xtream/M3U are carried over automatically.
- **Short clips no longer count as "watched" at position 0**, and content with an unknown length is
  never marked finished.
- **4K playback surface handling** and the mpv→ExoPlayer handoff now follow the same timing rules as
  every other engine switch, which removes a class of black-screen-after-switch cases on Realtek boxes.
- **4K channels no longer fall back to "compatibility mode" when you tune from one to the next.** Moving
  between 4K live channels dropped the second one to mpv with a decoder error, even though the very same
  channel played perfectly on ExoPlayer if you pressed the engine toggle. Some TV chipsets — Realtek
  boxes in particular — accept only **one** 4K decoder per video surface: releasing the first channel's
  decoder leaves the surface unusable, so the next channel's decoder starts and then dies about a second
  later. Toggling to mpv and back happened to rebuild the surface, which is why that always "fixed" it.
  Leaving a 4K channel now rebuilds the surface along with the decoder, so the next 4K channel gets a
  clean one and plays on ExoPlayer directly. Waiting longer between channels never helped and this is not
  a delay — a failing tune and a working one were within 30 ms of each other. Channels below 4K are
  untouched and zap exactly as before.
- **Subtitle timing offset no longer freezes the UI** — the shifted subtitle file is generated in the
  background and cached.
- **Switching engine during a catch-up recording no longer jumps to the live programme.** The player
  now knows an archive recording is playing and reloads the same recording at the same position,
  instead of re-tuning the channel and dropping you onto whatever is on air now.
- **Fewer "failed on both engines" errors.** When mpv had to be torn down and playback handed to
  ExoPlayer, the handoff could grab a video surface that was already being replaced and die instantly
  on an item that played fine on the next try. The handoff now waits for the new surface.
- **The resolution badge no longer under-reports wide-format streams.** The stream-info overlay worked out
  the quality label from the picture **height** alone, so a channel broadcasting a wide 1920×800 picture was
  labelled from its short edge and read **720p** even though it's a 1080p-class stream. A cinema-format
  picture is only cropped top and bottom, so the **width** survives it: the label is now taken from whichever
  of the width and the height implies the higher class, and cinema-format and letterboxed channels report the
  quality they actually deliver. Both playback engines use the same rule, so a channel can no longer show one
  quality full screen and another in the preview pane, and anything below 480p still reports its **true**
  height rather than being rounded up.

### 📺 Live TV

- **Choosing ExoPlayer for a channel that had fallen back to mpv now sticks.** When a channel dropped to
  mpv automatically, pressing the player's engine toggle to go back to ExoPlayer re-started it on
  ExoPlayer — and the fallback watchdog, armed again by that restart, immediately sent it back to mpv, so
  the button looked like it did nothing. Picking ExoPlayer is now treated as a deliberate override: the
  automatic fallback stays out of the way for that channel until you tune elsewhere. If ExoPlayer really
  can't play it you stay there and can press the toggle again for mpv. Automatic tunes are unchanged —
  ExoPlayer first, mpv if it fails.
- **Live preview no longer plays sound on surround channels when preview audio is off.** Channels with
  5.1 audio (Dolby Digital / DTS) kept playing sound while browsing even with **Settings → Live TV →
  Preview audio** turned off. The preview was muted by volume alone, which has no effect on a surround
  bitstream passed straight through to the TV over HDMI — the TV received it at full level. The preview
  now switches the audio track off entirely while muted, so every channel stays quiet. Radio and other
  audio-only channels are unaffected and still play.
- **The Preview audio setting now applies to a preview that's already playing.** Turning it on or off
  took effect only on the next channel; it now changes the current preview immediately.
- **A live channel opened from the Guide now appears in History.** Tuning a channel from the guide grid
  (or *Watch channel* in a programme popup) is recorded straight away, rather than going through the
  delay that exists to keep rapid channel-surfing out of your history.
- **CH+ and D-pad Up now go to the next channel in full screen, not the previous one (#84).** Channel
  surfing ran backwards: CH+ moved *down* the list and CH− moved up, so on the first channel CH+ jumped
  to the very last one instead of to channel 2. Every surfing key now points the same way — **CH+,
  D-pad Up and Next all go to the next channel; CH−, D-pad Down and Previous go to the previous one** —
  matching how a set-top box and every other TV app behave. Wrapping around is unchanged and still
  intended: going down from the first channel lands on the last, and up from the last returns to the
  first.

### 📅 Guide & series

- **New episodes now appear in a series you already opened.** Episodes were cached once and never
  refreshed, so a show you had opened before would never gain a new episode. They now refresh every
  6 hours and after a sync — and merging keeps your watch progress and resume positions on the right
  episodes. A failed or empty fetch never empties a show that already has episodes.
- **A truncated guide download is no longer trusted for 24 hours.** The downloaded guide is only
  promoted to the cache once it has been fully parsed.
- **Channels added while a guide sync was running now get their programmes**, instead of staying empty
  until the next full guide download.
- **Guides emptied by the 4.1.x upgrade refill themselves** once, automatically, on the next start.
- **"Resync now" vs "Resync and remove missing titles"** — refreshing a source now asks which you want,
  so removing titles a provider has dropped is a deliberate choice. Neither option deletes your data.
- **The sync status pill** shows one line per running sync with real per-type counts, instead of
  collapsing everything into a single line.

### ⬇️ Downloads that survive the background

- **Downloads keep running when you leave the app.** Transfers moved to a proper foreground background
  worker with a notification, so Android no longer kills them the moment OwnTV goes to the background.
- **Pause and resume no longer re-download what you already had** — the real file length is saved when
  you pause, and a download interrupted by the app being killed resumes rather than restarting.
- **Pulling out the USB/SD card mid-download fails the download** instead of quietly continuing into
  internal storage.

### 🚀 Startup & speed

- **A branded splash instead of a blank window** on cold start, held until OwnTV actually knows which
  screen to show (with a 4 s safety limit).
- **Roughly 3× faster to a usable screen** on the developer's TV with a full catalog: shell 1879 →
  557 ms, Home data 3171 → 1096 ms, guide preload 3072 → 1782 ms.
- **A fresh install is no longer slow for the first few days.** Android normally leaves most of an
  app's code in a slow form and only speeds it up gradually as it learns what you use — which hits
  sideloaded apps like OwnTV hardest, since every new release starts that over. The APK now ships a
  recorded startup profile so the code that runs at launch is compiled ahead of time, from the very
  first launch after installing.
- **The database is no longer repaired on every single open** — it is checked first and only repaired
  when something is actually missing.
- **Settings reads no longer wake every screen** on each preference write, and around 95 background
  data streams now stop when nothing is watching them.
- **The image cache is sized to the free space available** (up to 250 MB, 5% of free space, never below
  32 MB) instead of a fixed guess.
- **Browsing storage folders and importing a background image no longer block the UI.**

### 🛠️ Setup & appearance

- **Fixed: Stalker portals with a "virtual" MAC were rejected at setup.** Some panels hand out MACs
  containing letters past F (for example `…:PQ`), which the app refused with *"Enter 12 hex digits"* —
  locking those users out entirely. The portal only ever echoes the MAC back to itself, so there was no
  protocol reason to insist on hexadecimal. Any 12 letters/digits are now accepted, in any of the usual
  separator styles, which still catches typos and truncated pastes.
- **Fixed: picking a new background image did nothing until the app was restarted.** In Glass Effect
  mode, choosing a second image of the same file type reused the same filename, so nothing detected a
  change and the previous picture stayed on screen. Each pick now lands under its own name and appears
  immediately; the old file is still cleaned up, so only one background is ever kept.
- **Fixed: the + / − buttons became unreachable once a setting hit its maximum (#88).** In **Mini-player →
  Size**, setting the size to its 50% maximum left the picker with nothing selectable on the next visit —
  the D-pad did nothing and only Back got you out, so the size could never be turned back down with the
  remote. The dialog always tried to select the **+** button, which is disabled at the top of the range
  and so cannot be selected, and focus is deliberately kept inside the dialog. It now selects whichever
  button is usable, and hands over to the other one if the one you are on runs out of range mid-adjust.
  The same dialog is used by **A/V sync** and the **custom live buffer**, which were stuck the same way at
  their maximums.

### Internal

- **The bundled baseline profile was rebuilt from obfuscated names and did nothing.** Every release
  build reshuffles those names, so ~98% of the 7,238 recorded entries matched nothing in the shipped
  app — and `assembleStandardRelease` printed ~7,100 *"Startup class not found"* warnings because of it.
  The dead recording is removed. The baseline profiles that come from Compose, coroutines, lifecycle and
  Room are unaffected and still ship, so startup speed is unchanged. Recording our own again is blocked
  on an upstream fix; see `future-plan/baseline-profile-agp9-plan.md`.
- **Tests and lint now gate CI.** Pull requests run unit tests and Android Lint before anything is
  built, lint fails the build on an error (0 errors, from 122), and reports are uploaded on failure.
  APKs are still only built for `main` and tags.
- **New tests** for the database migration chain (every exported schema version migrates to the current
  one; the repair path restores every guaranteed index and search table), for backup merge/restore id
  remapping, and for the re-linking that keeps favorites, history and resume positions attached across
  a sync.
- **Release notes now come from `CHANGELOG_APP.md`, not `CHANGELOG.md`.** The tag build in
  `android.yml` was publishing the newest **full** `CHANGELOG.md` section as the GitHub release body —
  and GitHub's auto-generated commit list on top of it — which is what the in-app update dialog shows.
  It now extracts that tag's short, bullet-only section from `CHANGELOG_APP.md` and no longer appends
  the generated notes. `CHANGELOG.md` stays what it was meant to be: the detailed changelog developers
  and contributors read on GitHub by hand.
- `release-notes.yml` did already do this, but never ran for tag builds: a release published with
  `GITHUB_TOKEN` doesn't trigger other workflows. It remains as the fallback for releases published by
  hand from the GitHub UI. Both jobs now match the version header as a whole word, so a header without
  a trailing date (`## v4.1.5`) is found too.

## v4.1.4 — 2026-07-24

### 🧊 Glass Effect — frosted translucent interface over your own background photo

- An opt-in **glass look**: content panels, sidebar, preview panes, dialogs, top bar, cards and the
  mini-player turn **translucent with a real frosted-blur backdrop** over an optional **background
  photo** — glassmorphism on TV. Everything lives in one **Settings → Glass Effect**
  dialog: Glass effect On/Off, the background image, a **Transparency** stepper (20–95%), a
  **Blur / Frost** stepper (0–100%), a **Surfaces** sub-menu, and **Reset**.
- **Background image — Local or Remote.** **Local** picks a photo from USB/device storage (copied into
  app-private storage so unplugging the stick can't blank it). **Remote** sends one from your phone:
  the TV shows a **PIN + QR** (the same companion pairing as Remote Backup & Restore), the phone opens
  the page on the same Wi-Fi and uploads a JPG/PNG/WebP/BMP, and it applies instantly.
- **Per-surface control.** The **Surfaces** menu toggles glass individually for content panels,
  sidebar, preview panes, dialogs & popups, top bar, cards and the mini-player — or all at once with
  one master row. Turning every surface off turns glass off.
- The frost is a real blurred slice of the photo aligned behind each panel, pre-processed with a
  matching brightness scrim and a slight saturation lift so the glass blends with the scene instead of
  reading backlit. It is computed once per image at a downscaled size, so it stays cheap on low-end
  boxes. Backdrop blur needs Android 12+; older devices fall back to translucency without frost.
  All glass settings persist per install and are included in backups.
- **Every focused/selected control now frosts, not just the big panels.** The glass highlight was
  extended to the focus rim itself: cards, list rows and chips on every content screen, every row and
  list item inside popups/dialogs (pickers, storage browser, avatar/subtitle/backup/profile lists,
  EPG match review), the sidebar's profile/avatar buttons, the action pill buttons (Save / Cancel /
  Add / Edit / Delete / Done), and every search bar (category rail, Live/Movies/Series/Search/EPG
  panels, and the in‑popup search fields) — so a pill or search field inside a dialog frosts with the
  dialog and one on a panel frosts with the panel. The fullscreen player stays solid by design. Four
  dialogs that previously kept a flat fill (Video Player pickers, EPG "Fill from playlist", Live
  catch‑up, the Guide programme popup) now frost with the rest.
- **The player's Subtitles & Audio pickers are now glass too.** Their track rows and the A/V-sync
  buttons frost like the rest of the interface (they previously stayed flat inside the glass panel),
  and the popups were tightened to the compact style used by the storage picker — a narrower box with
  a smaller font.

### ⭐ Favorite from the player — add to Favorites without leaving the stream

- A **star button** in the fullscreen player's control bar favorites (or un-favorites) what you're
  watching **without backing out** to the list — a live channel, a movie, or a series (an episode
  favorites its parent show). The star fills when the item is already a favorite and updates
  instantly, and it survives channel zapping. (community suggestion)

### 🗂️ Per-section sync scope — choose Now / Later / Off for each section (#74)

- Every source now controls **Live**, **Movies** and **Series** independently with a **Now / Later /
  Off** scope instead of the old on/off sync toggles. Set a section to **Off** and it is never
  synced or shown (the long-requested "don't load VOD" — turn Movies off and the huge movie catalog
  is skipped entirely), **Later** keeps it available to sync on demand without running now, and
  **Now** syncs it with the rest. Editable per source in **Setup** and **Settings → Manage sources**,
  and from the LAN **companion** page (Now/Later/Off dropdowns replace the old checkboxes).
- A source with **every** section Off does no sync work at all. Changing a section's scope resyncs
  just that source. Backups carry the per-section scope forward, and upgrading preserves your existing
  behaviour (all sections default to **Now**). (community PR #78)

### 🎨 Accent color — full HSV picker with a live preview

- The accent dialog is rebuilt around a proper **color picker**: a **hue bar** and a large
  **saturation / brightness square**, each a D-pad "enter-to-edit" control — focus it, press **OK** to
  step inside (it glows amber), move with the D-pad, **OK/Back** to step out — plus a **live preview**
  circle and a trimmed set of **6 quick presets**. Type an exact **hex code** at the top and **Apply**,
  or dial one in and **Use this color**.
- **Custom hex accents now render exactly.** Entering a hex code used to pin its lightness and show a
  nearby shade; the seed color is now used verbatim as the accent (only the contrast roles are
  derived). The dialog uses the shared Lora popup styling.

### 🎧 Audio Mode — listen with the screen free

- A new third player mode, alongside fullscreen and the docked mini-player: **switch the current
  stream to audio-only** and keep browsing. Video decoding is stopped entirely (true audio-only, not a
  hidden video), and a compact **now-playing bar** appears in the top bar — an animated equaliser,
  the title, and transport controls (play/pause, previous, next, volume, fullscreen, close). The
  equaliser dances while sound plays and freezes flat when paused. Live shows a pulsing **LIVE**
  badge; movies/episodes show a slim progress line with remaining time.
- **Enter it** from the **headphones button** on the fullscreen player controls or on the docked
  mini-player. **Two-stage D-pad focus:** move onto the bar and it highlights as one target; press
  **OK** to step inside, where Left/Right move between the buttons and OK runs the focused one; focus
  stays locked in the bar and **Back** is the way out. **Fullscreen** returns to full video, **close**
  stops playback. Works for Live TV (both engines), movies and series.

### 📤 Remote Backup & Restore — move a backup between TVs over Wi-Fi

- **Remote restore.** **Settings → Backup & Restore → Restore from another device**, and the same
  option in the first-run / add-profile **setup wizard**, open the LAN companion server in
  backup-upload mode and show a **PIN, a QR code, and the URL**. A phone or laptop on the same Wi-Fi
  opens the page and uploads an OwnTV backup JSON straight to the TV; when it arrives it flows into
  the normal restore path (section picker, backup-password prompt). No cloud, no USB stick, no file
  browser on the TV.
- **Remote export.** **Settings → Backup & Restore → Send to another device** serves the exported
  backup from the TV so a remote device on the same Wi-Fi can **download** it — the mirror of remote
  restore for getting a backup *off* the TV.
- Both reuse the existing companion **PIN + QR pairing**, the profile / section pickers and the same
  encryption as local backups; the listener stops automatically when you leave the screen. Local
  backup/restore (USB, on-device file) is unchanged.

### 📡 Live TV latency control (#72)

- **Settings → Video Player → Live latency** trades how close to the live edge you play against
  stability: **Low latency**, **Balanced** (default), **Stable**, or a **Custom** buffer in seconds.
  It applies on the next channel open, to live streams only (VOD is never affected).
- Works on **both engines** — ExoPlayer live uses it as the HLS live-edge target offset, mpv live as
  the demuxer read-ahead. **Balanced applies no override at all**, so it can never regress a stream
  that already plays well. Picking **Low latency** (or a below-Balanced custom value) shows a quick
  heads-up that a smaller buffer can stutter on weaker connections.

### 🪟 Configurable mini-player

- The docked mini-player (live PiP) now has an adjustable **size** (percentage of screen width) and
  **screen position** (six docking spots — the four corners plus top/bottom centre), set in
  **Settings → Playback → Mini-player** and also changeable **on the fly** from the mini-player's own
  resize / move controls. The window is laid out proportionally (`fillMaxWidth% × 16:9`), so it scales
  consistently across TV sizes and the UI zoom instead of the old fixed box.

### 🖼️ Live TV preview pane — info-only, genre dots, EPG coverage

- **The preview pane is now informational only — the action buttons are gone.** Favorite / Rename /
  Hide / Match EPG / Catch-up all moved to the long-press channel menu (where Move and Remove-from-History
  already lived), so nothing in the pane is selectable or focusable any more. **Right-arrow no longer
  enters the pane** — D-pad focus stays in the channel list — which fixes the common complaint that a
  stray right press dropped you onto the buttons by accident. The pane instead shows a short note
  ("Press OK to watch fullscreen · Long-press for options").
- **Channel metadata row.** Under the channel name, a compact row of chips shows the channel's **real
  category** (resolved from its `categoryId`, so it's correct even when you're browsing via Favorites /
  History / All — never the browse context), its inferred **genre** with a colour dot, **catch-up**
  availability (with days, e.g. "Catch-up · 7d"), and **EPG coverage** ("EPG · Nd" from the stored guide
  span, or plain "EPG" / "No EPG"). Every channel gets a genre marker — unmatched categories fall back to
  a neutral grey **Other** dot rather than none.
- **Shared genre colour system.** The Guide's category→colour inference and the preview's genre dot now
  use one shared `ChannelGenre` helper (sport→green, news→red, movies→violet, kids→amber, music→blue,
  documentary→teal, other→grey), so the two surfaces agree. The chips use the Lora serif font and a
  uniform fixed height so long category names never make one chip taller than the others.

### 🔄 Sync reliability — completion notices, restore visibility, concurrent sync

- **Sync completion pill.** When a catalog sync finishes — success, failure, or cancel — the global
  status pill now shows the result for a few seconds ("Sync complete · Playlist · 3 categories added")
  instead of silently disappearing. Multiple back-to-back completions queue and display one after
  another. (community PR #73 by @pt5pnzghm6-sys)
- **Restoring a backup no longer hides all your channels.** A restored source starts with empty
  catalog tables, but its saved `lastSyncAt` timestamp made the first post-restore sync behave like a
  *re-sync* — and with "hide new categories on resync" on, every category looked "new" and got hidden,
  leaving the screen empty. Restored sources now take the fresh-install sync path, so your restored
  show/hide preferences are honored exactly as they were. (community PR #73)
- **Concurrent playlist syncs no longer corrupt each other — and still run in parallel.** Syncing
  two or more playlists at once (manual resync, startup auto-refresh) used to race on the shared
  SQLite tables: one source's index/FTS-trigger drop-and-restore cycled against another's concurrent
  writes, throwing `SQLiteDatabaseLockedException`s that truncated the second source's movies and
  skipped its series entirely — silently reported as success. PR #73 added a per-table index lock
  that fixed the "trigger already exists" crash; this release closes the remaining race at its
  source: a second sync arriving on a table in bulk-insert mode now *joins* that mode (writer-counted)
  instead of bypassing the lock, and the index restore waits for the last writer. Sources download,
  parse and insert fully in parallel — no app-wide queueing — with every playlist syncing to
  completion regardless of how many run at once. (community PR #73 by @pt5pnzghm6-sys)
- **Incremental M3U resync — no more clear-and-reimport.** M3U playlists used to be wiped and fully
  reinserted on every resync (playlists carry no provider item ids), which was slow on big playlists,
  briefly emptied the grids mid-sync, and re-created every row so favorites/history/manual order
  pointed at dead entries. Each M3U item now gets a stable synthesized key (name + group), and
  resyncs run the same hash-diffed upsert as Xtream/Stalker: unchanged items are skipped, changed
  items (including reordered playlists and series that gained/lost episodes) update in place keeping
  their identity, and removed items are pruned. **Favorites, watch history, playback progress and
  manual ordering on M3U content now survive resyncs.** The first resync after this update migrates
  old rows to stable keys once (that one resync still relinks like before; per-item hide/rename
  customizations on M3U sources reset once); every resync after that is incremental. A failed
  download or a playlist missing a content type still never wipes existing rows.

### 🐛 Fixes

- **Settings → About shows the updated Telegram group QR code.**
- **Editing a source no longer shows the other source types.** The Edit-source screen listed all the
  type chips (Xtream / M3U / Stalker) even though the type can't change while editing. It now shows
  only the chip matching the source you're editing.
- **Accent hex code field is no longer hidden behind the keyboard.** The hex input sits above the
  color picker so the on-screen keyboard can't cover it while you type a code.
- **Latency warning popup: focus returns to the Live latency row.** After picking **Low latency** (or
  a below-Balanced custom value) and dismissing the heads-up with "I understand", focus used to jump
  to the first row of Video Player settings ("Hardware decoding") instead of the row you were on. The
  picker→popup transition was clearing the pending return-focus target; it is now preserved through
  the popup so focus lands back on the Live latency row.
- **Live preview off: audio no longer keeps playing after you leave a channel.** With the in-pane
  Live preview turned off in Settings, exiting a full-screen live channel left the ExoPlayer engine
  decoding the stream's audio in the background (nothing re-took the engine to silence it, unlike when
  preview is on). Leaving full-screen now stops that engine when the preview is disabled.
- **4K live channels no longer lag/judder on mpv when a provider sends broken timestamps.** Some IPTV
  4K feeds send non-increasing / duplicate presentation timestamps; mpv is strict about PTS and was
  dropping nearly every frame (render output collapsing to ~8–12 of 30 fps) while decode itself was
  fine — so the channel looked laggy on mpv even though ExoPlayer played it cleanly. Live playback on
  mpv now derives timing from the container FPS (`correct-pts=no`), stops chasing the audio clock
  (`video-sync=desync`), and no longer drops frames (`framedrop=no`) — all **live-only**, so VOD keeps
  accurate PTS/seeking and normal frame-dropping. Confirmed on Realtek 4K hardware across 24/30/50/60 fps
  channels with zero frame drops.
- **Playlists & EPG Sources menus: focus now stays inside the list.** Entering either sub-menu used to
  land focus on the "Add" button instead of the list; after editing, re-syncing, or deleting a source,
  focus escaped the menu to the "Add" button. Both screens now track the row you acted on (per-row
  `FocusRequester`) and restore focus to that same row on edit/re-sync, move it to the nearest surviving
  neighbour on delete, and fall inside the list on entry.
- **Settings dialogs no longer let D-pad escape behind the scrim.** Every scrim dialog in Settings
  (Zoom, Accent, Theme, About, Playback error log, Clear history, Catch-up time, plus the Backup,
  Video Player picker/stepper, Customize and shared Number/Picker dialogs) was missing the focus trap,
  so a D-pad press toward the edge could land on the settings rows behind the dialog. All now use
  `trapAllFocusExit` like the rest of the app.
- **No more "scroll animates from the top" when closing a Settings / Video Player dialog.** Opening a
  scrim dialog over a scrollable settings list reset the list's scroll to the top, so closing it made
  the list visibly scroll back down to the row you came from. The scroll position is now snapshotted
  when you tap a row and restored instantly on dialog close, so the list stays exactly where it was.
- **Settings dialog-close focus return hardened.** The `dialogReturn` target (which row to refocus when
  a dialog closes) was being cleared in the wrong place, so it leaked and could misroute the next
  directional entry; it is now cleared in the restore effect itself. The entry fallback is also
  search-aware (uses the always-bound search field while searching, instead of an unbound row).
- **OpenSubtitles, Network & Metadata settings: focus no longer escapes on entry / state changes.**
  These three screens had no focus-group safety net, so entry focus could fall to the sidebar. The
  OpenSubtitles screen also stole focus back to the first row on every server state change (e.g. after
  pressing Refresh) and never restored focus when returning from the Delete-subtitles screen with no
  state change — all fixed.
- **Profiles, Mini-player, Customize, CH+- paging, Weather: focus returns to the row that opened a
  dialog.** Closing a dialog in these sub-menus used to send focus to the screen's first row. Each now
  tracks its opener row and restores focus there; the CH+ / CH− skip rows also got their own
  `FocusRequester`s (they had none).
- **Long-press context menus in Movies / Series / Live / Guide no longer let D-pad escape behind them.**
  The long-press menus used the OK-key guard but not the focus trap; D-pad could now escape behind the
  scrim. All now trap focus inside.
- **Downloads: focus moves to the next download when you delete one.** Deleting a download used to let
  focus escape to the sidebar; it now moves to the nearest surviving download row (same slot, else the
  last row).
- **Home & Customize category lists trap vertical focus.** A held D-pad Up/Down that outran the lazy
  composition could escape the list to the sidebar; both now use `trapVerticalFocusExit` like every
  other browse list.
- **Category rail: abbreviation badges removed.** Next to each category name the rail showed a short
  2–3 letter code derived from the name (e.g. `UPR` beside "UK PRIME RAW") in a fixed-width column.
  This was left over from the old compact-pill rail and read as clutter on what was otherwise a
  full-label column. Category folders now show just the name; **Favorites** and **History** keep their
  star / clock icons inline before the name. The content-pane subtitle also shows the full category
  name instead of the abbreviation. (#75)
- **Subtitle search overlay: sign-in moved to Settings only, local-file button removed.** Opening
  **Search OpenSubtitles** while signed out (or after the session expired) used to offer three buttons
  — add account / select local file / skip — plus an in-place username+password sign-in popup. Sign-in
  now lives only in **Settings → Video Player → Subtitles → OpenSubtitles** (which already had it):
  the signed-out overlay shows a clear note pointing there with just a **Close** button, and the
  in-overlay sign-in and sign-in-failed dialogs are gone. The overlay's **Select local file** button
  was removed too — the dedicated **Select local subtitle file** row in the Subtitles menu (right
  below **Search OpenSubtitles**) already covers local subs.
- **Catch-up dialog: D-pad focus no longer escapes the popup.** Opening the catch-up programme picker
  (long-press a channel → Catch-up) left the dialog without a hard focus boundary, so a stray D-pad
  press — or the Live screen's own focus restoration — could drop focus onto the channel grid behind
  the scrim. The dialog now wraps in `Popup(focusable = true)` and traps focus exit, matching the other
  scrim dialogs. It also picks up the standard popup-menu styling: the **Lora** serif font at 75% scale
  and a denser box, so it reads like the EPG-match and other popups.
- **Category rail highlight: sharper corners.** The focused / selected box on the category rail (used
  by Live TV, Series and Movies — one shared component) had a soft `14dp` corner radius that read as
  nearly pill-like; it's now `8dp`, crisper and closer to the channel-list item style next to it.

## v4.1.3 — 2026-07-19

### 💬 External subtitles — OpenSubtitles search & local subtitle files

- **Search OpenSubtitles from the player.** For any movie or series episode, open **Subtitles →
  ADD SUBTITLES → Search OpenSubtitles**. The search is pre-filled from the item's identity (TMDB id
  when available, else title/year and season/episode), shows language, release name, Trusted/SDH/AI
  tags and download counts, and supports **Edit search** and **All languages**. Picking a result
  downloads the subtitle, attaches it live without interrupting playback, and remembers it for that
  profile and title. Never automatic: OwnTV only searches or downloads when you ask.
- **OpenSubtitles account, per profile.** Sign in from **Settings → Video Player → Subtitles →
  OpenSubtitles** (free account at opensubtitles.com), with an optional **Stay signed in**.
  Each OwnTV profile connects its own account; the allowance display shows the provider's own
  remaining-downloads and reset values. Credentials sit in Android-Keystore-sealed storage, are wiped
  on sign-out/profile deletion, and are never logged. They enter a backup only when you set a backup
  password — encrypted per profile, and omitted entirely from a password-less backup. If you pick Search OpenSubtitles
  while signed out, a friendly dialog lets you **add the account right there** (or jump to a local
  file instead).
- **Local subtitle files — no account, no internet.** **ADD SUBTITLES → Select local subtitle file**
  opens OwnTV's TV-safe file browser for `.srt` / `.ass` / `.ssa` / `.vtt` / `.webvtt` files (USB or
  internal storage). Non-UTF-8 files (Windows-1256 Arabic, Windows-1252, ISO-8859…) are detected and
  converted automatically so they render correctly, and OwnTV keeps a managed copy so the subtitle
  keeps working after the USB stick is gone.
- **Subtitle timing.** **Subtitles → ADJUST → Subtitle timing** nudges the active subtitle in
  ±0.1 s / ±0.5 s steps while the video keeps playing, with plain-language direction (earlier/later).
  The offset is remembered per profile, per title, **per exact subtitle release** — a WEB-DL sub and
  a Blu-ray sub keep separate offsets, and switching subs never inherits another's offset.
- **Smart caching, quota-friendly.** Downloads are cached on the device and deduped: re-picking a
  subtitle any profile already downloaded re-uses the file and **spends no download quota**. On
  replay, a title's previously downloaded subtitles are re-listed in the Subtitles menu ready to pick.
  Everything works across both playback engines, including the in-player MPV/EXO toggle, and for
  **OwnTV Downloads** — offline, with the OpenSubtitles moviehash silently sharpening online matches
  for downloaded files.
- **Manage & delete.** **Settings → OpenSubtitles → Delete subtitles** lists every downloaded
  subtitle by Movies/Series with per-item and bulk delete; long-press a movie or episode for
  **Delete OpenSub subtitles**. Deletion is per profile — a subtitle another profile also downloaded
  stays available for them.
- *Privacy:* the OpenSubtitles API key lives only in an OwnTV-run Cloudflare Worker (like the TMDB
  proxy) — never in the app; only subtitle-search data is ever sent (no stream URLs or IPTV
  credentials). This product uses the OpenSubtitles API but is not endorsed or certified by
  OpenSubtitles.

### 👥 Profile-based backups (merge restore, PIN-protected)

- **Backup export now starts with a profile picker.** Every backup is per-profile: choose which
  profiles ride in the file (none pre-ticked — you decide), then pick the data sections as before
  (the old "Profiles & sources" section is now just "Sources"). Only the selected profiles' data —
  favorites, history, resume positions, customizations, startup modes, Customize PINs — and only the
  sources they actually use are written.
- **Locked profiles need their PIN.** Ticking a PIN-locked profile that isn't the one you're signed
  into prompts for that profile's PIN; a wrong PIN shows "PIN incorrect" and the profile stays out of
  the backup. Your current profile never re-asks (you already passed its gate). Profile PINs
  themselves are stored in the file only as salted hashes, never as the actual PIN.
- **Restore now MERGES — it never deletes existing profiles or sources.** Profiles are matched by
  name: a profile already on the device is updated from the backup, and profiles only in the backup
  are added — your other profiles are left completely untouched. Sources match by address, so a
  shared playlist isn't duplicated. (Previously a restore replaced everything.)
- **Profile names are now unique.** Creating or renaming a profile to a name that already exists is
  blocked with "This name is already taken" — names are how restore recognises the same profile.
- **OpenSubtitles logins now ride in encrypted backups.** With a backup password, each ticked
  profile's OpenSubtitles sign-in (username + password/token) is included, sealed with your passphrase,
  and restored to the matching profile on the target device. Without a backup password it's omitted,
  exactly like source passwords, the Stalker MAC and the proxy/TMDB secrets.

### 📱 Add a playlist from your phone (Remote setup)

- **"Add source" now starts with a Remote / Manual choice.** Pick **Manual** to type Xtream / M3U /
  Stalker details with the remote as before, or **Remote** to fill everything on your phone. Both the
  first-run setup wizard and Settings → Manage sources offer the choice.
- **Remote setup shows a QR code, a URL, and a one-time PIN.** Open the server on the TV, then on a
  phone or laptop on the same Wi-Fi scan the QR (or type the URL). The page first asks for the 6-digit
  PIN shown on the TV, then shows an **OwnTV-styled form** with Xtream / M3U / Stalker tabs. Fill it,
  tap **Send to TV**, and the details appear in the Add Source screen on the TV — you press **Start
  Import** with the remote (the phone never starts the import itself).
- **Secure by design.** The QR carries only the URL, never the PIN; every submission must carry the
  PIN or it's rejected (401). A fresh PIN is generated each time the server opens, passwords/MAC are
  never logged, and the listener stops automatically when you leave the screen.
- *Core idea from **@zarga03** (PR #66)* — reimplemented and hardened for OwnTV: added M3U support and
  the phone-side type picker, the one-time PIN gate, the QR onboarding, an app-matching web form, and
  "fill the form, you press Start Import" semantics.

### ⏱️ EPG sync: Run in background (onboarding)

- **The "Sync the TV guide now?" step during first-run setup can now Run in background.** Once the
  guide starts downloading you no longer have to wait on the sync screen — press **Run in background**
  to enter the app while the guide keeps downloading (matching the playlist import's own background
  option).

### 🗂️ Categories grouped by provider + new-category control

- **Multi-provider category lists no longer interleave.** When you view **All playlists** (or a profile
  with two or more linked sources), the category lists across Live/Movies/Series browse, Customize, EPG,
  Search and Home now stay **grouped by provider** (in the order you added them) instead of mixing two
  providers' categories together. A single selected playlist looks exactly as before.
- **Provider name on Customize rows.** When more than one source is in scope, each Customize category row
  shows which provider it belongs to, so bulk-hiding across providers is easier to follow.
- **"New category behavior" (Show / Hide) — per profile.** A new setting at the top of **Settings →
  Customize** decides what happens to a category the provider adds on a later re-sync: **Show** (default,
  the old behavior) or **Hide** it automatically. Useful if you keep only a few categories visible and
  don't want new ones appearing. It rides in the Customize backup/restore like other per-profile settings.
- **Re-sync tells you the category churn.** The sync-complete message now shows "N categories added,
  M removed" when a re-sync changes them — so you still know new categories exist even when you hide them
  by default. (Never shown on a source's first sync, where everything is new.)
- *Community PR #70 by **@pt5pnzghm6-sys** (related to issue #60).*

### 🎨 Smaller tweaks

- **Default UI zoom is now 90%** (was 100%) so more of each screen fits on smaller TVs out of the box;
  adjustable any time in Settings.
- **The Player settings "OpenSubtitles account" row is now just "OpenSubtitles"** — it holds sign-in
  *and* the downloaded-subtitle manager, so the shorter name fits what's inside.

### 📺 Live TV — current programme in the channel list

- **Now-playing subtitle on every channel row.** The Live TV channel list now shows the programme
  currently airing under each channel name (a small second line), sourced from your guide data. The
  channel-list column is also **a little wider** so the longer rows breathe, and the preview pane a
  little narrower to match. Channels without guide data look exactly as before — single line.
- **Same in the in-player channel overlay.** Pressing **Left** (while the player controls are hidden)
  to open the side channel list now shows the same current-programme subtitle under each channel, so
  you can see what's on without leaving fullscreen.
- *Detail:* the list uses the stored bulk guide only (one batched query, refreshed every 60 s); the
  focused-channel preview pane keeps its full provider short-EPG fallback. No per-row network calls.

### 🔄 EPG / Guide sync status pill

- **Updating the guide now shows the status pill too.** The small semi-transparent pill that already
  reports background playlist syncs now also reflects **EPG/Guide downloads** — manual resyncs from
  Settings → EPG Sources and the automatic startup/staleness refreshes. It reads "Updating guide ·
  *source* · N programmes" and disappears when the sync finishes. Catalog syncs keep priority; if both
  run at once the pill notes "· EPG too".

### 🎯 Smarter EPG matching

- **Match EPG picker suggests related channels first.** Long-press a channel → **Match EPG** (Live TV
  or Guide) no longer opens on a plain A-Z list: guide channels **similar to the channel's name float
  to the top**, best match first (e.g. opening it on "MTV FR" shows the MTV entries immediately). The
  ranking also applies while you type a search. The picker now scans the *whole* guide-channel set
  instead of only the first 300 alphabetical entries.
- **The name matcher itself is more robust** (used by the picker ranking, the Guide's **Auto-match
  all**, and single-channel auto-match):
  - Spelled-out **country names** match their codes — "MTV France" ↔ "FR| MTV" is now an exact match
    (guarded so channels like **France 24 / France 2** keep their name).
  - **Number words** — "BBC One" ↔ "BBC 1" now match.
  - **Word-order tolerance** — "France MTV" ↔ "MTV France" score highly via token overlap.
  - **Channel-number guard** — "Sky Sports 2" can no longer match "Sky Sports 3" (never even offered),
    and "MTV" vs "MTV 2" is capped below auto-apply so it goes to review instead of silently applying.
- **Dialog ergonomics on TV remotes.** In the Match EPG picker and the Auto-match **review** popup,
  the action buttons (**Close / Clear match**, **Accept all / Skip all / Done**) moved to a **right-hand
  column** — press **Right** from any list row to reach them, no more scrolling to the bottom of a long
  list. Focus is also **contained inside the popup** now (a stray D-pad press can no longer drop focus
  onto the screen behind it).

### 🎬 Better TMDB title cleaning

- The movie/series **title normalizer** (what builds the TMDB search query) strips more provider noise
  while keeping real titles intact: audio/language tags (**VOSTFR, VF, SUBBED/DUBBED, DUBLADO/LEGENDADO,
  TRUEFRENCH, LAT**), release markers (**HDCAM, CAMRIP, HDTC, HDLight, 10bit, 60fps, AAC/AC3/DTS, 5.1/7.1**),
  trailing **season/episode tails** on series names ("Show S05", "Loki Season 2", "Dark Staffel 1",
  "Temporada 3", "S02E04"), and trailing uppercase language codes ("Movie FR"). Guarded so titles like
  *Ocean's 8*, *Se7en*, *Area 51* and *Sub Rosa* are never touched.

### 🗂️ Storage access that works on more TVs

- **One-click "Grant full storage access."** The file/folder picker (download folder, local M3U
  import, backup) now has a single grant action that opens **OwnTV's own app-settings page**, where
  you enable **Allow management of all files** yourself. This fixes OEM TVs (e.g. TCL Android 12)
  whose system "All files access" screen is hijacked or missing, which previously left no working
  way to grant storage from inside the app. On Android 10 and below the standard permission dialog
  appears instead (it grants full access there). A media-only grant is no longer treated as storage
  access — it hid `.m3u`/backup files behind scoped storage.
- **The picker is a real dialog window now.** D-pad focus physically can't escape onto the screen
  behind it anymore, and access is re-checked when you come back from system settings, so the grant
  row disappears immediately after granting.

### 🎨 Compact popup menus in a new serif font

- **Popup menus are ~40% smaller and render in Lora** (a free, open-licensed serif; only popups —
  the rest of the app keeps its sans-serif): the player's **subtitle/audio/track menus**, Settings
  **option pickers** and **+/− steppers**, the **playlist switcher**, and the **storage/file
  picker** (now 300 dp with restacked footer buttons).
- **Match EPG picker** (Live TV & Guide long-press) shrank 40%, the Guide's **Review EPG matches**
  popup 20%, and the **Customize screen's PIN dialogs** got a compact variant — all in the Lora
  serif. The profile "Who's watching?" PIN dialog is unchanged.

### 🔀 CH+- key paging for browse panels

- **Page the category & item lists with the remote's CH+ / CH− keys.** In Live TV, Movies and Series,
  the CH+ / CH− keys now page whichever panel currently has focus — the category rail or the item
  list/grid. Short press jumps a configurable number of items (clamped at the ends, so a short list
  reaches the end in one press for free); long-press CH+ jumps straight to the **first** item and
  long-press CH− to the **last**. A lifesaver for big libraries (e.g. 50k live channels, 500+
  categories) where scrolling top-to-bottom was impractical.
- **Per-direction skip counts, typed or stepped.** New **Settings → Content → CH+- Key Paging**: a
  master on/off, plus a separate skip count for CH+ and CH− that you can type directly or nudge with
  − / +. The dialog warns (advisory, never blocking) when a count exceeds 50, since large skips
  overshoot short lists and may feel jumpy on low-end TVs; a hard cap of 1000 guards against typos.
- **Apply to the focused panel only.** The keys never fire when focus is elsewhere (e.g. the top bar),
  and a master toggle lets users whose remotes map CH keys to something else opt out entirely. The
  category rail moves focus only — selection still happens on OK, so a stray CH press never reloads
  a category's channels. All jumps use instant `scrollToItem` (no animation) to avoid jank on slow
  TVs over big distances. Defaults: enabled, skip 10 each direction.
- **Long-press is disabled on the "All" list.** On the built-in All channels / All movies / All series
  list a long-press jump to the very last item (e.g. the 170,000th movie) is pointless and janks, so
  long-press does nothing there — short-press skipping still works normally. Real categories and
  folders keep long-press jump-to-first/last. (This checks the built-in All key, not the name, so a
  provider category literally called "All Hindi" is unaffected.)
- **Also pages the Customize category list.** The same CH+ / CH− paging now works in **Settings →
  Customize Categories & Items**, where the list is just the raw provider folders (no "All"). Handy
  with big provider category lists; long-press jumps to the first/last folder. The keys move focus
  within the list only — they can never push focus out of it — and the CH+- Key Paging settings screen
  notes this coverage.

### 🐛 Fixes

- **"Grant full storage access" no longer dead-ends on OEM TVs.** On TCL Android 12 the old grant
  button opened the OEM "Permission Shield" screen, which has no storage entry at all; the picker
  also showed a "grant" option that could only ever yield a useless media-only permission. Both
  replaced by the app-settings route above.
- **Storage picker focus could escape the popup.** Moving focus (especially after returning from
  the permission screen) could land on the screen behind the picker; it's now hosted in its own
  window so that can't happen.
- **Deleting an EPG source now shows a "Deleting…" status and can't leave orphaned guide data.**
  Removing an EPG source with a large guide (100k+ programmes) took a while to clear from the
  database, but the row vanished instantly with no indication, and leaving the screen mid-delete
  could orphan those programmes with no source left to clean them up. The row now stays with a
  **Deleting…** badge (its actions hidden) until the delete finishes, the guide rows are removed
  **before** the source leaves the list, and the delete completes even if you navigate away.
- **EPG match now falls back to a network re-sync when the cache has no data for it.** After
  matching a channel, OwnTV fills its programmes from the cached XMLTV without a network call — but
  that step reported success even when the cache held none of the matched channel's programmes, so
  the network fallback never ran. It now re-syncs (with the just-saved match included in the sync
  filter) whenever the cache yields nothing for the matched channel. And when a matched channel
  genuinely has no current/upcoming programmes in the feed, the Guide now says so ("Matched — but
  this guide channel has no current programmes in the EPG feed yet") instead of leaving a silently
  empty row.
- **Match EPG from Live TV now takes effect immediately.** Matching a channel's EPG from the Live TV
  list used to leave the details/preview pane without guide data until an app restart (the row's
  now-playing line updated, the pane didn't). The match now also tops up the matched guide channel's
  programmes from the cached EPG and refreshes the pane right away.
- **Focus returns to the channel after Match EPG.** Closing the Match EPG dialog (pick, clear or
  back) lands D-pad focus back on the channel row it was opened for, instead of falling to the nav panel.
- **Customize screen showed categories from every playlist.** When you'd picked one playlist (e.g.
  playlist A) via the top-bar switcher, **Settings → Customize Categories & Items** still listed
  categories from *all* playlists. It now respects the selected playlist — same as the Live TV / Movies
  / Series rails. ("All playlists" still shows the merged set.) Existing reorders/hides are preserved.
- **Customize screen renamed** to **"Customize Categories & Items"** (was "Customize & Hidden Items")
  for clarity — it's where you hide/unhide items, rename, and reorder categories.
- **Live TV "Now" no longer shows a future programme** (#68). For channels without a configured guide,
  OwnTV falls back to the provider's short-EPG. When that data had a gap around the current moment, the
  "Now" slot could pick the next upcoming programme and mislabel it as live. It now correctly leaves
  "Now" blank on a genuine gap; the upcoming programme still shows under "Next". EPG display only — no
  playback impact.
- **CH+- skip dialog alignment.** In the CH+- Key Paging skip-count popup, the − / + buttons no longer
  sit above the number field — they now line up with it (the field's label was pushing them up).

### 🔒 Security (community PR #65)

- **Customize PIN no longer stored in plaintext** (community PR #65 by @aravindtri). The screen lock
  PIN is now stored as a salted SHA-256 hash, matching how profile PINs are already handled. Existing
  installs and imported backups with old plaintext PINs still verify correctly and migrate on use.
- **Hero preview URLs are redacted in error logs.** A failed Home hero-preview playback no longer logs
  the raw stream URL (which can carry credentials); it's scrubbed via the existing `redactUrl` helper.

### 📊 Player diagnostics — measured fps/bitrate & top-bar bitrate chip (community PR #67)

- **ExoPlayer now shows real fps, bitrate and dropped-frame stats** (community PR #67 by
  @pt5pnzghm6-sys). Raw MPEG-TS streams (most Xtream live TV) don't declare `frameRate` or `bitrate`,
  so ExoPlayer's **Stream Info overlay** and the preview's top-left chips used to be blank where mpv
  showed live values. This measures them on the fly — **fps** from decoder-rendered frame timing
  (snapped to a standard rate so a brief stall doesn't give a stray reading), **bitrate** from actual
  network bytes, and **dropped frames since the start of playback** — all with negligible CPU impact,
  and only computed while the info overlay is open. It also fixes a couple of mpv↔ExoPlayer handoff
  bugs that were blocking correct resolution/fps display for VOD on Exo.
- **Bitrate now appears in the player top-bar chips** for all playback — Live TV (preview & full),
  movies and series, on both engines. The chip uses the stream's declared bitrate (free to read), so
  it adds no measurement overhead; raw live MPEG-TS streams that don't declare one stay blank in the
  chip (the overlay still shows the live measurement when opened).
- **New "Measured stream stats" toggle** (**Settings → Video Player → Diagnostics**, on by default) —
  a one-switch escape hatch. On, the Stream Info overlay measures fps/bitrate/dropped frames as above.
  Off, no live measuring runs at all (declared values only), for the rare low-end TV where the
  measuring is ever suspected of causing stutter. It only gates the diagnostic numbers — never the
  actual video pipeline or the mpv↔ExoPlayer handoff.

### 📦 Packaging

- **Smaller downloads — split ABI builds.** Releases now ship a single **arm APK** (`OwnTV.apk` /
  `OwnTV-vX.X.X.apk`, `arm64-v8a` + `armeabi-v7a` — for all real Fire TV / Android TV devices, and what
  the Downloader code fetches) plus a separate **`OwnTV-x86_64-vX.X.X.apk`** (for emulators / rare Intel
  boxes). The main download roughly **halves in size** (~104 MB → ~49 MB), which fixes the "parse error
  on install" reports caused by truncated large downloads on Fire TV's Downloader app. `x86` (32-bit
  Intel) is dropped — even emulators use x86_64.
- **In-app updater picks the APK matching your device.** With releases now carrying one APK per ABI,
  the updater selects the asset matching the device's ABI (arm on real TVs, x86_64 on emulators)
  instead of blindly taking the first APK — so an arm TV can never download the emulator build, and
  in-app updates now also work on an x86_64 emulator. Older single-APK releases still update fine.

## v4.1.2 — 2026-07-14

### ⚡ Background catalog sync

- **"Run in background" during the first import** — the setup wizard's sync screen (and any first
  playlist import) now has a **Run in background** button: enter the app immediately and start
  watching while the catalog keeps loading. Works for M3U, Xtream and Stalker alike. If a
  backgrounded import later fails, the playlist is **kept** (with its credentials) so you can
  re-sync it from Settings → Playlists — previously a failed add was silently removed.
- **Stalker portals sync live TV first, movies & series in the background — automatically.**
  Stalker has no bulk VOD endpoint (its catalog is paged ~14 items per request), so a big portal's
  movies/series crawl took minutes. Adding a portal now imports live channels in seconds and hands
  the movies/series crawl to a background worker that survives app restarts and retries transient
  failures. No toggles to understand — it's the default for every Stalker add.
- **Adaptive portal speed** — the Stalker VOD/series crawl now learns how many parallel requests a
  portal tolerates (ramping up on success, backing off instantly on rate-limit/overload errors)
  instead of using a fixed pool. Tolerant portals sync significantly faster; strict portals stop
  erroring pages.
- **Faster Stalker re-syncs (delta check)** — on a refresh, a category whose item count is
  unchanged on the portal is skipped entirely instead of re-walking all its pages. On a stable
  catalog this cuts a re-sync from thousands of requests to roughly one per category.
- **Background-sync status pill** — a small semi-transparent pill at the bottom of the screen shows
  "Syncing *playlist* · N items" whenever any catalog sync runs in the background (a backgrounded
  first import, the movies/series remainder, auto refresh). It never takes D-pad focus and hides
  during fullscreen playback.
- **Clearer "All set!" message for staged imports** — when movies/series are still loading in the
  background, the import-success screen now says so explicitly (and points at the status pill), so
  a fresh Stalker add no longer looks like it "only synced live TV".

### 📺 Live player guide card

- **Before / Now playing / Next on the player controls** — bringing up the controls on a live
  channel now shows a guide card on the right edge: the programme that just ended, what's on now
  (with times and a short description), and what's next. Uses your XMLTV guide first, falling back
  to the provider's short-EPG API (Xtream and Stalker); channels without guide data simply show no
  card. Informational only — it never takes D-pad focus.

### 🐛 Fixes

- **Guide programme popup: last button was cut off** — with catch-up channels the four actions
  (Watch from start / Watch channel / Favourite / Close) overflowed the dialog edge. The buttons
  now wrap to a second row when they don't fit.
- **Guide programme popup: long-press acted in one go** — opening a programme with a held OK could
  instantly trigger the focused button. The dialog now swallows OK until the key is released once,
  so a long-press only opens it and the next press selects.
- **Settings → Home screen: focus didn't enter the list** — opening Home screen settings left
  D-pad focus on the sidebar instead of the first row (the only sub-settings screen missing the
  initial focus request; all others were audited and are correct).

## v4.1.1 — 2026-07-14

### 📡 Stalker / Ministra portal support

- **New source type: Stalker (MAC portal)** — add a portal with just a Portal URL + MAC address
  (no username/password). Third source type alongside M3U and Xtream, with the same Default-playlist
  toggle, playlist switcher, per-source Auto refresh, Backup & Restore (the MAC is encrypted like a
  password), and TMDB enrichment. Available everywhere sources are added — including the
  **first-run setup wizard**, so a portal + MAC is enough to onboard.
- **Test connection before saving** (handshake + profile check), with clear errors for a bad MAC,
  an unreachable portal, or clock drift ("check the TV's date & time"). When the portal reports a
  subscription expiry, the result shows it.
- **MAG User-Agent presets** (MAG250/254/270/420) in the add-source form, plus the per-source
  User-Agent override, so a portal's UA whitelist change never needs an app update.
- **Live TV** from a Stalker portal plays on both engines (ExoPlayer preview/fullscreen + mpv
  compatibility mode), with embedded subtitles, zap, and the engine toggle. Stream URLs are minted
  at play time and **silently re-resolved if they expire mid-session** (Stalker links are
  short-lived) — a long live watch survives a ~2–4 h token reset.
- **Movies & Series** sync and play: per-category catalog import with a shared concurrency budget
  (movies + series import simultaneously, with a bulk single-dump fast path where the portal
  supports it), lazy per-season episode loading, and next/previous/autoplay across a season — each
  episode mints its own fresh stream link. External player playback works too.
- **Downloads** work for Stalker movies and episodes like any other source: the link is resolved
  when the download starts, and if it expires mid-download the app fetches a fresh one and resumes
  from where it stopped (HTTP Range).
- **EPG & catch-up**: now/next comes from the portal's short-EPG API; the full guide uses an XMLTV
  feed (advertised by the portal, or pasted in Settings → EPG). Channels with a provider archive
  get the existing catch-up features — Guide "Watch from start", the Live TV catch-up picker, and
  live rewind.
- **Fast, resilient sync**: bulk `get_all_channels` fast path (thousands of channels in seconds)
  with paged fallback, and transient portal errors (HTTP 429/5xx) retried with backoff so a hiccup
  never drops a category. Re-syncs and auto refreshes are non-destructive (favorites, history and
  progress survive).

### 🧭 Sidebar Menu Customization

- **Show only the icons that match your playlist.** A playlist that only has VOD no longer clutters
  the side menu with Live TV / Guide; a Live-only playlist hides Movies / Series / Downloads. Open
  **Settings → Sidebar Menu Customization** and switch **Behavior** between **Static** (manually
  hide any of the six icons — Home, Live TV, Movies, Series, Downloads, Guide) and **Dynamic** (the
  icons auto-adapt to what the active playlist actually contains).
- **Dynamic** reuses the existing per-source content counts (Home & Settings always show; Live/Guide
  show when there are channels; Movies/Series when their tables have rows; Downloads when Movies or
  Series exist — Live has no download). Counts re-evaluate on their own after every sync, so the
  rail updates the moment content arrives. With the top-bar picker on "All playlists", counts are
  unioned across the profile's sources.
- **Static** is the default (all icons visible) — existing users see no change until they opt in.
  Settings is always pinned at the bottom and can never be hidden; hiding every browse icon lands
  the app on Settings.
- **Focus & fallback:** if the section you're viewing becomes hidden, the app jumps to the first
  still-visible browse item (or Settings if all are hidden); opening the screen lands focus on its
  first row.
- The mode and hidden-icon set are part of **Backup & Restore**.

### ✨ Improvements

- **Favourites from the TV Guide.** Add or remove a channel from Favourites without leaving the
  Guide: **long-press a channel label** for the channel menu (favourite toggle + the existing EPG
  match options), or use the **Favourite** button in a programme's details dialog. Stars show up
  immediately in Live TV, Search, and the Home rail, and the Guide's "Favorites" sort refreshes in
  place.

- **Subscription expiry in Manage sources.** Each Xtream and Stalker playlist row now shows an
  "Expires …" note with the account's end date (read from the provider when the screen opens).
  M3U playlists have no account, so they don't show one.

- **Deleting a playlist now shows its progress.** Removing a source with a huge catalog
  (hundreds of thousands of channels/movies/episodes) can take a while — the source row in
  **Settings → Manage sources** now shows a "DELETING…" badge with a spinner until the removal
  finishes, and the row's Edit/Re-sync/Delete buttons are hidden meanwhile so it can't be
  touched mid-delete. The removal also now always runs to completion even if you leave the
  Settings screen while it's working.

### 🐛 Fixes

- **Updating from 4.0.x/4.1.0 could crash the app at launch (database self-heal).** If a large
  playlist or EPG import was ever interrupted mid-sync (TV standby, low memory, force-stop), the
  import speed-up that temporarily drops SQLite indexes could leave some of them missing. That was
  invisible in daily use, but the next app update re-validates the whole database schema — so the
  update crashed the app on every launch until the previous version was reinstalled. The database
  now **self-heals**: the final migration and every database open recreate any missing index or
  search (FTS) table (idempotent and effectively instant on healthy installs), every index-restore
  pass shares one canonical index list so a gap can never persist again, and the post-import index
  rebuild now covers the rating-sort indexes it previously missed. Verified against every public
  upgrade path (v1.0.0 → current) — updating preserves all playlists, favorites, history and
  progress; no reinstall needed.
- **Hidden categories are now respected in the TV Guide.** Categories hidden via Customize no
  longer appear in the Guide's "Category" dropdown, and their channels stay out of the guide grid
  (matching Live TV). The dropdown also now shows your category **renames** and keeps manually
  **reordered** categories pinned first, like the Live TV rail. If the category you were filtering
  by gets hidden, the Guide falls back to "All" instead of showing an empty grid.
- **Download retry & failure polish.** Retrying a download now stops the old attempt before
  starting fresh (previously the two could race and corrupt the restart); a failed download keeps
  its real partial byte count instead of showing 0; and a resume that finds the file already fully
  downloaded is marked completed instead of failing.

### 🔧 Under the hood

- **Sync engine split into per-source-type modules.** The single large `SyncManager` was split
  into a thin dispatcher plus `XtreamSyncer`, `M3uSyncer` and a shared `SyncSupport` toolbox
  (chunked inserts, stable upserts, category refresh, pruning) — groundwork for the upcoming
  Stalker portal source type. No behavior change; import/sync logic and logging are identical.
- **Migration tests modernized.** The database migration test suite now runs every chain to the
  current schema version (it had stopped at v9), and gains a regression test that deliberately
  drops indexes from a v12 database and asserts the new self-heal repairs it during the upgrade.

## v4.1.0 — 2026-07-11

### ✨ New features

- **Playback error log in Settings.** The last ~10 playback failures are now kept on the device —
  each with its plain-English reason, the stream's codec/resolution spec, the raw engine error, the
  engine (mpv/ExoPlayer), Live/VOD, and your device model/Android version. Open **Settings →
  Playback → Playback error log** to read (or clear) them, so you can report exactly what happened
  even after dismissing the error screen or restarting the app — no adb/logcat needed.
- **Custom TMDB names are now in Backup & Restore.** Titles/years you hand-corrected via long-press →
  **Custom TMDB name** (for providers with weird item names) now ride in the backup's Customizations
  section and are merged back on restore — any stale cached match for a restored key is dropped so the
  corrected name re-fetches. Two more backup upgrades ride along: your own **TMDB API key** is now
  included when (and only when) the backup is password-encrypted (same policy as source/proxy
  passwords), and **recent searches** are backed up with settings. Older backup files still restore
  fine; older app versions simply ignore the new blocks.
- **Wider interface zoom range.** **Settings → Interface zoom** now goes from **50% to 150%**
  (previously 65%–140%), for tighter grids on big screens or larger UI on small/far ones. The
  existing low-memory warning below 85% still applies.

### ⚡ Performance & reliability

- **Smaller app, faster cold start (R8).** Release builds are now shrunk and optimized by R8 —
  dead code is stripped and the remaining code is optimized, so there's less to load on
  low-end TV boxes. Baseline profiles bundled by the UI/player libraries are now actually
  installed on sideloaded installs (via ProfileInstaller), pre-compiling the hot startup and
  scrolling paths instead of leaving them to the JIT on first run.
- **Faster playlist import on huge playlists.** The M3U parser now extracts all `#EXTINF` attributes
  in a single scan of each line (previously ~10 separate searches per channel), and the detailed
  per-item timing instrumentation in both the M3U and Xtream parsers is now off unless explicitly
  enabled for debugging (`setprop log.tag.M3uParser DEBUG` / `log.tag.XtreamClient DEBUG`) — removing
  millions of clock syscalls from a 100k+ item sync. The single-scan parser also fixes a subtle
  mis-parse where a key could match inside a longer key (e.g. `type` inside `tvg-type="…"`).
- **Scheduled syncs now retry after network blips.** A playlist or EPG auto-refresh that failed on a
  transient error (offline, timeout, connection reset, server 5xx) previously gave up until the next
  scheduled window, leaving content stale. Both sync workers now ask WorkManager to retry with backoff
  (up to 3 attempts); permanent errors (bad credentials/URL, malformed data) still fail immediately.
  Xtream category-list fetches also get up to 3 HTTP attempts, and server 5xx/429 responses are retried
  safely (only when no data was consumed yet).
- **Player stability hardening.** The stream-info chips (fps / audio layout) no longer read libmpv
  properties on the UI thread — on a stalling stream those reads can block for seconds and caused
  potential freezes/ANRs. Queued freeze-frame callbacks are now cleared when the player is released, so
  they can never fire against a destroyed surface.
- **More accurate playback error diagnosis.** The plain-English error mapper no longer mis-labels
  errors whose stream URL merely *contains* digits like `509`/`403` as HTTP provider errors, and a
  spurious "out of memory" match on any `-12` substring is fixed. The background codec-error log tail
  now restarts itself if the system kills it, so error details keep working for the whole session.
- **Much faster global search on huge catalogs.** Search-as-you-type now uses the full-text index
  instead of scanning every movie/series/channel name per keystroke — on a 170k-movie catalog each
  keystroke was a full table scan. Matching is now by word prefix ("harry pot" finds
  "Harry Potter…"); folder-scoped search keeps the old substring behaviour.
- **Big folders page faster.** Folders where you never used **Move** (manual reorder) now use the
  plain indexed query instead of the reorder-aware join that re-sorted the whole folder on every
  page turn. Folders with manual positions behave exactly as before.
- **Smoother UI during large syncs.** The live item-count badges (Live/Movies/Series and the EPG
  programme count) now refresh at most once per second during a bulk import instead of re-counting
  the whole table after every committed batch.
- **Posters and channel logos are cached on disk.** Artwork now survives app restarts (capped at
  250 MB) instead of re-downloading every session, loads offline once seen, and opaque poster
  bitmaps use half the memory.
- **Faster, safer backup restore.** Restoring thousands of favorites/history/resume records used to
  run one database transaction per record; they're now batched (500 per transaction). The
  profiles-and-sources restore is atomic: a crash mid-restore can no longer leave a half-restored
  database.
- **Faster first launch when upgrading from v3.2.0 or older.** The one-time database migration no
  longer de-duplicates the (huge) cached TV guide row-by-row — it clears the rebuildable guide cache
  instead, so the first launch after a big version jump is instant. The guide re-downloads on your
  next EPG sync. (Upgrades from any 4.x version are unaffected.)
- **Less UI work while browsing.** The most-passed-around UI models (channels, movies, series,
  home-screen state, EPG now/next, search results, weather, details panes) are now marked immutable
  for Compose, so screens can skip re-rendering unchanged parts instead of redrawing whole subtrees
  on every state tick.
- **TMDB caches no longer grow forever.** Metadata cached for items you haven't opened in 90 days is
  cleaned up after each playlist sync and simply re-fetches if you come back to them.

### 🐛 Fixes

- **Dialogs no longer get cut off on small screens.** On low-resolution/overscanned TVs, tall popup
  dialogs (New profile, context menus, Settings dialogs, catch-up & EPG-match pickers, the setup
  wizard, and more) could extend past the screen with no way to reach the lower buttons — profile
  creation could not be completed at all. Every popup is now scrollable (D-pad focus scrolls
  off-screen controls into view) and list pickers cap their height to the screen.
- **Grids keep your place through background refreshes.** The Movies/Series/Live lists and grids now
  track items by identity instead of position, so a background re-sync or list update no longer
  scrambles D-pad focus or recomposes every visible poster.

- **A–Z sorting now applies to categories too.** The sort chip in Live TV, Movies and Series only
  reordered the items inside a folder — the category rail itself always stayed in provider order.
  Switching the chip to **A–Z** now also sorts the category folders alphabetically (by their displayed
  name, so renamed categories sort under their custom name), and the TV Guide's category picker follows
  the Live TV setting the same way. Categories you manually reordered in **Settings → Customize**
  stay pinned at the top in your custom order in every mode; the rest sort below them. **Provider**
  (and **Rating**) modes keep the playlist order exactly as before. The fixed rail entries (All,
  Favourites, Recent…) never move.

### 🔧 Under the hood

- **CI dev builds are now release builds.** Every push now produces a release-signed, R8-shrunk
  `OwnTV-dev-<sha>.apk` artifact (previously debug), versioned `99.99.99` so it installs straight
  over any published release for testing. Publishing a GitHub Release still only happens on `v*`
  tags. Fork PRs (no signing secrets) still build debug.
- **Player timing constants named.** The ~15 bare `delay()` literals in the playback engine
  (decoder-release waits on mpv↔ExoPlayer handoffs, live-reconnect pause, surround/decode
  verification windows, retry beats) are now named companion constants documented in one place —
  no behavior change.
- **Dependency updates.** Koin 4.1.1 → 4.2.2, Coil 3.3.0 → 3.5.0, WorkManager 2.10.0 → 2.11.2.
  (core-ktx/lifecycle/Compose BOM stay put — their latest versions require compileSdk 37; OkHttp 5
  is deferred as its own change.)
- **Sync engine de-duplicated.** The three near-identical Xtream phase implementations
  (Live/Movies/Series: fresh-vs-stable upsert, per-category 512 fallback, prune) are now one generic
  phase parameterized per content type, so future fixes to the sync logic land once instead of three
  times. Behavior-identical; the category refresh also drops a redundant second database lookup.
- **Media3 (ExoPlayer) bumped 1.10.0 → 1.10.1.** ExoPlayer drives the image-subtitle (PGS/VOBSUB/DVB)
  handoff and the VOD mpv→Exo fallback, so this patch release lands fixes directly on those paths:
  a crash when recovering from decoder errors with renderer prewarming (the fallback triggers this),
  an `ArrayIndexOutOfBoundsException` during HLS stream fallback when the active track set is a subset
  of the manifest (#3161), and HLS init segments not carrying over across playlist updates when
  `#EXT-X-MAP` isn't repeated (#3105). It also stops needless MediaCodec resets at frame-rate changes on
  API < 30. `libmpv` is unchanged at `1.0.0` (still the latest).

## v4.0.3 — 2026-07-09

### ✨ New features

- **Settings: search and quick toggles.** A **"Search settings…"** field at the top of Settings filters
  the whole screen down to matching rows — results carry their group as a breadcrumb (e.g.
  `Playback › HDR`) and act exactly like the real row, so you jump straight to a setting without hunting
  through groups. Above it, a pinned row of one-press **quick toggles** (Live preview · Preview sound ·
  HDR · Auto-play · Check for update) flips the most-used options without opening a sub-menu. **Back**
  clears an active search before it leaves Settings.
- **Search: a launcher home, a detail pane and smarter Back.** The empty Search screen is now a launcher
  — a **"Jump to"** row (**Continue watching**, **Unwatched**, **Channels**) plus your **recent
  searches** as chips (with **Clear**). Results moved to a **list + detail** layout: focusing a result
  shows its poster, plot and rating in a side pane with a **primary action** button (Play / Watch live /
  Open series), and OK still plays it directly. **Back** clears the query (returning to the launcher)
  before it leaves Search. "Unwatched" and "Channels" are bounded to your favourites (and recent
  history) so they stay fast on large playlists.
- **Downloads: queue groups, a storage bar and clearer failures.** The Downloads list is now grouped
  into **Active · Waiting · Completed · Failed** sections with counts, a **storage bar** at the top shows
  free space (e.g. `12.4 GB free of 118 GB`), and a failed download now reads
  **"Download failed — couldn't reach the source. Tap Retry."** next to its one-press Retry.
- **Download status on the poster.** Start a download of a movie, a whole series, or a single episode and
  a compact **status strip** (Downloading / Queued / Paused / Failed, with a progress bar) now appears at
  the top of that item's poster panel — so you can see it's actually running without opening the
  Downloads screen. The strip only shows while something is in flight and disappears once complete.
- **Shell: a shared "Continue" chip.** The top bar now carries a compact **Continue** chip that resumes
  your most-recent item in one press — **Resume** a movie, **Next up** an episode, or your **Last
  channel** — labelled with the title and shown on every screen. It only takes focus from the navigation
  panel (like the search pill), so it never gets in the way while browsing, and hides when there's
  nothing to resume.
- **Series episode view: watched state, "Next up" and a "Hide watched" filter.** Episodes now show a ✓
  (and a dimmed title) once watched to ≥95%, and a thin progress bar when part-watched, so you can see
  exactly where you are in a season at a glance. Season chips show a `watched/total` count
  (e.g. `Season 2 · 8/18`). A **"Next up" card** at the top of the episode detail pane surfaces the
  episode to continue with — the one you're mid-way through, or the next one after the last finished —
  with a one-press **Play** (and a `Resume <time>` line when in progress). A **"Hide watched"** toggle
  in the header filters the list down to what's left to watch. Opening a show still focuses your
  last-watched episode (#22); when that episode is hidden by the filter, focus falls to the first
  visible one instead of losing focus.
- **Mark an episode watched / unwatched manually.** Long-press an episode for a new **"Mark as
  watched"** option (or **"Mark as unwatched"** if it's already watched) — corrects the auto-detected
  ≥95% state without playing the episode. Marking watched restarts the episode from the beginning the
  next time you press Play (it won't jump to the credits).
- **TV Guide: a "now" line, Jump-to-Now, catch-up badges, genre dots and a preview strip.** The guide
  grid now draws a red vertical line at the current time; a **"Jump to Now"** button in the header
  scrolls the timeline back to now (handy after browsing the catch-up archive); programmes you can
  rewind from show a ↻ badge; channel labels get a small colour dot by genre
  (sport / news / movies / kids / music / docs); and a non-modal strip at the bottom previews the
  programme under the cursor (title, channel, time, runtime, catch-up, synopsis) without opening the
  dialog — OK still opens the full detail.
- **Movies: watched state on posters and a resume label.** Movie posters (and the compact list rows)
  now show a ✓ badge (with dimmed art) once watched to ≥95%, and a thin progress bar when part-watched,
  matching the Series episode view. The movie detail pane shows a `Resume <time>` label under the poster
  when there's an unfinished position, and long-press gains a **"Mark as watched / unwatched"** option
  (mirrors Series; marking watched still restarts from the beginning on Play).
- **Player: a next-episode countdown card.** When a series episode nears its end, a card appears with a
  countdown to the automatic next-episode advance plus **Play now** and **Cancel** — so you can jump
  early or stop the auto-advance. Works on both the mpv and ExoPlayer engines.

### 🐛 Fixes

- **All seasons now reachable on long-running series.** The season selector on the Series detail screen
  was a single non-scrolling row, so shows with more seasons than fit on one line (e.g. a 12-season
  series) had the seasons past the visible ones clipped off the right edge — invisible and unreachable
  with the D-pad. The selector is now a scrollable rail: Right/Left moves season-by-season and
  auto-scrolls the focused season into view, and opening a show scrolls straight to the active
  (last-watched) season.
- **Clearer 4K decode-guard message.** When a stream's format can't be hardware-decoded on the TV and
  falls back to software decoding (which can't sustain >1080p), the error now explains the stream's
  format is the issue rather than implying the TV can't play any 4K content — the TV may still play
  other 4K videos fine.
- **Player seek bubble now shows time remaining.** The scrub bubble above the seek thumb was not
  displaying (padding couldn't lift it out of the bar) and, once fixed, now reads the time left to the
  end (e.g. `-12:34`) — the elapsed and total times are already shown at the bar's two ends.
- **Favourite "On Now" now covers every favourite channel.** When the Favourite Channels row was set to
  **On Now**, the inline mini-guide only looked up programme data for the first ~10 favourites and left
  the rest without guide info. The builder now reads programme summaries for the whole candidate list in
  a single batched query, so every visible favourite shows its airing show (community PR #62 by
  [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV).
- **Home artwork and metadata from TMDB.** The Home hero card and Continue Watching series tiles now
  prefer **TMDB backdrops, title logos and plot text** when metadata is available, while preserving the
  provider artwork/text fallbacks. Continue Watching series tiles resolve episode/show artwork on focus
  and render as **landscape cards** instead of stretched portrait art. The hero's expanded view now uses
  a landscape backdrop with a title logo, plot and a Play action (community PR #62 by
  [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV). Requires metadata cache v13 (Room
  migration `12 → 13`, additive `logoPath` column on `metadata_cache`).
- **Home refreshes after a playlist switch.** Switching the active playlist from the top-bar quick
  switcher while sitting on Home now updates the hero, Continue Watching, Recent and Favourites rows in
  place — previously you had to leave and reopen Home to see the new source's content (community PR #62
  by [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV).
- **Manual reorder now survives Backup & Restore.** The Move up/down positions you set for channels,
  movies and series (the `content_order` table from v4.0.0) were never written to a backup or restored
  — the resolver supported it but the backup section picker never asked for it. Backup & Restore now
  has a dedicated **Manual reorder** section (export and restore) so your custom order comes back after
  a restore. Existing backup files still restore cleanly; older files simply have no reorder data to
  apply.

## v4.0.2 — 2026-07-07

### 🏠 Customizable Home screen — reorder/hide rows, dwell-to-expand hero, On Now mini-guide (community PR #58 by [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV)

- **Reorder and show/hide every Home row** via the new **Settings → Home screen** page (per profile):
  Keep Watching hero, Recent Channels, Favourite Channels, Continue Watching Movies, Continue Watching
  Series can each be toggled and moved up/down/top/bottom. When every row is hidden, Home says so
  instead of showing a blank screen. Configs ride with **Backup & Restore** (backup format v8; older
  backups restore cleanly with defaults).
- **Filter the Keep Watching hero row** — independent toggles include/exclude live channels, movies and
  series from the hero strip (e.g. keep it VOD-only). Addresses **#43**.
- **Redesigned hero cards — dwell-to-expand** — a card stays compact until it holds focus for **3
  seconds**, then widens to a 16:9 preview with a **blurred-artwork backdrop** (no more stretched
  channel logos — **#49**). Quick D-pad sweeps never expand; the video preview starts only after the
  expansion settles, and the row stays anchored on the active item across data refreshes.
- **"On Now" mini-guide rows** — Recent Channels and Favourite Channels can each display as **Cards**
  or **On Now**: an inline programme guide with the currently-airing show, live progress bar, and the
  next ~6 hours, sharing the real EPG renderer. Up/Down picks a channel, Left/Right scrolls the
  timeline, OK tunes. Favourite Channels defaults to On Now.
- **New Recent Channels row** (hidden by default) — recently tuned live channels, respecting the active
  playlist filter.
- **Times follow the device's 12h/24h clock setting** across Home, Live TV preview, TV Guide and the
  catch-up dialog (previously always 24h).

### ⚙️ Settings menu reorganized

- **Profiles** moved to the **top** of Settings (own "Profile" group, first focused row).
- **Live preview** and **Preview audio** moved from Content into the **Playback** group.
- **App startup** (Home / Last channel / Live TV Favorites) now lives in the **App** group.
- **Home screen** (new page above) sits in Content; the **Android TV home** toggle + refresh moved into it.

### 🗂️ Multiple playlists — switch the whole app to one playlist (or all)

- **Selecting a playlist as "Default" now actually filters the app.** Previously the Default toggle only
  changed a label; the Browse screens always merged every playlist. Now choosing a default narrows
  **Live TV, Movies, Series, TV Guide, Search, and the Home rails (Continue Watching / Favourites)** to
  that one playlist. Choosing **All playlists** (no default) restores the merged view — exactly the old
  behaviour. It's a view filter only: nothing is deleted or re‑imported, and switching back to All brings
  everything straight back.
- **New top‑bar playlist switcher.** With 2+ playlists, the playlist chip in the top‑right becomes a
  button (with a ▾) that opens an **All playlists / A / B / C** picker. It applies everywhere instantly and
  **persists across restarts**, so you can switch without opening Settings.
- **Default is now chosen in the playlist's Add/Edit form** via a **"Default playlist"** toggle (instead of
  a per‑row button). The Sources list shows a **DEFAULT** badge as a status marker. Turning the toggle off
  on the current default clears it back to **All playlists**.
- **Favourites & History inside each section respect the selected playlist** — with a single playlist
  active you no longer see another playlist's favourites/history mixed in; the rail counts match too.
- The selected default is included in **Backup & Restore** (Sources section).

### ✨ VOD engine fallback (movies & series play on more devices)

- **Automatic second-engine retry for Movies & Series** — if a movie or episode terminally fails on
  the mpv engine (file rejected, decoder stall, all retries exhausted), the same item is now retried
  automatically on ExoPlayer at the same position before any error is shown. Some devices/providers
  play streams on ExoPlayer's decoder path that mpv can't open — previously those items just errored
  even though the hardware could play them (as Live TV, which starts on ExoPlayer, proved). Each item
  gets one fallback attempt; if **both** engines fail, the error says so explicitly ("Playback failed
  on both video engines") instead of a misleading single-engine message.
- **New setting: Settings → Video Player → "Movies & Series player"** — choose which engine plays VOD
  first: **mpv** (default; widest format support — DTS/TrueHD audio, unusual containers — plus the
  A/V sync nudge) or **ExoPlayer** (for TVs/providers where mpv can't start movies at all; no
  DTS/TrueHD decoding and no A/V sync fix). Whichever is picked, the other is still tried
  automatically on failure, in reverse order. Live TV and catch-up are unaffected. The setting is
  included in Backup & Restore like the other player preferences.
- **Player top bar shows the active engine** — the mini chips in the player's top-left (aspect ·
  resolution · fps · audio) now lead with **MPV** or **EXO** on every stream — Live TV, Movies and
  Series — so you can always tell at a glance which engine is playing.
- **Stream Info shows the active engine** — the player's info overlay now leads with an "Engine" row
  (mpv / ExoPlayer, including *why* ExoPlayer is active: preferred, fallback, or image-subtitle
  handoff), and shows real ExoPlayer codec/resolution/audio/buffer data while it owns playback.
- **In-player engine toggle for movies & episodes** — the player's **engine toggle (the ⇄ MPV/EXO
  pill, same spot as Live TV's compatibility mode)** switches the **current** item between mpv and
  ExoPlayer at the same position, without changing the global setting. Useful to check whether the
  other engine exposes a subtitle or audio track the current one doesn't — flip, check the tracks,
  and stay on whichever works. The pill shows the active engine (teal while on ExoPlayer) — and,
  like Live's compatibility mode, the choice is **remembered per movie/episode**: a toggled item
  opens on that engine every time, while everything else keeps following the setting.
- **Engine toggle restyle + confirmation toast** — the Live "compatibility mode" and the in-player
  mpv/ExoPlayer switch are no longer a gear icon: they're one labeled pill that shows the active
  engine (MPV or EXO) and turns teal on the non-default one. Flipping it briefly pops up a small
  "Switched to MPV" / "Switched to ExoPlayer" note at the bottom of the player, so the change is
  always confirmed. Applies everywhere the toggle appears: Live TV, Movies, Series, and channels
  opened from the Guide.
- While ExoPlayer owns VOD playback: subtitles (text **and** image) and audio tracks are selectable
  directly on it, autoplay-next keeps working across episodes and seasons, and progress/resume is
  tracked as usual.

### 🔄 Per-source Auto Refresh (playlists & EPG)

- **Each playlist and EPG source can now refresh itself automatically** — open Settings → Manage
  sources (playlists) or Settings → EPG sources and pick an **Auto refresh** mode per source: **Off**,
  **Refresh at startup** (once per cold app start), or a staleness interval (playlists: 6h / 12h / 24h
  / 48h; EPG: 1h / 3h / 6h / 12h / 24h / 48h). Interval modes are checked on cold start **and** when
  the app returns to the foreground; a source refreshes only once it's actually stale (now − last
  successful sync ≥ the chosen threshold), so resuming the app doesn't re-sync everything every time.
- **Off by default** — new playlist and EPG sources start with Auto refresh **Off**; nothing syncs in
  the background unless you turn it on. Existing users who had the old "Refresh on startup" toggle
  enabled are migrated to **Refresh at startup** so their behaviour is unchanged.
- **Failure-safe freshness** — a failed EPG sync no longer marks the source as freshly synced, so a
  source that errors stays "stale" and is retried on the next check instead of being skipped for the
  full interval. Never-synced sources are always treated as stale. Auto refreshes preserve existing
  data (they never clear-then-reimport); a manual sync still does the full replace.

### 💾 Backup & Restore now covers every persistent setting

- **Auto Refresh selections are backed up** — the per-source playlist and EPG Auto refresh modes ride
  with the **Profiles & sources** section. On restore, a saved mode is re-applied only if that source
  still exists; ids that no longer exist are skipped, and an unknown/corrupt mode falls back safely to
  **Off**. Sync timestamps are **not** backed up — after a restore the app re-derives freshness from
  the restored mode and the real sync state.
- **Per-item compatibility mode is backed up** — the Live TV "compatibility mode" pins and the
  Movies/Series per-item engine pins (mpv / ExoPlayer, set from the player's engine toggle) are now
  saved and restored with the **App settings** section. They're keyed by stream URL, so they survive a
  re-sync, and restore **merges** them into any pins you've already set rather than replacing them.
- **Audit gaps closed** — the **Default source** selection and the legacy **"resume last channel"**
  preference were being stored but not backed up; both are now included. Every user-facing preference
  in the settings store is now covered by Backup & Restore.
- **Download folder is backed up too** — the chosen **Download folder** (Settings → Storage) was the
  one persistent setting still missing; it now rides with App settings and restores on import. On a
  different device a path that no longer exists harmlessly falls back to app storage, so a stale
  restore never breaks downloads.
- **Backward compatible** — older backup files that lack any of these new fields still restore
  cleanly: missing Auto refresh defaults to the normal app behaviour (EPG stays Off), and missing
  compatibility-mode/default-source fields simply leave your current values untouched. Unknown or
  invalid entries are ignored — a restore never crashes on them.
- **Customize PIN lock is backed up** — each profile's Customize PIN rides with the **Profiles &
  sources** section and is restored per profile (PINs for profiles that no longer exist are dropped
  safely; older backups without the field restore as before).

### 🎬 TMDB metadata enrichment (Movies, Series & Episodes)

- **On-demand TMDB enrichment** — cached posters, plots, cast, genres, ratings and backdrops from TMDB,
  filling the gaps your playlist leaves. Fully opt-in and cached in Room; no bulk calls. Works out of the
  box via a shared caching server (no setup), or bring your own TMDB API key / self-hosted server.
- **Metadata source mode (Settings → Metadata)** — choose **Provider only**, **Provider + TMDB** (provider
  wins, TMDB fills gaps), or **TMDB only** (TMDB preferred). Advanced key/self-host fields appear only when
  TMDB is on.
- **TMDB Details window** — long-press a movie or series (or episode) → **TMDB Details** opens a scrollable
  window with the backdrop/still, full overview, cast, genres and rating (Back to close).
- **Series & episode enrichment** — series show pages and, inside a series, a new **episode detail pane**
  showing each episode's TMDB still, plot, air year and rating (resolved lazily per season).
- **Sort by rating** — the Movies & Series sort chip now cycles Provider → A–Z → **Rating** (highest first).
- **Cleaner detail pane / interaction** — the side detail pane is now display-only (single-press plays,
  long-press for Favorite / Download / TMDB Details), which also fixes D-pad navigation from the grid to the
  pane. Episode rows lost their play/download icons (single-press plays, long-press for Download / Details).
  Downloading an already-downloaded item shows a toast instead of re-queuing.
- **Better title matching** — provider prefixes like `4K-OSN+ - ` are now stripped before searching TMDB, so
  more messy playlist titles resolve correctly.
- **Refetch TMDB details (long-press)** — clear a wrong/stale TMDB match (or a 7-day "no match" cache) and
  re-search immediately, on Movies, Series, and Episodes — no need to wait for the cache to expire. Lets the
  improved title matcher reach titles that failed before the fix.
- **Set TMDB name (long-press)** — manual override for titles the matcher still gets wrong: type the exact
  TMDB title (and optional year) and OwnTV re-searches under that name, on Movies and Series. The override
  survives playlist re-syncs; Clear reverts to automatic matching. Episodes inherit their series' match.
- **In-app toasts** — transient notices (refetch, already-downloaded, re-search) now use a themed in-app
  toast instead of the system toast.
- **🎞️ In-app trailers (Movies & Series)** — long-press → **Play Trailer** (shown only when TMDB has one)
  plays the YouTube trailer in a floating window styled like the TMDB Details window, with Exit, a progress
  bar and D-pad ◀/▶ ±10s seek. Falls back to opening the YouTube app if the built-in player can't run.
- **Self-hostable metadata server** — the caching-proxy Worker source now ships in `worker/` with a README,
  so anyone can deploy their own and point OwnTV at it.
- **Attribution** — Settings → Metadata shows the TMDB logo and the required notice: this product uses the
  TMDB API but is not endorsed or certified by TMDB.

### 🙈 Hide individual movies & series — and a Customize PIN lock

- **Hide any single movie or series** (not just whole categories) — long-press an item → **Hide**
  removes it from everywhere at once: global **Search**, in-section search, its **category**, the
  **All** list and count, **Home** rails (Continue Watching / Favourites), the Android TV **Watch
  Next** launcher, and **Downloads**. The downloaded file stays on disk and the item returns the
  moment you unhide it — exactly like Live TV's per-channel hide.
- **Hidden categories now hide their items everywhere too** — previously hiding a Movies or Series
  category only dropped the folder from the rail, while its items still showed in **All** and
  **Search**. Hiding a category now behaves like Live TV: the items vanish from Search, All and the
  Home/launcher rails until you unhide the category.
- **Unhide everything from one place** — Settings → **Customize & Hidden Items** (renamed from
  "Customize Category", since it now manages hidden items too) lists every hidden channel, movie and
  series per section, each with an **Unhide** button.
- **Optional PIN lock on the Customize screen** — tap **🔒 Set PIN** at the top-right of Customize &
  Hidden Items to lock it; afterwards every entry asks for the PIN, so nobody else can unhide items
  or change your category setup. It is per-profile, asked each time you open the screen, and
  **deliberately not included in backups** — a lock code shouldn't travel in a readable file, and a
  restore must never lock you out.

### ✨ External player — play movies, series & downloads in VLC / MX Player

- **New setting: Settings → Video Player → "External player"** — when on, pressing Play on a **Movie**,
  **Series episode**, or **Download** opens the stream in an external video player (VLC, MX Player, …)
  instead of the built-in one. Useful for streams this app can't decode, or if you simply prefer another
  player. Turning it off restores normal in-app playback. The setting is included in **Backup & Restore**
  like the other player preferences.
- **Long-press "Play with external player"** — every movie and series episode's long-press menu has a
  new action that plays just that item externally, **regardless of the global setting**. Completed
  downloads get an **"External"** button next to Play.
- **Live TV is unaffected** — channels always play in the built-in player (external routing would lose
  rewind/catch-up). Movies, Series and Downloads are the only sections that route externally.
- **Smart hand-off** — if more than one player is installed you get a chooser; if exactly one is set up it
  opens directly; if none is installed you get a clear "install VLC or MX Player" message instead of a
  silent failure. Downloaded files are shared safely via a content URI (not a raw file path).
- **Trade-offs when playing externally** (the same ones every IPTV app has): resume position and
  prev/next aren't available, and streams that require a custom User-Agent or referer header may not
  play in the external player. Watch history is still recorded.

### 📺 Live TV closed captions now work (#57)

- **ExoPlayer engine: embedded CEA-608 captions on raw MPEG-TS channels are now detected.** IPTV
  panels almost never declare captions in the stream tables, so the player never exposed them; the app
  now surfaces the standard **CC1** track on every `.ts` live channel (HLS channels already worked).
  Because detection is unconditional, the CC entry also appears on `.ts` channels that carry no
  captions — selecting it there simply shows nothing.
- **mpv engine: selecting the CC track now actually renders captions.** CC text can only be extracted
  by the software video decoder, so while a CC track is selected the channel temporarily switches to
  software decoding (≤1080p only — the same GL path used by the decoder-rescue fallback) and switches
  straight back to hardware decoding when CC is turned off or you change channels. Expect a ~1s
  blip when toggling. On >1080p channels captions stay unavailable on mpv rather than risking
  stutter; use the ExoPlayer engine there.

### 🌦️ Weather settings submenu — Celsius / Fahrenheit

- The two weather rows on the Settings root are now a proper **Settings → Weather** submenu with three
  options: **Show weather** (top-bar chip on/off), **Custom location** (city or "lat,lon"; blank =
  auto-detect — useful on a VPN), and a new **Temperature unit** toggle (**°C / °F**) for the top-bar
  chip. All three are included in Backup & Restore.

### ⚠️ Low-zoom memory warning (#51)

- **Setting UI Zoom below 85% now asks you to accept the risk first.** Lower zoom draws far more
  items on screen at once, which can crash devices with limited memory (e.g. 2 GB Fire TV sticks)
  when combined with large playlists and EPG data. Stepping under 85% shows a one-button warning —
  **OK** ("I understand and accept the risk") continues, **Back** keeps zoom at 85%. If your zoom is
  already below 85%, the dialog doesn't nag.

### 🐛 Fixes

- **Fixed D-pad navigation from the Movies/Series grid to the detail pane** — the display-only pane no
  longer traps focus on the way right.
- **Fixed episode long-press menu losing focus** — after an action in the episode context menu (e.g.
  Refetch TMDB details), focus now returns to the episode row instead of jumping away.
- **Failed TMDB lookups are no longer remembered as "no match"** — a network error, rate limit or proxy
  outage during a lookup now simply retries on the next open, instead of being negative-cached for 7 days
  like a genuine "title not on TMDB" answer. The Settings test lookup also distinguishes "server
  unreachable" from "no match".

- **Live channel-list overlay now matches the channel you launched from Home (#55)** — pressing Left
  while a Live channel plays opens the quick channel-list overlay. When you started the channel from a
  Live TV **category**, it correctly listed that category — but when you started it from the **Home**
  screen (Keep Watching or a Favourites rail), the overlay still showed the *previous* category's list.
  The Home launch path updated the CH+/CH- zap list but not the list the overlay reads, so the two
  disagreed. The overlay now reflects the same list you're zapping through — the Keep Watching /
  Favourites channels you actually opened.
- **Active nav section stays visible when focus moves away (#47)** — in the left navigation and the
  category rail, the *selected* item lost all highlight as soon as you moved focus to another item, so
  at a glance you couldn't tell which section/category was actually active. Both now use a consistent
  four-state treatment: **selected + focused** (full accent fill) → **focused** cursor (surface fill +
  teal outline) → **selected but unfocused** (soft tonal fill, accent tint and a persistent left accent
  bar) → idle. The accent bar gives a colour-independent marker of the active tab for low-contrast
  panels. Selection/focus boxes are also slightly less rounded (box-style) and the nav bar sits a little
  closer to the first panel, so the whole left navigation reads as one consistent system.
- **4K Live channels no longer break playback on some TVs** — on certain low-end panels (e.g. some
  Hisense models), watching a 4K channel could wedge the TV's hardware video decoder: every channel
  afterwards took ~20 seconds to start, and it stayed broken until the TV was rebooted (Google TV /
  higher-end sets were unaffected). The Live engine (ExoPlayer) was *parking* and reusing its decoder
  between channels instead of releasing it, so the stuck 4K decoder was never handed back. Now, whenever
  you **leave a UHD (>1080p) channel** — Back, exit full-screen, background, or zap to another channel via
  CH+/-, the D-pad, or the channel-list overlay — the decoder is **fully released** so the next channel
  starts cleanly. It's scoped to 4K only, so normal SD/HD zapping keeps the same fast, instant switching.
- **Live engine pill now shows the engine that's actually playing** — when a Live channel auto-fell-back
  from ExoPlayer to mpv, the MPV/EXO pill still read **EXO** (it was showing the saved pin, not the live
  engine), and tapping it appeared to do nothing. The pill now reflects the **running** engine, and one
  tap always switches it — flipping to mpv (and remembering the channel) or back to ExoPlayer. (The
  Movies/Series pill already tracked the live engine and is unchanged.)
- **Live TV zoom / aspect modes now work** — choosing Fit, Fill / Crop, Stretch, Original, Force 16:9
  or Force 4:3 on a Live TV channel did nothing at all (the picture never changed). Live channels play
  full-screen on ExoPlayer (the live engine), and that path had no zoom implementation — the mode was
  stored but never applied to the surface. Zoom/aspect now works on Live TV just like on Movies and
  Series, whether the channel plays on ExoPlayer or on mpv (a compatibility-mode pin).
- **Fill / Crop now actually zooms in and crops** — on Movies, Series and Live, "Fill / Crop" could
  look identical to Fit (especially on 16:9 content), or read as a stretch rather than a crop. It now
  takes the fitted picture and scales it up ~20% so it always visibly zooms and fills edge-to-edge,
  regardless of the source's aspect ratio. (Stretch remains a true distort-to-fill.)
- **Weather chip: VPN-friendly location override + hide toggle (#45)** — the top-bar weather guesses
  your city from your public IP, so on a VPN it showed the VPN server's city instead of yours. You can
  now set a manual **Weather location** (Settings → Appearance) — a city name (e.g. *London*) or a
  raw `lat,lon` pair (e.g. `51.5,-0.12`) — which is geocoded via Open-Meteo and overrides IP lookup.
  Leave it blank for the previous auto-detect behaviour. There's also a **Show weather** switch to hide
  the chip entirely. Both settings are included in Backup & Restore. Default ON + blank location means
  existing users see no change.
- **Modal D-pad focus can no longer escape into the UI behind it (#48)** — in the Exit, Avatar picker,
  Rename/Text-input, Resume, App-update and EPG-sync-prompt dialogs, pressing Left/Right/Up/Down from
  a button could move focus into the browse UI behind the dialog, leaving Cancel/Exit unreachable
  (only Back could dismiss it). A new all-directions focus trap keeps D-pad focus inside every modal
  scrim; Back still closes each dialog as before.
- **Focus returns to the right item after a long-press context menu (#46)** — on Live TV, Movies and
  Series, long-pressing OK on an item and closing the menu (Cancel / Favourite / Hide / Remove from
  history / Download) used to jump focus to the left Category rail. Focus now lands back inside the
  list/grid: on the exact item if it's still there, or on the **nearest surviving neighbour** if it
  was removed (e.g. unfavouriting on Favorites, or Remove from History) — only leaving the pane when
  the category becomes empty. The restore is now deterministic (id + position based), fixing an
  intermittent race where the paged list still held a stale copy of the removed item.

## v4.0.1 — 2026-07-03

### 🐛 Fixes

- **D-pad focus no longer jumps to the top bar while scrolling long lists** — holding Up in a big
  category rail or channel/movie/series list (e.g. 500 categories) could make focus outrun the list
  and teleport to the top bar's Search button. Focus now stays inside the panel you're in; you leave
  it only deliberately with Left/Right or Back.
- **Top-bar Search button now appears only while the highlight is on the left nav panel** — inside
  Live TV, Movies, Series, Guide, Downloads or Settings it fades out (keeping its space, so the
  clock/weather chips never shift) and can't take focus. It fades back in when you return to the
  nav panel, where it still opens Search as before.
- **Autoplay next episode no longer fails with a "malformed or corrupted" error** — when an episode
  ended and autoplay advanced, some providers still held the finished episode's connection slot, so
  opening the next episode hung and the player gave up with a misleading corruption error (the same
  episode then played fine manually). A hung open now gets one automatic silent reset-and-retry —
  the transition shows a few extra seconds of spinner instead of an error. Only a second consecutive
  hang still surfaces the error.
- **Player HUD no longer steals D-pad focus from overlays drawn above it** (community PR #41 by
  [@attembot](https://github.com/attembot) — Michael Botta).

## v4.0.0 — 2026-07-02

### 📄 License

- OwnTV has moved from the **MIT License** to the **GNU General Public License v3.0 (GPLv3)**. OwnTV
  remains fully open-source — anyone can use, study, modify, and redistribute it, including commercially —
  but any redistributed version (forks, modified builds, or commercial products built on it) must also
  be licensed under GPLv3 with its source made available. Versions released before this change remain
  available under MIT. See [LICENSE](LICENSE).

Big release — the community‑feedback **UI upgrade** (3 phases; Phase 1's quick wins are the first two
entries below) folded together with a large batch of new features, performance work and fixes.

### ⚡ Much faster syncing & background updates (community PR #40 by [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV, integrated & hardened)

- **Priority sync during setup** — when adding an Xtream playlist you can choose what to import first
  (e.g. Live TV only). You land in the app as soon as the priority content is ready, and the rest
  (movies/series) finishes automatically in the background — even if you leave the screen or the
  device sleeps (WorkManager-backed, survives sleep/reboot).
- **Incremental re-syncs** — re-syncing a source now compares content hashes and only writes what
  actually changed, instead of re-importing everything. Re-syncs of large playlists are dramatically
  faster and no longer churn the database.
- **Incremental EPG sync** — guide refreshes also skip unchanged programmes and prune removed ones.
  Memory use is strictly bounded, so even multi-million-programme guides stay safe on low-RAM boxes.
- **More resilient downloads** — playlist/EPG downloads retry automatically on transient network
  errors, and sync progress reporting is smoother and more accurate.
- Integration hardening on top of the PR: database migrations were renumbered so both v3.2.0 users
  and dev builds upgrade cleanly (final schema v9); staged priority syncs now correctly mark the
  source as synced once the background remainder finishes; favorites/history/resume are re-attached
  after *every* sync attempt (permanent cleanup only after a fully successful full sync); and EPG
  hash tracking loads per-channel with a hard memory cap.
- Post-integration fixes from on-device testing:
  - **Favorites/history could vanish when several playlists refreshed at once** — cleanup of stale
    user data is now strictly scoped to the playlist that actually synced (an empty sync snapshot
    never triggers a global cleanup anymore), and parallel startup refreshes can no longer purge
    against each other's in-flight state.
  - **M3U playlists: movies tagged as VOD landed in Live TV again** — the sync rewrite had dropped
    the VOD detection; entries tagged `type="vod"` / `type="movie"` / `tvg-type="movie"` go back to
    the Movies grid with their own categories.
  - **NEW: M3U series playlists import as real series** — entries tagged `type="series"` /
    `tvg-type="series"` (per-episode lines like *"Stranger Things S01E05"*, also `1x05` style) are
    now grouped into shows with seasons and episodes under the **Series** tab, instead of piling up
    as live channels or loose movies. Entries without an episode pattern become a show with
    sequentially numbered episodes.
  - **TV Guide header showed a date up to a week in the past** — with catch-up channels the header
    displayed the archive's start date. It now shows today when the Guide opens, and follows the day
    you're browsing when you scroll back into the archive.
  - **Subtitle/audio selection could open with nothing focusable on HDR/HDR10/DTS content** — the
    player's pickers (subtitles, audio, speed, zoom, volume) were overlays competing with the HUD
    for D-pad focus, and heavy streams could win that race and lock the picker out. They are now
    real dialog windows that own the remote's focus outright — on both engines, live and VOD — so
    selection always works.
  - **Episode list had no panel background** — opening a series showed its episodes on a bare
    background; the list now sits in the same rounded content panel as every other screen.

> ⚠️ **Upgrade note for EPG users:** v4.0.0 redesigned EPG loading. If the Guide shows blank on first open 
> or after re-entry, **delete your EPG sources and re-add them** (Settings → EPG → Edit → delete, then add 
> again) and resync. Old cached EPG data is incompatible with the new loader — a fresh import fixes it. 
> This is a one-time fix after upgrading.

### 🐛 Fixes

- **Live TV could give up reconnecting too early during a real outage** — a single failed reconnect
  attempt was being counted twice against the retry budget (ExoPlayer fires both an error and an idle
  event for the same failure), so a provider hiccup that needed ~30–60s to recover could exhaust all
  retries and show "Lost connection to this channel" well before the stream was actually back. Reconnect
  attempts are now deduplicated so each real failure only counts once, and the retry budget was raised
  slightly to cover longer outages.

- **Audio-plays-but-no-video no longer leaves you stuck on a black screen** — some streams/files could
  play sound with no picture (both Surround Sound on and off), because the existing freeze watchdogs only
  caught a *total* stall or a freeze *after* a frame had already been seen — never "audio/position is
  advancing fine, but a video track exists and has never produced a single frame." All three playback
  paths now detect this specifically:
  - **Live TV, ExoPlayer (primary engine):** if no video frame renders within ~8s while audio/position
    keeps advancing, it automatically tries the mpv compatibility fallback once (shows the spinner during
    the switch, no loop). If mpv plays it fine, playback continues normally; if mpv also fails, a clear
    on-screen message is shown.
  - **Live TV, mpv (compatibility-mode / fallback channels):** the same condition now triggers the existing
    bounded reconnect/reload path; if video still doesn't appear after the retry budget, shows "Audio is
    playing, but video could not be rendered on this device."
  - **VOD, image-subtitle handoff (PGS/VOBSUB/DVB subtitles):** the brief ExoPlayer handoff used only for
    these subtitle types now has the same first-frame timeout, falling back to mpv with a clear message if
    it can't render video either. The main VOD (mpv) path already had a working no-video watchdog.

- **Favorites could disappear after a source re-sync failed partway through** — a source's clear-then-insert
  import is deferred per chunk (old content is only wiped once new data starts arriving), so a sync that
  failed midway (e.g. flaky Wi-Fi right as a Fire TV woke from sleep) could leave content partially cleared.
  Favorites/history/resume are re-attached to the new content ids only after a *successful* sync, so a
  failed one left them silently orphaned (rows still existed but resolved to nothing) until a later sync
  healed them — in the meantime they simply looked gone. Re-attaching now runs after every sync attempt,
  successful or not; only a fully successful sync is still allowed to permanently drop favorites for
  content the provider actually removed.

- **Live TV no longer freezes silently mid-stream** — a live channel could play smoothly and then
  freeze/hang with no spinner, no reconnect and no error (replaying the channel fixed it). This happened
  when a feed stalled in a way the player didn't *signal* — the stream stops advancing while the socket
  stays open, so there was no buffering event, no error and no end-of-file to react to. Both playback
  backends now detect this:
  - **ExoPlayer (the primary live engine):** the silent-freeze watchdog now keys off *intent to play*
    instead of the stricter "is-playing" flag (which briefly flickered off during a stall and kept
    resetting the freeze timer), and adds an absolute "no forward progress for ~8s" backstop that can't be
    missed even if per-frame detection isn't available. On a stall it shows the spinner and auto-reconnects
    to the live edge (bounded retries with back-off), surfacing "Lost connection to this channel." only
    after repeated failures.
  - **mpv (compatibility-mode / fallback channels):** added an equivalent live progress watchdog that
    detects a frozen stream, shows the spinner and reconnects with a bounded retry budget.
  - The loading spinner is now shown consistently while a live stream is buffering, reconnecting or
    retrying in either backend, and clears once playback resumes or a final error is shown. Detailed
    Logcat is emitted around buffering / freeze detection / reconnect attempts for diagnosis.
  - **Follow-up:** closed a second silent dead-end in the ExoPlayer (primary live) engine — if a feed
    dropped into `STATE_ENDED` or unexpectedly into `STATE_IDLE` mid-playback, it was previously ignored
    entirely (no spinner, no reconnect, no error). Both are now treated as a recoverable stall and
    auto-reconnect, while a normal stop/back/release still exits cleanly with no reconnect attempt. Added
    a debug-only diagnostic log (state transitions, watchdog/reconnect events) plus a small bounded
    on-device diagnostic file, so a future recurrence can be captured even if it happens unobserved —
    see `extras/LIVE_TV_HANG_DIAGNOSTICS.md`.

- **EPG match no longer removes a channel from the Guide** — matching a channel's EPG (auto or manual)
  could silently delete its stored programmes and leave the channel blank and then invisible in the
  Guide. This happened when multiple EPG sources were configured and a cache re-fill across a large
  source file was interrupted before it could restore the deleted rows. The cache re-fill is now
  parse-then-apply: programmes are only deleted for ids where fresh replacement data was successfully
  parsed first. Channels that had no in-window data in any fresh cache keep whatever they already had.

- **Show/Hide password toggle on all password fields** — a **Show / Hide** button now appears on the
  right of every password field (Xtream password when adding/editing a playlist; PIN fields in profile
  setup and profile settings). The toggle is D-pad focusable independently of the text field, so the
  password can be revealed and re-hidden without opening the keyboard. Previously there was no way to
  see the password you had typed on either the first-run setup screen or the Settings → Playlists edit
  screen.

### ✨ New features

- **Backup now covers more settings and encrypts saved passwords** — the backup file now also includes
  surround sound, auto-play-next, Guide sort, animation level, Movies/Series view mode, catch-up timezone
  & offset, the global proxy (host/port/user/enabled), and each profile's startup landing screen. Saved
  passwords (source/playlist and proxy) are no longer written in plaintext: on export you can set a
  **backup password** to encrypt them (AES-GCM, field-level only — the rest of the file stays readable),
  or export without passwords. On restore you're prompted for that password; a wrong password never wipes
  anything and lets you retry, and you can skip it to restore everything except saved passwords. Old
  backups still import as before. Both restore entry points (Settings and the first-run setup wizard)
  prompt for the backup password.
- **Manually reorder channels, movies and series** — long-press any item in a **category folder** or **Favorites**
  and choose **Move**. A full-screen reorder overlay appears with the full list; **D-pad Up/Down** moves the item
  up or down, **OK** saves, **Back** cancels. The order persists across playlist re-syncs and is included in
  profile backups / restores.
- **Remove a single item from History** — long-press any item in the **History** folder and choose
  **Remove from History** to delete just that entry. The existing bulk "Clear watch history" in Settings is
  unchanged.
- **Download from long-press menu** — Movies and Series now show a **Download** / **Download all episodes**
  button directly in the long-press context menu, alongside the existing detail-pane download button.
  Movies queues the file immediately; Series queues every locally-cached episode (open the series once first
  if no episodes appear).
- **Settings → Customize Category** — the "Customize" settings row has been renamed **Customize Category** to
  clarify it affects categories (hide, rename, reorder), not individual items.
- **Global HTTP proxy support** — **Settings → Network → Proxy** lets you route all OwnTV traffic
  (playlist sync, Xtream API, EPG, images, downloads, updates) and fullscreen playback through an HTTP proxy.
  Enter a proxy host and port (optionally with username / password); a **Test Proxy** button verifies connectivity
  before saving. Disabling the proxy restores direct connections. The proxy is applied globally across all
  playlists — per-playlist proxy overrides and SOCKS5 support are planned for future versions. See
  `extras/PROXY_SUPPORT_PLAN.md` for full details and limitations.
- **Home screen with Continue Watching** — a new **Home** tab opens to a hero carousel of your partially‑watched
  movies, episodes and recent live channels (newest first); the selected card is shown large with its poster and
  starts a muted video preview when focused, and pressing **OK** resumes right where you left off. Below it is a
  **Favourite Channels** rail. On **stock Android TV** launchers it also feeds the system **"Continue Watching"**
  (Watch Next) row, so you can resume straight from the TV home screen — Settings → Android TV home → **Refresh
  now** rebuilds those cards (with a *Rebuilding… → Done* status). (Sideloaded Fire TV / Google TV don't surface
  system Watch Next rows, so the in‑app Home screen is the universal landing for everyone.)
  🙏 **Huge thanks to [@codeVerine](https://github.com/codeVerine) (Sagar Mukundan UV) for building and
  contributing this entire Home screen feature ([PR #31](https://github.com/ahXN00/OwnTV/pull/31)).**
- **Stream technical info overlay** — in the player, the bottom-bar **info** button toggles a live readout of
  the current stream: video codec · resolution · fps · bit-depth, HDR type, bitrate, decoder (hardware/software
  · direct), audio codec · channels · sample rate, buffer & dropped frames, and the (credential-masked) source.
  Works on both playback engines and updates live.
- **Volume boost to 150%** — for movies, series and any channel played on the mpv engine, the player volume
  can go above 100% (Kodi-style amplification, **capped at 150%**) for quiet streams, with mpv's internal soft
  limiter so it never harshly distorts.
- **Fixed, roomy layout — no more "sandwiched" Live TV** (Phase 2) — the navigation and category panels no
  longer expand and collapse as you move the D‑pad, so the interface never jumps around. Live TV is now a
  stable grid: a slim **icon nav**, a **full‑label category column** (no more 2–3 letter abbreviations), the
  **channel list**, and a large **preview** — each a fixed size. The same fixed nav + category column apply
  across **Movies, Series and the Guide**. The result also feels noticeably faster on lower‑end boxes.
- **Shell redesign — new sidebar, top bar, and rounded panels** (Phases 0–7) — the entire app shell has been
  rebuilt with a fixed icon-only left rail: **brand logo** at the top, **nav items** vertically centered
  (scrollable at high UI zoom), **profile avatar** pinned at the bottom (click = "Who's watching?" profile
  switcher, even for a single profile; long-press = avatar picker with a new **"no avatar"** option showing a
  silhouette). **Search moved out of the rail** into a new **top bar** that shows the active section name,
  a Search pill on the left, and a **live clock**, **weather chip** (with Canvas weather symbols — sun, moon,
  cloud, rain, snow, thunder — via Open-Meteo, free no-key API), and **playlist name** on the right. All
  content now sits inside **rounded panels** (Option A "Clean + Premium"): the category rail, content grid,
  and preview pane each get their own rounded box with 22dp corners and hairline borders, floating on a dark
  `#040E0B` surface. Settings submenus share the same rounded look. **Theme** renamed from `AMOLED_DARK` →
  `DARK` with a `#040E0B` charcoal default (no more pure black). **Neo Signal Duotone** nav icons
  (Home, Live TV, Movies, Series, Downloads, Guide, Settings, plus a Profile fallback silhouette) drawn on
  crisp 100-unit Canvas. **Top bar is uniform** — all 5 chips (section, search, clock, playlist, weather)
  share identical height. Light mode fully supported with matching panel tints.
- **Clear watch history** — Settings → Content → **Clear watch history** lets you wipe this profile's
  recently-watched / "continue watching" rows — **all of it, or just Live TV, Movies or Series** (with a
  Yes/No confirmation). Playlists, favorites and downloads are untouched.
- **Favorite a channel straight from Search** — long-press a channel in search results to add or remove it
  from Favorites; a star shows the current state. No need to open Live TV first.
- **Detailed channel search results** (Phase 3) — channel results now show **category · channel number** under
  the name, so near‑identical feeds (e.g. several "ABC" or "Sky Sports") are easy to tell apart; long‑press
  still toggles the favourite.
- **Move categories to top / bottom** — in Settings → Customize, each category now has ⤒ / ⤓ buttons to jump
  it straight to the top or bottom of the list, alongside the existing one-step ↑ / ↓.
- **Animations setting (On / Off)** — Settings → Appearance → **Animations** turns interface motion on or off.
  **Off** makes navigation instant — a reduce‑motion / accessibility toggle (the v4.0.0 fixed grid already
  removed the menu lag that a middle "Reduced" tier used to address).
- **Channel list in the player** — while watching a channel full-screen, press **Left** (with the controls
  hidden) to slide out a **channel list over the video** — browse and switch channels without leaving
  full-screen. The current channel is highlighted; Back or Left again closes it.
- **Per‑profile startup (default landing)** (Phase 3) — Settings → **Startup** sets, **per profile**, where the
  app opens: **Home**, the **Last channel** you watched (so a profile that always watches one channel boots
  straight into it), or **Live TV on Favorites**. Replaces the old global "Resume last channel" toggle —
  existing "On" carries over to **Last channel**.
- **Remembers where you were in Live TV** — Live TV reopens on the **category you last had selected** (instead
  of resetting to All) and lands focus back on the **last channel you were on**.
- **Guide by category** — the EPG/Guide has a new **Category** filter so you can view just one group at a
  time instead of every channel at once, with a **search box** in the category list to find a group fast.
- **Favourites in the Guide** — the Guide's **Sort** button now includes a **Favorites** option, filtering
  the guide to just your favourited channels.
- **List view for Movies & Series** — a new **Grid / List** toggle on the Movies and Series screens: switch
  the poster wall to a compact list to see many more titles at a glance.
- **A/V sync nudge in the player** — open the **Audio** menu on a movie/episode for an **A/V sync** stepper to
  nudge the audio earlier/later in 50 ms steps when a badly-encoded file has the sound out of sync. It resets
  per file, so it never throws off your other movies.
- **One-tap guide sync after adding a playlist** — after importing a playlist (first-run setup or Settings →
  Playlists), OwnTV now asks **"Sync the TV guide now?"** if the playlist has a guide feed. **Sync now** shows
  a **live programme count** (just like the playlist import) and a brief "Done"; **Not now** keeps it manual.
- **Long-press a channel in Live TV** — long-press any channel in the Live TV list for a quick menu:
  **Add/Remove Favourite, Rename, Hide, Match EPG**, and **Catch-up** (on channels that support it) — without
  moving over to the preview pane.
- **Closed captions (CC) on Live TV** — channels that embed CEA-608/708 closed captions in the video stream
  (e.g. many US channels like HBO/Showtime/Cinemax) now expose a selectable caption track in the player's
  **Subtitles** menu, instead of showing only "Off". (#28)
- **Compatibility mode (per-channel mpv engine)** — if a live channel shows artifacts or won't play right on
  the fast engine, press the **gear** in the player controls to switch that channel to the mpv engine. It's
  **remembered per channel**, so it opens cleanly on mpv every time after — every other channel keeps the
  near-instant start.

### ⚡ Performance

- **Movies & Series open instantly** — the grids are now **pre-warmed at startup** (like the Guide), and the
  query planner's table stats are refreshed after every playlist sync. A bulk sync does `REPLACE` on 100k+
  rows which invalidates SQLite's stats and made the planner ignore the existing `(sourceId, name)` /
  `(categoryId, name)` composite indices — so the grid fell back to a full-table sort on cold open (the 2–3s
  delay). Stats are now re-analyzed post-sync and at launch so the indices stay chosen. (Mirrors the EPG fix.)
- **The Guide opens instantly** — the guide is now **pre-loaded in the background at startup**, so even the
  first open is immediate, and re-opening no longer flashes a loading spinner or rebuilds from scratch — it
  shows your channel list right away and refreshes silently.
- **Much faster EPG sync** — the guide sync now stores programmes **only for the channels you actually have**
  instead of the entire feed (public XMLTV feeds often carry 10–20× more channels than your playlist). Far
  fewer rows to parse and write means a dramatically quicker, lighter sync.
- **Leaner TV Guide internals** — the guide now loads every row's programmes in **one batched query**
  (grouped into a cache) instead of a separate query per channel row (an N+1 storm), and draws each row's
  timeline in a **single Canvas pass** instead of dozens–hundreds of per‑cell composables. The catch‑up
  lookback streams in on a background thread (memory‑safe on low‑RAM boxes), the channel list is built off
  the main thread, and re‑sorting/filtering reuses the cache. Mostly an efficiency/memory win — lighter on
  large channel lists and multi‑day catch‑up windows.

### 🔧 Internal

- Room database version **6 → 7**: new `content_order` table stores per-profile manual item ordering; included in backup/restore.
- Long-press context menus on Movies and Series replaced the previous instant-favourite-toggle with a full menu (Favourite, Move, Remove from History, Download, Close).

### 🐛 Bug fixes

- **Per-source User-Agent for playback** — each source now supports a **custom User-Agent** (entered in source
  settings), and it is consistently applied to Live TV, Movies, Series, and EPG playback on both mpv and
  ExoPlayer. If playback fails with a format/demuxer error and no custom UA was set, the app retries once
  with the short `vlc` User-Agent — some providers block the full `VLC/3.0.20 LibVLC/3.0.20` string but
  accept the short form. If that also fails, the error message hints: *"This provider may require a custom
  User-Agent in source settings."*
- **No more false "Playback error" over a movie that's actually playing** — on some TVs (e.g. Realtek-based
  panels) the hardware decoder takes a few seconds to negotiate and deliver its first frame, which made the
  VOD watchdog wrongly conclude the file wasn't streamable and show *"This video isn't formatted for
  streaming…"* on top of perfectly-playing video. The watchdog now waits a little longer before that verdict
  and, more importantly, automatically dismisses the popup the moment a real video frame decodes. Genuinely
  non-streamable files still surface the error as before.
- **Startup focus rests on the nav** — on a cold start (or switching to the Home tab) focus now stays on the
  **Home item in the sidebar** instead of being pulled into the content; it only jumps into the hero when you
  return from the player. (Builds on [@codeVerine](https://github.com/codeVerine)'s empty‑Home focus fix,
  [PR #32](https://github.com/ahXN00/OwnTV/pull/32).)
- **Clear watch history now empties Movies/Series from Home too** — clearing history (all, or just Movies /
  Series) now also wipes the **resume positions** that feed Home's "Continue Watching", so those titles
  actually leave the row (previously only Live cleared).
- **Live preview shows full stream spec** — the preview pane's badge now shows **aspect · resolution · fps ·
  audio** (e.g. `16:9 · 4K · 50 FPS · STEREO`) instead of resolution alone.
- **Startup → Live · Favorites lands inside the list** — choosing this startup mode now drops focus on the
  first favourite channel instead of the navigation panel, so you can start zapping immediately.
- **Long‑press channel menu keeps focus on the channel** — closing the Live TV long‑press menu (Cancel /
  Favourite / Hide) now returns focus to that channel instead of jumping back to the navigation panel.
- **Clearer Surround sound warning** — the setting now explains that multichannel can drift audio behind
  video (lip‑sync) on some TVs/soundbars, and points to the player's **Audio → A/V sync** nudge to correct it.
  (Surround stays **off by default**; the drift is a hardware‑latency reality of multichannel LPCM over HDMI/ARC.)
- **Imports survive a provider that errors on the full Movies/Series list** — some providers (e.g. peoplestv)
  return a non-standard **HTTP 512** on the giant bulk `get_series` / VOD response, which used to abort the
  whole import after the channels had loaded. Now a bulk error **automatically falls back to fetching that
  section one category at a time** (small requests those panels serve fine) — and if even that fails, the
  import keeps your channels/movies instead of failing outright. Credentials are also no longer shown in
  import errors.
- **EPG no longer fails on a single malformed tag** — a guide feed with one bad/odd entry used to abort the
  whole sync with a cryptic "END_TAG expected …" error. The parser is now tolerant (relaxed mode + resilient
  text reading) and keeps everything it can, so one bad programme no longer loses the entire guide.
- **Playback survives the screensaver** — leaving the TV long enough for the screensaver no longer leaves you
  on a dead stream. A paused **movie/episode** is restored **paused at the exact spot**, and a **live channel**
  is **re-tuned to the live edge**, when you come back — instead of doing nothing until a manual reload.
- **Live TV no longer freezes with no recovery** — some live streams stop advancing while the player still
  thinks it's playing (no buffering, no error), so the auto-reconnect never kicked in and the channel just
  hung. A new freeze watchdog detects the stalled picture and reconnects automatically.
- **No sound when opening a channel very fast** — pressing OK on a channel a split-second before its preview
  loaded could carry the muted-preview state into full-screen, so the channel played silently. Full-screen
  now always plays with sound.
- **One corrupted file no longer breaks all playback** — a malformed MP4 (broken UDTA metadata pointing to
  a multi-GB offset) sends FFmpeg's demuxer into a 3+ GB HTTP seek that blocks mpv's core thread. Previously
  this poisoned every subsequent video (even healthy ones wouldn't play until app restart). Now the video
  watchdog detects the stuck demuxer (no `FILE_LOADED` after 7s) and **destroys+recreates the mpv instance
  entirely** (the only way to abort a blocked HTTP read), showing a clear error for the bad file while every
  other video continues to play fine.
- **Audio/video drift on some movies** — a few high-bitrate / high-frame-rate movies could play with the
  picture slightly behind the sound, because nothing was dropping the late frames on the direct hardware
  path. The player now drops late frames at the decoder so audio and video stay in sync.
- **Long-press to favourite in Movies and Series** — long-press OK on any movie or series poster (grid or
  list view) to toggle it as a favourite. Same as the details-pane button, just faster — no need to focus
  into the details pane first. The existing star indicator still shows the current state.
- **Sync no longer wipes data on failure** — old channels/movies/series are only cleared when the first new
  row is actually written, not at the start. If a sync fails completely (wrong password, network down,
  timeout), your existing content stays intact instead of vanishing. The Add Source screen now also
  remembers what you typed so a typo doesn't mean re-typing everything from scratch on the remote.
- **Sync times out fast instead of spinning forever** — OkHttp connect/read/write timeouts are now 15/20/20s
  (down from 30/60/30s) and silent auto-retries are disabled. When the network drops mid-sync, the error
  dialog appears in ~20s instead of hanging for minutes. Category-by-category fallback also aborts on
  network errors (continues only for HTTP errors like 512) instead of retrying every category against a
  dead server.
- **M3U VOD entries now route to Movies** — M3U playlists with `type="vod"` or `tvg-type="movie"` entries
  now create movie/stream rows in the Movie grid instead of being incorrectly filed under Live TV. The
  `group-title` becomes the movie category (e.g. "Movies", "Peliculas").
- **Offline banner now works on all devices** — Android TV boxes whose Ethernet interface stays "up"
  forever (never fires network callbacks) now get a 20-second connectivity poll, so the banner actually
  appears when the internet is unreachable.
- **Profile dialog focus no longer escapes** — the edit/create profile popup now uses a `Popup` window
  with `focusable=true` so D-pad stays inside the dialog instead of wandering out to the sidebar.
- **Two-stage video watchdog** — broken files caught faster and more accurately: **Stage 1** (T_OPEN, 10s)
  catches a demuxer that never opens the file; **Stage 2** (T_DECODE, 7s) catches a decoder that opened
  the file but never produced a frame. **Moov-at-end detection** catches MP4s with trailing headers
  from servers without Range support (shows a clear error instead of retrying endlessly); **`END_FILE`
  instant-catch** aborts immediately when the demuxer rejects a malformed file outright. A **thrash
  guard** (3 consecutive hard-resets) prevents infinite tear-down/recreate loops on bad playlists.
  Added `seekable=1` to VOD demuxer options so FFmpeg attempts HTTP Range requests even on servers
  that don't advertise byte-serving.
- **Guide shows programmes on first open** — the EPG guide was blank until you navigated into a row (on large 
  catch-up windows with a lookback), because the auto-scroll-to-now fired before the timeline layout was ready. 
  The scroll now waits for layout, so programmes appear immediately. **Note:** if upgrading to v4.0.0 and the 
  guide remains blank after this fix, **delete the EPG sources and re-add them** (Settings → EPG → Edit → delete, 
  then add the feed again); v4.0.0's new batched EPG loader is incompatible with old cached data, and a fresh 
  re-import ensures compatibility. Resync only after re-adding.

## v3.2.0 — 2026-06-22

### ✨ New features

- **Live rewind (timeshift)** — on a channel your provider records (Xtream catch-up / archive), you can now
  **rewind the live stream** to re-watch a moment you missed (a goal, a play) and then jump back to the live
  edge — without leaving the channel for the Guide. On a catch-up live channel the player gains a **⏪ rewind**
  control; while rewound it shows how far behind live you are, the clock time you're watching, and a **● Live**
  button to snap back to the edge. There's both a **scrubbable timeline** (the last 2 hours up to the live
  edge, with a red live marker — hold ◀/▶ to scrub) **and** ⏪/⏩ buttons for precise 30-second steps, plus a
  **"behind live" counter** that ticks down as the archive catches up (and grows if you pause).

### ✨ Improvements

- **Switch profile without leaving the app** — the profile card (top-left) now has a **Switch Profile**
  button that stops playback and returns to the "Who's watching?" screen, so you can change profile without
  force-quitting the app.
- **Wider category folders** — the Live TV / Movies / Series category rail now expands wider when focused,
  so long category names are fully readable; it still shrinks back when you move into the list.
- **Catch-up defaults to your device timezone** — catch-up / live-rewind timestamps now default to the
  **device's timezone** (was UTC), which matches most providers' server-local archives out of the box; you
  can still override it in **Settings → Catch-up time**.
- **Longer Guide catch-up** — the guide now keeps up to **7 days** of just-aired programmes (was ~2 days), so
  you can browse and replay further back when your provider records that long and its EPG feed supplies it.
- **Clearer audio-track icon** — the player's audio-track button is now a music note, so it's no longer
  easily confused with the volume button.

### 🐛 Bug fixes

- **Audio & subtitle selection now works on Live TV** — the ExoPlayer live engine wasn't exposing any
  tracks, so multi-language live channels (and a dual-audio file added via an **M3U** playlist, which
  imports as a live channel) showed **"No tracks available."** Live now enumerates **audio** and
  **subtitle** tracks: the HUD's Audio/Subtitle menus list them with language labels and switch them on
  the fly, and a selected subtitle renders on screen (the overlay mounts only while subtitles are on, so
  4K live keeps its direct hardware-overlay path).
- **No more silent playback for AC3/DTS files played as live** — a movie file with **AC3 / E-AC3 / DTS**
  audio (e.g. a dual-audio rip added via an M3U playlist, which imports as a live channel) played **video
  with no sound** on devices whose hardware can't decode those codecs, because the live ExoPlayer engine
  relies on the device's audio decoders. Such streams now **automatically fall back to the mpv engine**
  (which decodes them in software), so they play **with sound** — and on hardware that *can* decode the
  codec, playback stays on the fast ExoPlayer engine as before.
- **Live audio no longer keeps playing after you exit/log out** — a **live channel** plays on the ExoPlayer
  engine, but leaving the app only stopped the mpv player, so the live stream's **audio kept playing in the
  background**. Exiting/backgrounding now stops **both** engines.
- **Clearer error for an unplayable movie** — when a movie/episode can't be decoded, the player showed the
  *catch-up* "recording/archive" error text; it now shows a video-appropriate message (only real catch-up
  recordings use the archive wording).
- **Playback errors now show the real reason** — the error screen now lays the failure out in three parts so
  the actual cause is visible **without adb/logcat**: a **plain-English reason**, the **media spec** (codec •
  resolution • decoder, e.g. `HEVC 3840×1920 • hardware decoder`), and the **raw** engine line. It surfaces,
  in order of usefulness:
  the **hardware codec / audio error** (Android MediaCodec/AudioTrack — e.g. the cryptic `0x80001000` is shown
  as *"video decoder error — the TV's hardware decoder is busy or can't handle this stream [MediaCodec: …]"*),
  the **network/format** reason from mpv (`http: HTTP error 400`, `unrecognized file format`), or the
  **ExoPlayer** code for live (`ERROR_CODE_DECODING_FORMAT_UNSUPPORTED`). On live, codec/audio failures are
  read **programmatically** from ExoPlayer (reliable across devices, no logcat needed). Common cryptic cases
  are translated to plain English — e.g. **HTTP 509** → "Provider blocked — too many streams at once", **403**
  → "Provider denied access", an expired **SSL** certificate, out-of-memory, and unsupported codec profiles.
  Works for video **and** audio failures, on movies, series and Live TV — turning "guess and rebuild" into
  "read the line."

## v3.1.2 — 2026-06-21

### 🐛 Bug fixes

- **Surround sound is now off by default (opt-in), with a safety net** — v3.1.1's multichannel-LPCM surround
  (on by default) broke playback on some TVs that *claim* 5.1 over HDMI but mis-play it: series with
  multichannel (Dolby/DTS) audio played at **double speed with no sound** (movies/live were fine). Surround
  is now **off by default** — leave it off on TV speakers / stereo soundbars (clean stereo), turn it **on**
  for a real 5.1/7.1 receiver. When on, OwnTV pins a widely-compatible **16-bit / 48 kHz** output and, if it
  still detects that double-speed/no-sound runaway, **auto-switches that session to stereo** so playback
  never breaks. (#25)
- **Live TV recovers from connection drops** — if a live channel froze mid-watch (a brief Wi-Fi/provider
  hiccup), it used to stay stuck until you backed out and re-opened it. Live now **auto-reconnects** from the
  live edge after a drop or stall, retrying with back-off; if it still can't recover, the on-screen **Retry**
  takes over.
- **Screen no longer sleeps during Live TV** — because live plays on the ExoPlayer engine, the TV
  screensaver could start mid-channel; the screen is now held awake while watching live (full-screen and
  PiP), just as it already was for movies and series.

## v3.1.1 — 2026-06-21

### ✨ New features

- **Near-instant Live TV (two playback engines)** — live channels now play on a dedicated **ExoPlayer**
  engine: the channel-list **preview** comes up almost instantly as you scroll, and pressing **OK promotes
  that same stream straight to full-screen** with no reload — so opening a channel and **zapping** (CH± /
  D-pad) are immediate, especially on HLS/M3U. The robust **mpv** engine still runs **all movies & series**
  (4K/HDR direct path, broad stream compatibility) and automatically backs up any live stream ExoPlayer
  can't open. Live PiP/dock works on either engine.
- **Import a playlist from a local file** — adding an **M3U / M3U8** source now has a **"Choose a local
  file"** button that opens an in-app, TV-friendly file browser, so you can load a `.m3u`/`.m3u8` saved on
  the device (USB drive, Downloads, etc.) instead of a URL. The file is re-read on each refresh. (#24)

### 🔧 Changes

- **EPG is now opt-in** — adding a playlist **no longer auto-downloads its guide** (that could make every
  import slow). Add a guide when you want it via **Settings → EPG sources**, where the form **pre-fills the
  playlist's own guide URL** (Xtream `xmltv.php` / M3U `url-tvg`) — so it's still one step, just on demand.

### 🐛 Bug fixes

- **Surround sound no longer stutters video** — the v3.1.0 *Surround passthrough* toggle bit-streamed raw
  Dolby/DTS to the TV/receiver, but on some TVs (e.g. Realtek) the passthrough audio path returns no
  timing to the player, which starved the video into a **1–2 fps slideshow** on Dolby/DTS titles (most
  noticeable on 4K). The setting is now simply **Settings → Surround sound** (on by default): OwnTV
  **decodes** Dolby/DTS to **multichannel LPCM (5.1/7.1)** over HDMI, so your TV or AV receiver still gets
  surround **and** the picture stays smooth on the fast 4K/HDR path. Turn it off for a stereo downmix.
  (Raw bitstream passthrough has been removed.)
- **M3U live channels that wouldn't play now work** — after v3.1.0's faster channel-zapping, some live
  channels from a plain **M3U/HLS** playlist could hang on a black screen (the trimmed startup probe
  couldn't open those streams), while Xtream live was unaffected. OwnTV now uses the full probe for
  HLS/non-TS live (as it did before), and keeps the fast trimmed probe for direct **MPEG-TS** (`.ts`) live
  — so M3U live plays again *and* TS zapping stays quick.
- **4K channel zapping no longer hangs** — switching between **4K** channels with the D-pad / CH± in
  full-screen could freeze the picture until you backed out and re-entered. The player now starts each
  4K-class channel on a fresh video surface, so zapping plays cleanly (a TV-decoder quirk on back-to-back
  4K decodes).
- **Episodes now appear for every Xtream series** — some providers return a series' episode data in a
  different JSON shape, which OwnTV didn't read, so those shows opened with **no episodes** (they worked in
  other apps). The parser now handles both shapes, so episodes populate. (#23)
- **Global search opens the right series** — picking a series from the **main search** now opens that
  show's **episode list** directly, instead of just jumping to the Series tab.

## v3.1.0 — 2026-06-20

### ✨ New features

- **Catch-up straight from Live TV** — focus a catch-up channel in **Live TV** and the preview now has a
  **Catch-up** button: it opens a simple list of recent programmes — pick one and it **replays from the
  start**. No more hunting through the Guide timeline. (The Guide still works for browsing too.)
- **Hide/show a whole range of categories at once** — in **Settings → Customize**, long-press a category's
  Show/Hide button to start a span, then press Show/Hide on another category to select everything in
  between and hide or show it all in one go — a big time-saver for providers with hundreds of categories.
  (by @dan-maloney, #20)
- **Auto-play next episode** — when an episode finishes, OwnTV automatically starts the next one, and
  **rolls into the next season** after a season's last episode — great for binge-watching. There's a new
  **Settings → Auto-play next episode** toggle (on by default) for anyone who prefers manual playback. (#21)
- **Series open on your last-watched episode** — reopening a show now jumps straight to the episode you
  last watched (correct season, scrolled into view and focused) instead of always starting at episode 1,
  and that episode is tagged **"Last watched"** so it's easy to spot. (#22)
- **Surround sound passthrough** — a new **Settings → Surround passthrough** toggle sends **Dolby
  (AC-3/E-AC-3, incl. Atmos) and DTS** audio straight to your TV or AV receiver to decode, instead of
  mixing down to stereo. OwnTV only passes through the formats your audio output reports it can handle,
  and you can switch it off if a stream goes silent. (Off by default.)

### 🐛 Bug fixes

- **Faster channel zapping** — live channels and HLS streams now start with a **trimmed stream probe**,
  so the picture comes up noticeably quicker when switching channels. If a trimmed probe ever misses a
  stream's audio (rare, on sparse feeds), OwnTV automatically **re-probes that channel in full** so it
  still plays with sound. On-demand movies/series keep the full probe for rock-solid HDR/audio detection.
- **Live channels that dropped out every few seconds now play continuously** — some live servers close the
  connection on a schedule (common with 4K feeds); OwnTV now **reconnects automatically at the stream level**
  and keeps playing, instead of stalling and re-buffering on a loop.
- **Smoother video on TVs** — the player now asks the display to **match the video's frame rate** (e.g.
  switch a 60 Hz panel to 24/48 Hz for 24fps content). On TVs that support it, this removes the subtle
  *judder* of film-rate content on a fixed 60 Hz screen (the "looks slightly slow/uneven, but not
  buffering" feel). No effect on panels that can't switch — it just stays as-is.
- **Installs on non-TV devices now** — OwnTV required the Android **TV (leanback)** feature, so it
  wouldn't install on plain phones / non-TV boxes (incl. some armv7a Android 11 devices) and showed
  **no launcher icon** on phones. It's now installable on regular Android too, with a normal home-screen
  icon — while still appearing in the TV launcher on Android TV. (Also resolves #16.)
- **EPG sources that failed with a "protocol error" now load** — some EPG/host CDNs have flaky HTTP/2
  and would reset large downloads (e.g. a big US guide) with *"stream was reset: PROTOCOL_ERROR"*.
  OwnTV now uses HTTP/1.1 for its downloads, which those servers handle reliably. (#17)
- **Image-based subtitles now play smoothly** — text subtitles (SRT/ASS) display on the fast HDR path as
  before. **Image-based** subtitles (PGS/VOBSUB/DVB) on **movies & series** now display *without* slowing
  the video down: picking one seamlessly hands that title to a second engine (ExoPlayer) that keeps the
  picture on the same zero-copy/HDR path and draws the bitmap subtitle on its own layer — no more stutter,
  and still only **one** connection to your provider. (The old approach composited inside the video and
  could make 4K/HDR unwatchable on TV hardware — that's gone.) Image tracks are tagged **"image"** in the
  picker; turning subtitles off or choosing a text track hands straight back. If a title's audio is a
  format the second engine can't play (e.g. DTS), it stays on the main engine and tells you. (Image
  subtitles aren't shown on live channels, where they're virtually never present.)
- **Big-library import no longer gets stuck** — the per-category fallback (for providers that truncate
  the bulk movie/series list, #15) used to make the import counter look like it was *restarting* each
  category, and on panels that **ignore the category filter** it could loop forever re-fetching the same
  list. Progress now climbs **continuously** across the whole import, and the fallback **stops** when the
  provider clearly isn't honoring per-category requests (keeping everything fetched so far). (#15)

## v3.0.0 — 2026-06-17

*Big release — bundling the open feature requests + Catch-up TV.*

> 💬 **Join us on Telegram** — **Settings → About** now shows the OwnTV **Telegram group** link with a
> **QR code** you can scan from your phone to join the community (also added to the README).

### ✨ New features

- **Browse the TV Guide timeline** — navigating the guide is now two-stage: press **Right** on a channel
  to select its **whole programme row**, then **OK** to step in and move through programmes with
  **Left/Right** (the row scrolls with you). **OK** on a programme opens it (watch / *Watch from start*
  for catch-up), and **Up/Down** jumps to the next channel at the same time. **Back** steps back out.
- **Catch-up TV (archive)** — for providers that offer it, the TV Guide now lets you **watch programmes
  that already aired**. When you have catch-up channels, the guide extends **back in time** (up to ~2
  days, depending on your EPG) — scroll **left** to reach earlier programmes, open one and pick **Watch
  from start** to replay it from the archive (seekable, with a progress bar). The guide opens at *now*,
  with past shows to the left. Works with Xtream (`tv_archive`) and M3U playlists with `catchup` tags.
  If catch-up plays the wrong programme, **Settings → Playback → Catch-up time** lets you set the
  timezone it uses — your **device's**, or a **manual UTC offset** (UTC−12…+14) — that your provider needs.
- **Auto-match your channels to the guide** — the TV Guide has a new **Auto-match EPG** button that
  links channels whose tvg-id is missing or doesn't line up with your EPG feed by matching them **by
  name** (ignoring HD/country tags etc.). Confident matches are applied automatically; the rest are
  shown in a quick **review** list to accept or skip (with **Accept all** / **Skip all** shortcuts).
  Matches are saved per profile and survive re-syncing. (Fixes #13.)
- **Match a channel's EPG from the Guide** — **long-press a channel** in the TV Guide, then choose
  **Auto-match** (match just that channel by name) or **Pick manually** (choose its guide channel from
  the full list, or clear the override). The choice is saved per profile and survives re-syncing. (Fixes #10.)
- **See what's coming up in Live TV** — the channel info overlay now shows a **"Later"** row with the
  next few programmes after *Now/Next*, so you can see the upcoming schedule without opening the Guide.
  (Fixes #11.)
- **Change channels with the D-pad** — while watching a channel fullscreen with the controls
  hidden, **D-pad Up/Down** — plus the **media ⏮/⏭** keys and **CH+/CH−** — now switch channels, so
  remotes without dedicated channel buttons (e.g. Fire TV) can zap too. When the controls are showing,
  Up/Down navigate them as before. Zapping also **wraps around** — past the last channel it loops to the
  first (and vice-versa) instead of dead-ending. (Fixes #9.)
- **Sort the TV Guide** — the Guide has its own **sort** button: **A–Z**, **Provider** order, **Live TV**
  (mirrors your Live TV sort), or **Catch-up** (channels with archive first, so you can find them fast).
  (Fixes #12.)
- **See a channel's real resolution before you watch** — the Live TV preview now shows the **actual
  stream resolution** (e.g. `1080p`, `720p`, `4K`) as a badge on the preview, so a channel named
  "…4K" that's really 1080p no longer fools you.

### 🐛 Bug fixes

- **New playlists show up immediately** — after deleting a playlist and adding another, Live TV / Movies /
  Series now refresh **right away** instead of staying empty until you restarted the app.
- **Huge playlists import fully again** — some Xtream panels cut off very large movie/series lists
  mid-download, which aborted the whole import with an *"Unterminated string…"* error and left you
  unable to sign in. Now, if the bulk list truncates, OwnTV automatically **fetches it category by
  category** (small requests the server can handle) so you get your **full library** — and items keep
  populating as it goes. (Fixes #15.)
- **Faster channel switching in Live TV** — switching channels no longer feels slow or briefly "broken".
  The player now recognises that the *previous* stream's cleanup isn't the *new* stream failing, so it
  skips the needless retries/backoff (and the occasional false "Couldn't play this stream" flash) that
  could delay the preview. The Live preview pane also shows a **loading spinner** while a stream is
  opening. *(Thanks to **[@codeVerine](https://github.com/codeVerine)** — PR #14.)*
- **Left from the channel list returns to your category** — pressing **Left** into the category rail now
  lands on the folder you're actually in (e.g. the current channel's category) instead of jumping to the
  search box at the top. The category search is still there — press **Up** from the top category to reach it.
- **"Now watching" card shows the right channel** — the channel info card no longer keeps the *previous*
  channel's name after a quick zap; it updates the instant the stream changes. (#9)

## v2.2.4 — 2026-06-14

- **Back from a series returns to the right poster** — pressing **Back** inside a series (or its
  on-screen back button) now puts focus back on the **series you opened** in the grid instead of jumping
  to the sidebar (it now scrolls to and focuses it, matching how Movies already behaves).
- **No more sidebar flicker in Settings** — moving between a Settings sub-screen (Playlists, EPG,
  About…) and the Settings menu no longer makes the left rail briefly expand and collapse; it only
  expands once focus actually settles on it. (The sidebar is shared, so this covers every section.)
- **…and no category-rail flicker** — the same settle-before-expand fix now applies to the **category
  rail** (Live TV / Movies / Series), so it no longer briefly widens then collapses when focus passes
  through it during a screen transition.

## v2.2.3 — 2026-06-14

> 🔁 **Please re-sync your playlists after updating.** This release switches live channels to the more
> widely-supported **MPEG-TS** stream format — but each channel's link is built when you sync, so your
> existing channels keep the old format until you re-sync. Open **Settings → Playlists** and press
> **Re-sync** on each one so every channel picks up the change.

- **Channels that wouldn't load now play** — live streams use the universal **MPEG-TS (`.ts`)** endpoint
  instead of HLS (`.m3u8`); some Xtream providers only serve raw MPEG-TS and don't offer the `.m3u8`
  wrapper, so their channels failed to load entirely. And if a `.ts` channel still won't start, the
  player now **automatically falls back to the `.m3u8` variant** before erroring — so the rare HLS-only
  panel keeps working too.
- **Back hides the player controls first** — while watching, when the player UI is showing, **Back** now
  just hides it instead of leaving the channel; press **Back** again (with the controls hidden) to exit
  the player.
- **Smarter playback retries** — when a stream stalls, the silent auto-retry now uses **exponential
  backoff** (1s · 2s · 4s) to better ride out cold-boot decoder lag, **skips retrying when you're
  offline** (shows a "No internet" message immediately instead of spinning), and **fails faster on
  movies/episodes** — a bad VOD link errors after one try instead of three.
- **Channel zapping from the Guide** — the **CH+ / CH−** keys now surf channels while watching a channel
  opened from the **TV Guide**, stepping through the guide's channel list — just like from the Live TV
  list.

## v2.2.2 — 2026-06-14

- **Category rail highlight follows your focus** — the rail no longer keeps your current category lit
  up when you're not on it (while you're on the sidebar, on the new category-search box, or arrowing
  past other categories). Now only the pill you're focused on is highlighted, and your active category
  turns green the moment you land on it — so there's always exactly one highlight, right where the
  remote is.

## v2.2.1 — 2026-06-14

- **Search your categories** — the category rail (Live TV / Movies / Series) now has a **search box**
  at the top. Opening the rail lands right on it, so you can **type to filter** hundreds of categories
  by name and jump straight to the one you want instead of scrolling; **Down** drops into the list. The
  filter clears when you leave the rail.

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
