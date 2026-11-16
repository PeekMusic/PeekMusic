/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.api.lyrics.LyricsAiResult

/**
 * Google Translate has no language check up front: lyrics already in the target language come back unchanged. Such a
 * result is cached as "not needed", so the song is never sent again and shows no failure.
 */
internal object PeekTranslationNotNeeded {
    fun isUnchanged(
        sources: List<String>,
        result: LyricsAiResult,
        translationStyle: String,
    ): Boolean {
        if (translationStyle == "Romanized") return false
        val translated = result.translatedLines ?: return false
        if (translated.size != sources.size || sources.none(String::isNotBlank)) return false
        return sources.indices.all { normalize(sources[it]) == normalize(translated[it]) }
    }

    private fun normalize(line: String): String = line.trim().lowercase().filter { it.isLetterOrDigit() }
}
