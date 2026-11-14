/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.settings.Settings

/**
 * PeekMusic's preferred defaults, applied once per version on app start. A default is only written when the key has
 * never been stored, so values the user chose or that were migrated from the legacy app always win. Upstream's
 * defaults in Settings stay untouched, which keeps upstream merges and tests unaffected.
 */
object PeekDefaults {
    internal const val VERSION = 1

    fun apply(settings: Settings = Settings.shared) {
        if (settings.peekDefaultsVersion >= VERSION) return
        with(settings) {
            setIfUnset("showLyricsOnPlayer") { showLyricsOnPlayer = true }
            setIfUnset("isQueueLocked") { isQueueLocked = false }
            setIfUnset("lyricsSyllableRomanizationEnabled") { lyricsSyllableRomanizationEnabled = false }
            setIfUnset("preferWordSyncedLyrics") { preferWordSyncedLyrics = true }
            setIfUnset("preventDuplicateTracksInQueue") { preventDuplicateTracksInQueue = true }
            setIfUnset("lyricsAiProvider") { lyricsAiProvider = "Google Translate" }
            setIfUnset("peekParallelLyricsLookup") { peekParallelLyricsLookup = true }
        }
        settings.peekDefaultsVersion = VERSION
    }

    private inline fun Settings.setIfUnset(
        key: String,
        set: Settings.() -> Unit,
    ) {
        if (!hasStoredValue(key)) set()
    }
}
