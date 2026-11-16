package com.metrolist.shared.peek

/**
 * PeekMusic's player menu: only what concerns the current song.
 *
 * The volume slider is gone because it set an extra in-app volume on top of the device volume, which the
 * volume keys never touch. Without the slider that in-app volume stays at [FULL_VOLUME]: it is reset on
 * every start and when a PeekParty guest (who can mute) leaves the room, so the app can never stay quiet.
 * PeekParty has its own entry points and no longer sits in this menu. The song header is hidden too: the
 * player behind the sheet already shows the song, and artist and album have their own entries.
 */
object PeekPlayerMenu {
    const val SHOW_HEADER = false
    const val SHOW_VOLUME = false
    const val SHOW_LISTEN_TOGETHER = false
    const val FULL_VOLUME = 1f
}
