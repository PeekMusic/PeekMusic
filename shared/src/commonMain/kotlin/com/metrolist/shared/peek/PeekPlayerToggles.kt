/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.viewmodels.PlayerViewModel

/** The player's Peek and artwork controls that replace upstream's share button. */
@Stable
class PeekPlayerToggles internal constructor(
    internal val viewModel: PlayerViewModel,
    private val onOpenLyricsMenu: () -> Unit,
) {
    /** The Peek button opens the lyrics menu (Peek, translations, romanization, reload, sources). */
    fun openLyricsMenu() = onOpenLyricsMenu()

    val peekOn: Boolean
        get() = Settings.shared.showLyricsOnPlayer

    fun togglePeek() {
        Settings.shared.showLyricsOnPlayer = !peekOn
    }

    val translationOn: Boolean
        get() = Settings.shared.lyricsAiTranslationEnabled && Settings.shared.lyricsShowTranslations

    val romanizationOn: Boolean
        get() = Settings.shared.lyricsRomanizationEnabled

    val artworkMotionOn: Boolean
        get() = Settings.shared.isCanvasEnabled || Settings.shared.enableAnimatedCovers

    fun toggleTranslation() {
        val settings = Settings.shared
        if (translationOn) {
            settings.lyricsShowTranslations = false
            return
        }
        settings.lyricsAiTranslationEnabled = true
        settings.lyricsShowTranslations = true
        val lyrics = viewModel.uiState.value.lyrics
        if (lyrics.isNotEmpty() && lyrics.none { !it.translatedText.isNullOrBlank() }) {
            viewModel.setManualLyricsTranslation(true)
        }
    }

    fun toggleRomanization() {
        Settings.shared.lyricsRomanizationEnabled = !romanizationOn
    }

    /** Turns all moving artwork off and later restores exactly the sources that were on (canvas, animated covers). */
    fun toggleArtworkMotion() {
        val settings = Settings.shared
        if (artworkMotionOn) {
            settings.peekArtworkMotionRestore = "${settings.playerCanvasEnabled},${settings.enableAnimatedCovers}"
            settings.playerCanvasEnabled = false
            settings.enableAnimatedCovers = false
        } else {
            val (canvas, animatedCovers) = restoredArtworkMotion(settings.peekArtworkMotionRestore)
            settings.playerCanvasEnabled = canvas
            settings.enableAnimatedCovers = animatedCovers
        }
    }
}

/** Saved "canvas,animatedCovers" flags; with nothing saved, turning motion on enables the player canvas. */
internal fun restoredArtworkMotion(saved: String): Pair<Boolean, Boolean> {
    val flags = saved.split(',').mapNotNull { it.trim().toBooleanStrictOrNull() }
    if (flags.size != 2 || flags.none { it }) return true to false
    return flags[0] to flags[1]
}

@Composable
fun rememberPeekPlayerToggles(
    viewModel: PlayerViewModel,
    onOpenLyricsMenu: () -> Unit,
): PeekPlayerToggles {
    val openLyricsMenu by rememberUpdatedState(onOpenLyricsMenu)
    return remember(viewModel) { PeekPlayerToggles(viewModel) { openLyricsMenu() } }
}
