**✨ UI & Quality of Life**
- **Quick Download Button**: The download action has been moved out of the 3-dot overflow menu and placed directly into the header of Playlists and Albums next to the Like and Play buttons! You can now download entire playlists with a single tap, track progress with an inline spinner, or tap again to remove downloads.
- **Listen Together Fixes**: Fixed a bug where creating a room would get stuck infinitely on "Creating room...". Added proper error notifications and restored room creation compatibility with the server.

**🎤 Lyrics Improvements & Fixes**
- **Persistent Search & Auto-Retry**: If a track didn't have lyrics when first played, PeekMusic previously remembered that failure permanently in the local database. Now, it will dynamically retry searching on future plays so you don't miss out when lyrics get added.
- **Provider Reliability & Extended Timeout**: Increased the concurrent lyrics search timeout to 6 seconds, giving community providers (like BetterLyrics, Paxsenix, etc.) adequate time to return rich word-synced timestamps even on slower mobile networks.
- **Consolidated Lyrics Prefetching**: Cleaned up duplicate background lyrics fetchers between the player UI and playback service, eliminating redundant network requests and race conditions while keeping zero-wait prefetching silky smooth.
- **Joined Indic Script Rendering**: Upstream fix ensuring joined Indic scripts (Hindi, Bengali, etc.) are rendered properly as cohesive lyric lines.

**⚡ Performance & Under the Hood**
- **Widget Hitching Eliminated**: Moved homescreen widget progress updates off the main UI thread to prevent periodic frame drops and micro-stuttering.
- **Automated Version Tracking**: Replaced static build codes with automated commit-based versioning to ensure seamless in-app updater recognition and installation.
- **Upstream Sync (Metrolist)**:
  - Bumped InnerTubeX to v0.7.4 for improved YouTube stream handling and metadata parsing.
  - Preserved shuffle order across crossfade transitions and player rebuilds.
  - Restored full media notification controls for Android 17.
  - Faster, unthrottled range requests for downloads and offline track duration lookups.
  - Multiple translation additions and minor UI polish.
