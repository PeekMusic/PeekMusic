<div align="center">

<img src="assets/logo.png" alt="PeekMusic logo" width="160" />

# PeekMusic

### A YouTube Music client for Android that only keeps what you actually use — a fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist)

</div>

PeekMusic is a personal fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist),
a third-party YouTube Music client. The idea is simple: **less noise, more convenience.**
Everything that gets in the way — features nobody asked for, clutter in the settings,
janky animations — gets removed or reworked. Everything you touch every day — the home
screen, lyrics, the player — gets extra polish.

## Features

- Stream and download music from YouTube Music
- Background playback with full media notification controls
- Lyrics — synced, word-by-word karaoke, romanization, and translation
- Offline library: local songs, albums, playlists, and artists
- Android Auto support
- Material You theming with dynamic color
- Home feed with YouTube recommendations
- Smart queue and radio generation
- Sleep timer and alarm
- Multiple lyrics providers (SimpMusic, Musixmatch, and more)
- Widget support (4×2 and 4×3 player widgets)
- Foldable and large-screen adaptive layout

## What makes PeekMusic different

**Lyrics Peek** — The active lyric line lives between the album art and the song title, so you always know what's being sung without switching screens. It does karaoke word-highlighting, shows adlibs, and has an Apple-style flowing gradient.

**Block Artists** — Block any artist from the artist page or player menu. Blocked tracks are filtered from queues, mixes, and radios automatically. Manage your list under Settings → Blocked Artists.

**Translation in the player** — A translate button sits right in the player. One tap to see the full translation, another to dismiss it — no menus needed.

**Cleaner settings** — Dozens of switches nobody touches were removed and hardcoded to sensible defaults. What's left is reorganized into focused pages: Player, Audio, Queue, Sleep & Alarm, Lyrics, and Home.

**Fresher home screen** — Quick Picks shuffles its order on every visit and mixes in songs from your forgotten local favorites.

**First-launch onboarding** — A 7-step introduction walks you through theme, player design, lyrics, playback, and more before you even play your first song.

## Screenshots

<div align="center">

<table>
  <tr>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_01.jpg" width="200" /></td>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_02.jpg" width="200" /></td>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_03.jpg" width="200" /></td>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_04.jpg" width="200" /></td>
  </tr>
  <tr>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_05.jpg" width="200" /></td>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_06.jpg" width="200" /></td>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_07.jpg" width="200" /></td>
    <td><img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_08.jpg" width="200" /></td>
  </tr>
</table>

</div>

## What's been removed

Things this fork deliberately doesn't carry:

- **Discord and Last.fm integrations** — and their settings pages.
- **YouTube channel switching** — one account at a time; sign out and back in to change.
- **AI lyric translation behind API keys** — replaced by the free keyless translation above.
- **Zemer and YouTube Subtitle lyrics providers** — replaced by SimpMusic and Musixmatch.

## 🌐 Help Translate PeekMusic

PeekMusic is continuously being translated by our awesome community. You can help translate the app into your native language!

We manage our translations via Crowdin. Join our project and start translating:
**[Contribute to PeekMusic on Crowdin](https://crowdin.com/project/peekmusic)**

*(If your language isn't listed, request it directly on Crowdin!)*

Many translations were originally contributed by the [Metrolist community](https://github.com/MetrolistGroup/Metrolist) — huge thanks to all the volunteers who made that possible. 🙏

### Top Contributors

<!-- CROWDIN-CONTRIBUTORS-START -->
<table>
  <tbody></tbody>
</table>
<!-- CROWDIN-CONTRIBUTORS-END -->

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
