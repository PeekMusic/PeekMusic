/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.api.lyrics.LyricsUtils
import com.metrolist.shared.api.lyrics.MlxParser
import com.metrolist.shared.api.lyrics.XmlElement
import com.metrolist.shared.api.lyrics.XmlText
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.MlxDocument
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.utils.getSystemLanguage

/**
 * Lyrics translations that ship with the lyrics. BetterLyrics' TTML (Apple's format) carries them in its head as
 * `<translations><translation xml:lang="en-US"><text for="L1">…</text>`, keyed by each line's `itunes:key`; upstream
 * ignores them. They are written into the MLX `[translatedLyrics]` section with their language, and only shown when that
 * language is the lyrics translation target, so a German target still gets Google Translate instead of Apple's English.
 */
object PeekBuiltInTranslations {
    private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"

    /** Adds the TTML's translation to [mlx]. [keysByStartMs] maps each main line's start to its `itunes:key`. */
    internal fun withTtmlTranslations(
        mlx: String,
        root: XmlElement,
        keysByStartMs: List<Pair<Long, String>>,
    ): String {
        if (mlx.isBlank() || keysByStartMs.isEmpty()) return mlx
        val translation = findTranslation(root) ?: return mlx
        val language = translation.getAttribute("xml:lang").ifBlank { return mlx }
        val texts =
            translation.children
                .filter { it.localName == "text" }
                .mapNotNull { text ->
                    val key = text.getAttribute("for").ifBlank { return@mapNotNull null }
                    key to mainText(text).trim()
                }.filter { it.second.isNotEmpty() }
                .toMap()
        if (texts.isEmpty()) return mlx
        val keysAt = keysByStartMs.groupBy({ it.first }, { it.second }).mapValues { ArrayDeque(it.value) }
        val lines = MlxParser.parse(mlx).lines
        val translations = lines.map { line -> if (line.isBackground) null else keysAt[line.time]?.removeFirstOrNull()?.let(texts::get) }
        if (translations.none { it != null }) return mlx
        return MlxParser.withTranslations(withTranslationLanguage(mlx, language), lines, translations)
    }

    /** The lines of [document], without its built-in translation when that is not in the translation target language. */
    internal fun linesForTarget(document: MlxDocument): List<LyricsEntry> {
        val language = document.song.translationLanguage ?: return document.lines
        if (document.lines.none { it.translatedText != null }) return document.lines
        val target = runCatching { targetLanguage() }.getOrNull() ?: return document.lines
        if (sameLanguage(language, target)) return document.lines
        return document.lines.map { it.copy(translatedText = null) }
    }

    /** Where the shown translation comes from, when it shipped with the lyrics; null means it was machine translated. */
    fun builtInSource(rawLyrics: String?): String? {
        val document = rawLyrics?.let(LyricsUtils::parseMlxDocument) ?: return null
        if (document.song.translationLanguage == null || document.lines.none { !it.translatedText.isNullOrBlank() }) return null
        return document.meta.lyricSource ?: document.meta.provider
    }

    internal fun sameLanguage(
        first: String,
        second: String,
    ): Boolean = primarySubtag(first) == primarySubtag(second)

    private fun primarySubtag(language: String) =
        language
            .substringBefore('-')
            .substringBefore('_')
            .trim()
            .lowercase()

    private fun targetLanguage(): String? {
        val configured = Settings.shared.lyricsAiTargetLanguage
        val language = if (configured == "device" || configured.isBlank()) getSystemLanguage() else configured
        return language.takeIf { it.isNotBlank() }
    }

    private fun findTranslation(root: XmlElement): XmlElement? =
        child(root, "head")
            ?.let { child(it, "metadata") }
            ?.children
            ?.firstOrNull { it.localName.equals("iTunesMetadata", ignoreCase = true) }
            ?.let { child(it, "translations") }
            ?.children
            ?.firstOrNull { it.localName == "translation" }

    private fun child(
        element: XmlElement,
        name: String,
    ) = element.children.firstOrNull { it.localName == name }

    /** The line's own words, without background vocals. */
    private fun mainText(element: XmlElement): String =
        buildString {
            for (node in element.childNodes) {
                when (node) {
                    is XmlText -> append(node.value)
                    is XmlElement -> if (role(node) != "x-bg") append(mainText(node))
                }
            }
        }

    private fun role(element: XmlElement): String =
        element.getAttributeNS(TTML_METADATA_NS, "role").ifBlank { element.getAttribute("ttm:role") }

    private fun withTranslationLanguage(
        mlx: String,
        language: String,
    ): String {
        val lines = mlx.lines().toMutableList()
        val song = lines.indexOfFirst { it.trim().equals("[song]", ignoreCase = true) }
        if (song < 0) return mlx
        lines.add(song + 1, "translation_language=$language")
        return lines.joinToString("\n")
    }
}
