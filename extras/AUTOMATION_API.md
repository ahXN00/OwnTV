# OwnTV Automation API

A small HTTP API on the TV, for a computer on the same home network: a home server, a script,
Home Assistant. It is **off by default** and only answers devices that were paired on the TV.

- **Where:** ⋯ More › Keep it safe › **Automation**. On a new TV, **Set up by Automation** on the
  first setup screen turns it on and shows the pairing PIN.
- **Port:** `8090`, plain HTTP, home network only (10/8, 172.16/12, 192.168/16, link-local). Tailscale
  addresses only when **Allow over Tailscale** is on.
- **Base path:** `/api/v1`. `GET /api/v1` lists every endpoint with its body.

## Pairing

1. On the TV, choose **Pair a device**. A 6-digit PIN shows for 3 minutes.
2. From the computer:

   ```sh
   curl -X POST http://<tv-ip>:8090/api/v1/pair \
        -H 'Content-Type: application/json' \
        -d '{"pin":"123456","name":"Home server"}'
   ```

3. The answer holds a token. Keep it secret: the TV stores only a hash and cannot show it again.
   Paired devices are listed on the Automation screen, where each can be removed.

Wrong PINs close pairing after a few tries; wrong tokens are rate limited.

## Every request

- `Authorization: Bearer <token>`
- Address the TV by its **IP address** (`http://192.168.1.20:8090/...`). Requests that name it by
  a hostname are refused, which stops a web page in a browser on the network from reaching it.
- Bodies are JSON (`Content-Type: application/json`).

| Status | Meaning |
| --- | --- |
| `200` / `201` / `202` | Done; `202` means it started (a sync, a channel tuning) |
| `400` | The body or a parameter is wrong; the answer says which |
| `401` | Missing or wrong token |
| `404` | No such playlist, channel, movie, … |
| `409` | Not possible right now, for example a private screen is open (below) |
| `429` | Too many requests or failed attempts; try again later |

## What it can do

| Area | Endpoints |
| --- | --- |
| Status | `GET status` |
| Playlists | `GET/POST playlists`, `GET/PATCH/DELETE playlists/{id}`, `POST/GET/DELETE playlists/{id}/sync` |
| TV guides | `GET/POST guide`, `GET/PATCH/DELETE guide/{id}`, `POST/DELETE guide/{id}/sync` |
| Live TV | `GET channels?q=`, `POST channels/{id}/play` |
| Movies and series | `GET movies?q=`, `POST movies/{id}/play`, `GET series?q=`, `GET series/{id}/episodes`, `POST series/{id}/open`, `POST episodes/{id}/play` |
| Player | `GET player`, `POST player/play-pause`, `player/stop`, `player/volume`, `player/audio`, `player/subtitles`, `player/keys` |
| Screen | `GET ui`, `POST ui/navigate` |
| Settings | `GET/PATCH settings` (the keys the API may change) |
| Profiles and setup | `GET/POST profiles`, `POST profiles/{id}/use`, `GET setup`, `POST setup/finish` |
| Maintenance | `POST cache/clear`, `GET logs/app`, `GET/DELETE logs/playback`, `GET logs/crash` |

Examples:

```sh
TV=http://192.168.1.20:8090/api/v1
AUTH="Authorization: Bearer $TOKEN"

# Add an M3U playlist; the import runs in the background (see GET status)
curl -X POST $TV/playlists -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"type":"m3u","name":"My playlist","url":"http://example.com/list.m3u"}'

# Find a channel, then tune to it
curl "$TV/channels?q=news" -H "$AUTH"
curl -X POST $TV/channels/42/play -H "$AUTH"
```

A channel's `id` stays the same when its playlist refreshes, as long as the channel keeps its name
and group, so it can be saved in a script.

## What it cannot do

- **Private screens.** While a PIN, login, profile, network, backup or pairing screen is open on the
  TV, key presses and screen reads are refused (`409`), so a paired device cannot type or read a PIN
  or a password.
- **Secrets.** Playlist and account passwords are never returned. Network settings (proxy, DNS) and
  metadata servers cannot be changed from the API.
- **PIN-locked profiles.** The API cannot open a profile that has a PIN.

## Running in the background

While Automation is on, a foreground service keeps the API listening (Android shows a small
notification), including after a restart of the TV. To tune a channel while another app is in front,
OwnTV needs Android's "display over other apps" permission. Android TV has no screen for it, so
grant it once with adb:

```sh
adb shell appops set tv.own.owntv SYSTEM_ALERT_WINDOW allow
```

Without it, playing from the background answers `409` and the TV stays where it is.

Tuning or opening a section wakes the TV first, as a remote button would: it ends a screensaver,
turns a sleeping screen on and, over HDMI-CEC, asks the TV set to turn on and switch to this input.

## Turning it off

Switch **Automation API** off. Nothing listens any more, and pairings stay until removed. **Remove
all devices** revokes every token.
