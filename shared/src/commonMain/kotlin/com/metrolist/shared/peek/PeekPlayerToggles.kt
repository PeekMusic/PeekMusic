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

    val canvasOn: Boolean
        get() = Settings.shared.playerCanvasEnabled

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

    /** Switches only the canvas; animated covers keep their own setting. */
    fun toggleCanvas() {
        Settings.shared.playerCanvasEnabled = !canvasOn
    }
}

@Composable
fun rememberPeekPlayerToggles(
    viewModel: PlayerViewModel,
    onOpenLyricsMenu: () -> Unit,
): PeekPlayerToggles {
    val openLyricsMenu by rememberUpdatedState(onOpenLyricsMenu)
    return remember(viewModel) { PeekPlayerToggles(viewModel) { openLyricsMenu() } }
}
