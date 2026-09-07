<div align="center">

<img src="assets/logo.png" alt="DripMusic logo" width="160" />

# DripMusic

### A YouTube Music client for Android that only keeps what you actually use — a fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist)

</div>

DripMusic is a personal fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist),
a third-party YouTube Music client. The idea is simple: **less noise, more convenience.**
Everything that gets in the way — features nobody asked for, clutter in the settings,
janky animations — gets removed or reworked. Everything you touch every day — the home
screen, lyrics, the player — gets extra polish.

## What's different (and why it's better)

**A home screen you actually control**
- Reorder *all* sections — including the YouTube-generated ones — with drag & drop, and hide any section you don't want to see. Hiding a category (like "Similar to") hides all of its instances.
- Optional shuffle ordering for a fresh layout on every start.
- App-generated sections (quick picks, forgotten favorites, …) are separate from YouTube's content: off by default when you're logged in, on when you're not.

**Smoother where it matters**
- Home screen and player animations reworked (rendering off the main thread, fewer database observers, draw-phase animation progress)
- Configurable queue pre-caching: buffer up to 8 upcoming songs while you listen, so skips are instant.
- Crossfade follows an equal-power curve — no more audible volume dips between songs.
- Search stays out of your way: at most 4 autocomplete suggestions, then straight to music results.

**Lyrics, taken seriously**
- More providers: SimpMusic and Musixmatch joined the existing ones (BetterLyrics, Paxsenix, LrcLib, KuGou, LyricsPlus, YouTube).
- Auto-pick best provider: the app finds the best synced lyrics per song on its own, preferring word-by-word (karaoke-style) results. Prefer a specific provider? Reorder or search manually — results are grouped by source.
- Free, keyless lyric translation — no API keys, no accounts. (Rate Limits may apply). Mixed-language songs translate completely, identical lines are never shown twice. 
- Full cache control: clear translation or lyrics caches any time in Settings → Storage.
- **Lyrics peek**: the active line lives right in the player, between the cover and the title — with karaoke highlighting, romanization and a ring that fills during instrumental passages. One tap opens the full lyrics view; disable it in Settings → Songtexte if you prefer a clean player.

**A home feed that stays configured**
- Sections are matched against fixed categories, so hiding one (e.g. "Similar to") really hides all of them — no surprises when more content loads.
- No endless scrolling: the feed stays exactly as you arranged it.
- Section options are only available while logged in (they only affect the YouTube feed anyway).

**A settings menu that makes sense**
- Options grouped into focused pages instead of endless scrolling (Player design, Audio, Queue, Sleep & Alarm, Lyrics, Home screen, …).
- Android Auto: choose whether recommendations come from your YouTube home feed or local suggestions.

## What's been removed

Things this fork deliberately doesn't carry:

- **Discord and Last.fm integrations** — and their settings pages.
- **YouTube channel switching** — one account at a time; sign out and back in to change.
- **AI lyric translation behind API keys** — replaced by the free keyless translation above.
- **Zemer and YouTube Subtitle lyrics providers** — replaced by SimpMusic and Musixmatch.

## Build

Debug build (for development):

```bash
./gradlew :app:assembleFossDebug
```

Release build (optimized, installable next to the debug build):

```bash
STORE_PASSWORD=<store password> KEY_ALIAS=<key alias> KEY_PASSWORD=<key password> \
  ./gradlew :app:assembleFossRelease
```

The release signing config expects a keystore at `app/keystore/release.keystore`
(`*.keystore` is gitignored). Without the environment variables, the build produces
an unsigned APK as usual.

## Credits & License

- Based on [Metrolist](https://github.com/MetrolistGroup/Metrolist) by
  [MetrolistGroup](https://github.com/MetrolistGroup) — all credit for the app goes
  to them and the upstream contributors.
- Licensed under [GPL-3.0](LICENSE), same as upstream.

## Disclaimer

This project is not affiliated with YouTube or Google. If YouTube Music is
unavailable in your region, the app will not work without a VPN or proxy connecting
to a supported region.
