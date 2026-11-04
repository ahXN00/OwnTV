# OwnTV TMDB caching proxy (Cloudflare Worker)

A tiny Cloudflare Worker that proxies TMDB `/3/...` API calls, injects a server-side TMDB API
key, and edge-caches the JSON responses so a large user base costs only a handful of real TMDB
calls. This is the same proxy logic OwnTV's built-in default metadata server runs.

You can run your **own free copy in about 10 minutes** and point OwnTV at it — no programming
needed. A personal copy serves only your own devices, so it needs none of the abuse-protection
the public deployment carries (see [`../README.md`](../README.md)).

## What you need (both free)

1. A **TMDB API key**: create an account at [themoviedb.org](https://www.themoviedb.org/signup),
   then go to [Settings → API](https://www.themoviedb.org/settings/api) and request a key
   (choose "Developer"; it's free for personal use and issued instantly). Copy the
   **"API Key" (v3 auth)** value.
2. A **Cloudflare account**: sign up at [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up)
   (free plan is plenty — no credit card, no domain needed).

## 🟢 Easy way — everything in the browser (no tools to install)

1. Log in to the [Cloudflare dashboard](https://dash.cloudflare.com).
2. In the left menu choose **Workers & Pages**, then click **Create** → **Create Worker**.
3. Give it any name you like (e.g. `my-owntv-meta`) and click **Deploy** — this creates a
   "Hello World" worker; you'll replace its code next.
4. Click **Edit code**. Delete everything in the editor, then open
   [`index.js`](index.js) from this folder, copy **all** of it, paste it into the editor,
   and click **Deploy** (top right).
5. Now add your TMDB key as a secret: go back to the worker's page → **Settings** →
   **Variables and Secrets** → **Add** → type = **Secret**, name = `TMDB_KEY` (exactly like
   that), value = your TMDB API key → **Deploy** / **Save**.
6. Find your worker's address on its overview page — it looks like
   `https://my-owntv-meta.<your-subdomain>.workers.dev`.
7. **Test it** in any browser — open:

   `https://my-owntv-meta.<your-subdomain>.workers.dev/3/search/movie?query=Oppenheimer`

   Then confirm the two Trending endpoints used by Home:

   `https://my-owntv-meta.<your-subdomain>.workers.dev/3/trending/movie/day`

   `https://my-owntv-meta.<your-subdomain>.workers.dev/3/trending/tv/day`

   If you see a wall of movie data (JSON), it works. If you see an error, re-check step 5
   (the secret must be named `TMDB_KEY`).
8. In OwnTV on your TV: **Settings → Content & metadata → Metadata (TMDB) → Get advanced TMDB info** →
   enter your worker address as **Server** (without any `/3/...` part) → **Test lookup** should succeed.

That's it. Your TMDB key never leaves your Cloudflare account, and repeated lookups are served
from Cloudflare's cache without hitting TMDB at all.

## 🛠️ Developer way — wrangler CLI

```sh
npm install -g wrangler
wrangler login
wrangler deploy              # run from this folder
wrangler secret put TMDB_KEY # paste your TMDB v3 key when prompted
```

The deployed URL is printed by `wrangler deploy`; test and configure it as in steps 7–8 above.

## Files in this folder

| File | What it is |
|---|---|
| [`index.js`](index.js) | The whole server — one file. This is what you paste in the browser editor. |
| [`wrangler.toml`](wrangler.toml) | Config for the CLI way only (name, entry file). Ignore it if you used the browser. |

## Notes

- Free-plan limits (~100k Worker requests/day) are far more than enough for personal use. Ordinary
  search/details responses are fresh for 30 days; fast-changing Trending responses are fresh for 15 minutes.
- Equivalent query strings share one cache entry even when their parameter order differs. A caller-supplied
  `api_key` is discarded, so it never changes authentication or cache identity.
- TMDB calls time out after eight seconds and retry once for transport errors, rate limits and temporary
  upstream failures. If TMDB is unavailable, a previously cached metadata response may be served for up to
  90 days and a Trending response for up to 24 hours instead of making Home fail empty.
- Only GET requests to `/3/...` are proxied; everything else is rejected.
- Upstream errors are never cached. JSON errors and response headers include a request id plus cache state
  (`HIT`, `MISS`, or `STALE`) to make self-hosted troubleshooting easier without exposing the TMDB key.
- Images are **not** proxied — OwnTV loads poster/backdrop art directly from `image.tmdb.org`,
  which needs no key. The Worker only ever sees small JSON lookups.

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB.
