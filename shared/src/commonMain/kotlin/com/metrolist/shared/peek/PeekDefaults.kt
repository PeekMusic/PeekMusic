/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.constants.AppleMusicArtistVideosKey
import com.metrolist.shared.constants.FloatingNavBarKey
import com.metrolist.shared.constants.FloatingTopBarKey
import com.metrolist.shared.constants.LyricsProgressiveBlurKey
import com.metrolist.shared.constants.UseLegacyShuffleKey
import com.metrolist.shared.settings.PlayerBackgroundStyle
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.SliderStyle

/**
 * PeekMusic's preferred defaults, applied once per version on app start. A default is only written when the key has
 * never been stored, so values the user chose or that were migrated from the legacy app always win. Upstream's
 * defaults in Settings stay untouched, which keeps upstream merges and tests unaffected.
 */
object PeekDefaults {
    internal const val VERSION = 2

    fun apply(settings: Settings = Settings.shared) {
        if (settings.peekDefaultsVersion >= VERSION) return
        with(settings) {
            setIfUnset("showLyricsOnPlayer") { showLyricsOnPlayer = true }
            setIfUnset("isQueueLocked") { isQueueLocked = false }
            setIfUnset("lyricsSyllableRomanizationEnabled") { lyricsSyllableRomanizationEnabled = false }
            setIfUnset("preferWordSyncedLyrics") { preferWordSyncedLyrics = true }
            setIfUnset("preventDuplicateTracksInQueue") { preventDuplicateTracksInQueue = true }
            setIfUnset("lyricsAiProvider") { lyricsAiProvider = GOOGLE_TRANSLATE }
            setIfUnset("peekParallelLyricsLookup") { peekParallelLyricsLookup = true }
            // Version 2: the settings review of 2026-11-15.
            setIfUnset(FloatingNavBarKey) { floatingNavBar = true }
            setIfUnset(FloatingTopBarKey) { floatingTopBar = true }
            setIfUnset("playerBackgroundStyle") { playerBackgroundStyle = PlayerBackgroundStyle.GRADIENT }
            setIfUnset("sliderStyle") { sliderStyle = SliderStyle.SQUIGGLY }
            setIfUnset(AppleMusicArtistVideosKey) { enableAppleMusicArtistVideos = false }
            setIfUnset(UseLegacyShuffleKey) { useLegacyShuffle = false }
            setIfUnset(LyricsProgressiveBlurKey) { lyricsProgressiveBlur = true }
        }
        settings.peekDefaultsVersion = VERSION
    }

    /**
     * Fixed values for the settings PeekMusic hides ([PeekSettingsLayout.hiddenItemIds]). Applied on every start, because
     * a hidden setting can no longer be changed in the app, so a value from an old version or the legacy app must not stick.
     */
    fun pinHidden(settings: Settings = Settings.shared) {
        with(settings) {
            pin(uiEnhancements, true) { uiEnhancements = it }
            pin(slimNavBar, false) { slimNavBar = it }
            pin(forceBottomNavBar, true) { forceBottomNavBar = it }
            pin(swipeThumbnail, true) { swipeThumbnail = it }
            pin(swipeToSong, true) { swipeToSong = it }
            pin(swipeToRemoveSong, true) { swipeToRemoveSong = it }
            pin(useRoundedRectPlayButton, false) { useRoundedRectPlayButton = it }
            pin(expressiveMiniPlayerShapeMotion, true) { expressiveMiniPlayerShapeMotion = it }
            pin(skipSilence, false) { skipSilence = it }
            pin(skipSilenceInstant, false) { skipSilenceInstant = it }
            pin(keepPausedOnSongChange, false) { keepPausedOnSongChange = it }
            pin(varispeed, false) { varispeed = it }
            pin(audioTrackPlaybackParams, false) { audioTrackPlaybackParams = it }
            pin(seekExtraSeconds, false) { seekExtraSeconds = it }
            pin(persistentQueue, true) { persistentQueue = it }
            pin(promptContinuePlayback, false) { promptContinuePlayback = it }
            pin(rememberShuffleAndRepeat, true) { rememberShuffleAndRepeat = it }
            pin(lyricsAutoScroll, true) { lyricsAutoScroll = it }
            pin(preferWordSyncedLyrics, true) { preferWordSyncedLyrics = it }
            pin(lyricsAiProvider, GOOGLE_TRANSLATE) { lyricsAiProvider = it }
            pin(lyricsAiActivationMode, "Automatic") { lyricsAiActivationMode = it }
            pin(lyricsRomanizeCyrillicByLine, true) { lyricsRomanizeCyrillicByLine = it }
            pin(explicitOnly, false) { explicitOnly = it }
            pin(blockScreenshots, false) { blockScreenshots = it }
            pin(queueYouTubeHistoryWhenOffline, true) { queueYouTubeHistoryWhenOffline = it }
        }
    }

    private const val GOOGLE_TRANSLATE = "Google Translate"

    private inline fun <T> pin(
        current: T,
        value: T,
        set: (T) -> Unit,
    ) {
        if (current != value) set(value)
    }

    private inline fun Settings.setIfUnset(
        key: String,
        set: Settings.() -> Unit,
    ) {
        if (!hasStoredValue(key)) set()
    }
}
