**🎧 Listen Together (PeekParty) Overhaul**
- **Friend-Only System**: We completely redesigned PeekParty. No more clunky room codes! You now add friends directly via unique friend codes, see who's online, and join their rooms with a single tap.
- **Offline Friends Memory**: Your friend list now remembers the usernames of your offline friends instead of showing them as "Unknown".
- **Custom Synchronization Server**: PeekParty now runs entirely on our own dedicated Metrolist-Server (Hetzner), offering a massive stability and speed boost compared to the old third-party solution.
- **Perfect Sync Logic**: The audio synchronization engine has been rebuilt from the ground up:
  - Eliminated the "Client permanently starts before Host" bug.
  - Removed flawed NTP-style Websocket pings that broke due to mobile network asymmetries.
  - Implemented an incredibly robust `15ms` soft-sync tolerance with an Exoplayer drift-correction bugfix. The app now stays perfectly locked to the host without bouncing.

**✨ UI & Polish**
- **Mandatory Username**: You can no longer secretly join a room; the app now properly prompts you for a username before you can use PeekParty.
- **Snappy Onboarding**: Fixed a bug where the Onboarding Screen and Changelog would briefly flash on every app startup.
- **Lyrics Peek Layout Fix**: The "PlayerLyricsPeek" widget now correctly responds to different layout widths and screen sizes.
- **Cleaner Menus**: The Listen Together connection state and room controls have been unified into a dedicated `ListenTogetherScreen` and completely removed from the standard `PlayerMenu` to reduce clutter.
