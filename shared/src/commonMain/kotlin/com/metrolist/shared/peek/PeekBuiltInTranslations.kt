/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.api.lyrics.LyricsUtils
import com.metrolist.shared.api.lyrics.lyricsTranslationLanguageMatches
import com.metrolist.shared.api.lyrics.lyricsTranslationTargetLanguage
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.MlxDocument

/**
 * Lyrics translations that ship with the lyrics. Upstream reads them from BetterLyrics' TTML (Apple's) since
 * eba449f9a and keeps only the one in the translation target language. PeekMusic adds two things on top: lyrics cached
 * before a change of the target language don't show a translation in the old language, and the lyrics menu names
 * the translation's source.
 */
object PeekBuiltInTranslations {
    /** The lines of [document], without its built-in translation when that is not in the translation target language. */
    internal fun linesForTarget(document: MlxDocument): List<LyricsEntry> {
        val language = document.song.translationLanguage ?: return document.lines
        if (document.lines.none { it.translatedText != null }) return document.lines
        val target = runCatching { lyricsTranslationTargetLanguage() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return document.lines
        if (lyricsTranslationLanguageMatches(language, target)) return document.lines
        return document.lines.map { it.copy(translatedText = null) }
    }

    /** Where the shown translation comes from, when it shipped with the lyrics; null means it was machine translated. */
    fun builtInSource(rawLyrics: String?): String? {
        val document = rawLyrics?.let(LyricsUtils::parseMlxDocument) ?: return null
        if (document.song.translationLanguage == null || document.lines.none { !it.translatedText.isNullOrBlank() }) return null
        return document.meta.lyricSource ?: document.meta.provider
    }
}
