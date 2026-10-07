**✨ UI & Quality of Life**
- **Quick Download Button**: The download action has been moved out of the 3-dot overflow menu and placed directly into the header of Playlists and Albums next to the Like and Play buttons! You can now download entire playlists with a single tap, track progress with an inline spinner, or tap again to remove downloads.
- **Persistent Lyrics Search**: Songs that previously couldn't find lyrics are no longer permanently stuck as "Not Found" in the local database. PeekMusic will now automatically retry searching on subsequent plays so you don't miss out on newly synced lyrics.
- **Listen Together Fixes**: Fixed a bug where creating a room would get stuck infinitely on "Creating room...". Added proper error notifications and restored room creation compatibility with the server.

**⚡ Performance & Under the Hood**
- **Widget Hitching Eliminated**: Moved homescreen widget progress updates off the main UI thread to prevent periodic frame drops and micro-stuttering.
- **Automated Version Tracking**: Replaced static build codes with automated commit-based versioning to ensure seamless in-app updater recognition and installation.
- **Upstream Sync (Metrolist)**:
  - Bumped InnerTubeX to v0.7.4 for improved YouTube stream handling and metadata parsing.
  - Preserved shuffle order across crossfade transitions and player rebuilds.
  - Restored full media notification controls for Android 17.
  - Faster, unthrottled range requests for downloads and offline track duration lookups.
  - Multiple translation additions and minor UI polish.
