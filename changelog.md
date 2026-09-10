# Changelog

## v1.0.1

- Added an active state indicator to the Lyrics button in the legacy player design.
- Removed excessive spaces in Japanese romanization for better readability.

## v1.0.0

- Rebranded the entire app to **PeekMusic**
- Unified Playlist Header Controls: Placed the Shuffle button consistently between the Play and Menu buttons across all playlist/album views.
- First-launch onboarding: a 7-step introduction with live previews for theme,
  player design, lyrics peek, playback, smart queue/caching, content filters,
  and update checks; optional YouTube sign-in or guest mode

## v0.6.0

- Player lyrics peek: supports lyrics offset, translation and romanization, and
  shows refresh/search actions when lyrics are not found; the previous line now
  stays visible while the instrumental gap indicator is running
- Lyrics fixes: SimpMusic rich-sync word-end timing corrected and a translation
  composition lifecycle bug resolved
- Queue: added an option to exclude recently played songs from generated mixes
  and radios, with a configurable window (1–100 tracks); duplicate prevention now
  also covers the auto-load-more path for mixes and radios
- Home screen: the speed-dial grid is always visible, even when logged in; the
  separate "App home sections" master toggle is removed, while the remaining
  internal sections stay logged-out only; home section order and shuffle are now
  in one settings screen and the order list is only editable while logged in and
  shuffle is off

## v0.5.1

- Fix: the update badge (red dot on the profile picture) no longer shows on the
  latest release — the version check now compares against the release tag
  instead of the release title

## v0.5.0

- Lyrics peek everywhere: in landscape (folded and unfolded) the peek line and
  the controls now sit as one unit, vertically centered next to the cover; on
  wide portrait screens (unfolded) the peek lives in the right-hand column
  instead of overlaying the cover
- The player stays open through display changes — folding, unfolding and
  rotating no longer collapse it back to the mini player
- Player design: button color now defaults to the dynamic primary color and
  the squiggly slider is the default
- Android Auto: selecting a song under Songs now starts a Mix seeded from that
  song, bound to the auto-radio switch; when the mix can't load it falls back
  to the plain song list
- Merged upstream v13.7.0: playback and resource regression fixes, liked
  playlist sync, account info loading, unified update prompt, translations

## v0.4.0

- Lyrics peek in the player: the currently active line now lives right between
  the cover and the title — no more flipping to the lyrics page just to see
  what's being sung. It follows your lyrics animation style, does word-by-word
  karaoke when timings are available, shows romanization underneath, and a tap
  opens the full lyrics view. While the search runs you get the same loading
  spinner as the lyrics page, and during long instrumental passages (or before
  the first line) a ring fills up until the lyrics return. Can be turned off in
  Settings → Songtexte
- Home screen: YouTube sections are now matched against a fixed list of known
  categories, so hidden sections stay hidden; endless scrolling is disabled to
  keep the feed exactly what you configured
- Search: autocomplete shows at most 4 suggestions before jumping straight to
  music results
- Settings: home screen section options are only available while logged in
  (they only affect the YouTube feed anyway) and say so when disabled
- Fixes: word-by-word lyrics from richsync providers no longer lose their
  spaces between words

## v0.2.0

- Free lyrics translation: replaced the AI translation (OpenRouter/DeepL API
  keys required) with a keyless Google Translate backend. One tap in the lyrics
  menu translates the whole song in a single request; results are cached in
  memory and stored in the database per song. Settings → Songtexte → Übersetzung
  picks the target language
- Settings restructure: the overloaded pages were split into focused ones —
  Player (design), Audio (playback, crossfade, sleep timer moved out), Queue,
  Sleep & Alarm (alarms + sleep timer), Lyrics (display, providers, romanization,
  translation in one place) and Home (sections + auto playlists)
- Home screen sections: reorder via drag & drop, hide categories with toggles
  (replacing the +/- buttons), section order entry is disabled while shuffle
  ordering is active, and the list no longer animates into the custom order on
  every visit
- Android Auto: choose the recommendations source — your YouTube home feed
  (default when logged in) or local suggestions; source-specific options are
  hidden where they don't apply
- Account & cleanup: account and sync options live in one place, YouTube channel
  switching removed (log out and back in instead), Discord and Last.fm
  integrations removed
- Fixes: update badge no longer shows permanently when no update exists, top bar
  avatar loads at app start instead of after opening the account dialog, oversized
  login header fixed, in-app updater checks PeekMusic releases
- Branding: Wrapped feature and playlist covers rebranded to PeekMusic, new app
  icon with proper themed-icon (monochrome) support, settings entry for lyrics
  translation renamed to plain "Translation" in all locales

## v0.1.0 (pre-release)

First PeekMusic release. Fork changes on top of upstream Metrolist v13.6.3:

- Home screen shows only YouTube content; app-internal sections (speed dial,
  quick picks, keep listening, forgotten favorites, daily discover) are disabled
- Home sections are grouped into 19 categories with a new settings screen
  (Settings → Home screen sections): drag-and-drop reordering, hide/restore
  whole categories; the menu on a home section title hides its category too
- Pull-to-refresh now loads the full feed (continuation pages), same as on cold
  start, and the home screen is revealed at once behind a short loading shimmer
  instead of sections popping in one by one
- Removed the toggles for YouTube home sections and endless scrolling
  (both permanently enabled)

## v13.6.3-peek.1

Fork changes on top of upstream Metrolist v13.6.3:

- Performance: widget rendering moved off the main thread; one Room flow per home
  section instead of per-item flows; player expand progress read in the draw phase;
  thumbnail loading resized (player artwork 1080px, ItemThumbnail respects caller sizes)
- Home screen: section toggles decoupled from the YouTube home fetch — the speed dial
  stays populated when YouTube sections are hidden; the master toggle now hides all
  YouTube recommendation sections (home rows, account mixes, similar-to, community
  playlists, moods & genres) including their network requests
- Build: release APKs are signed directly by Gradle when keystore environment
  variables are set
- Repo: rebrand to PeekMusic (README, fastlane en-US/de-DE)

Upstream Metrolist history remains available in the git history of this repository.
