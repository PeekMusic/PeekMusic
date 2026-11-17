/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.peek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.state.playback.LyricsState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.shared.peek.PeekLyricsLine
import com.metrolist.shared.settings.Settings

/**
 * PeekMusic's Peek in the desktop full player, in place of upstream's ActiveLyricsLine: the same line as on Android
 * (adlibs, gap ring, loading and missing states), hidden while the lyrics panel shows the full text. A tap opens the panel.
 */
@Composable
internal fun DesktopPeekLyricsLine(
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val currentTrack = PlayerState.currentTrack
    val showPeek = Settings.shared.showLyricsOnPlayer
    LaunchedEffect(showPeek, currentTrack?.id, Settings.shared.lyricsProviderOrder, Settings.shared.lyricsEnabledProviders) {
        if (showPeek && currentTrack != null) LyricsState.fetchLyrics(currentTrack)
    }
    val castState by PlayerState.castManager.state.collectAsState()
    val openPanel = { if (!UiState.showLyricsDrawer) LyricsState.toggleLyricsDrawer() }
    PeekLyricsLine(
        positionFlow = PlayerState.playbackPosition,
        positionReader = PlayerState::currentMediaPositionMs,
        playbackSpeed =
            PlayerState.player.playbackSpeed
                .collectAsState()
                .value,
        lyrics = LyricsState.currentLyricsEntries,
        songId = currentTrack?.id,
        timingOffsetMs = LyricsState.currentLyricsOffsetMs,
        isPlaying = PlayerState.isPlaying,
        isLoading = LyricsState.isLyricsLoading,
        textColor = textColor,
        isCasting = castState.isConnected,
        onShowLyrics = openPanel,
        onReload = { currentTrack?.let { LyricsState.fetchLyrics(it, skipCache = true) } },
        onSearchLyrics = openPanel,
        modifier = modifier,
        matchesPlayback = !UiState.showLyricsDrawer && !UiState.videoFullscreenObscuresPlayer,
    )
}
