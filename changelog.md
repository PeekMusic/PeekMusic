# Changelog

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
  login header fixed, in-app updater checks DripMusic releases
- Branding: Wrapped feature and playlist covers rebranded to DripMusic, new app
  icon with proper themed-icon (monochrome) support, settings entry for lyrics
  translation renamed to plain "Translation" in all locales

## v0.1.0 (pre-release)

First DripMusic release. Fork changes on top of upstream Metrolist v13.6.3:

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

## v13.6.3-drip.1

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
- Repo: rebrand to DripMusic (README, fastlane en-US/de-DE)

Upstream Metrolist history remains available in the git history of this repository.
