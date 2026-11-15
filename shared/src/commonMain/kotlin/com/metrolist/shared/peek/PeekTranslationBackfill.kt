/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

/**
 * Google Translate with `sl=auto` picks the dominant language of a whole request. In a mixed song (ETA: mostly English
 * with Korean lines) the other language's lines come back unchanged, so they show no translation. PeekMusic 1.3.0
 * fixed this the same way: lines that came back unchanged are translated again, grouped by writing system, so each
 * request's dominant language is theirs.
 */
internal object PeekTranslationBackfill {
    enum class Script { LATIN, HANGUL, KANA, HAN, CYRILLIC, GREEK, ARABIC, HEBREW, DEVANAGARI, THAI, OTHER }

    /** Writing system of the first letter in [line], or null for lines without letters. */
    fun scriptOf(line: String): Script? {
        val ch = line.firstOrNull { it.isLetter() } ?: return null
        return when (ch.code) {
            in 0x0000..0x024F, in 0x1E00..0x1EFF -> Script.LATIN
            in 0x1100..0x11FF, in 0x3130..0x318F, in 0xAC00..0xD7AF -> Script.HANGUL
            in 0x3040..0x30FF, in 0x31F0..0x31FF -> Script.KANA
            in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> Script.HAN
            in 0x0400..0x052F -> Script.CYRILLIC
            in 0x0370..0x03FF -> Script.GREEK
            in 0x0600..0x06FF -> Script.ARABIC
            in 0x0590..0x05FF -> Script.HEBREW
            in 0x0900..0x097F -> Script.DEVANAGARI
            in 0x0E00..0x0E7F -> Script.THAI
            else -> Script.OTHER
        }
    }

    /**
     * Translates again the lines of [originals] whose entry in [translated] equals the original, one request group per
     * writing system. A group whose answer has the wrong number of lines keeps the first result.
     */
    suspend fun backfill(
        originals: List<String>,
        translated: List<String>,
        translate: suspend (List<String>) -> List<String>?,
    ): List<String> {
        if (originals.size != translated.size) return translated
        val unchanged =
            originals.indices.filter { index ->
                scriptOf(originals[index]) != null && translated[index].trim().equals(originals[index].trim(), ignoreCase = true)
            }
        if (unchanged.isEmpty()) return translated
        val result = translated.toMutableList()
        unchanged.groupBy { scriptOf(originals[it]) }.values.forEach { indices ->
            // A single group means the whole song came back unchanged: it is already in the target language.
            if (indices.size == unchanged.size && unchanged.size == originals.count { scriptOf(it) != null }) return translated
            val again = runCatching { translate(indices.map { originals[it] }) }.getOrNull()
            if (again != null && again.size == indices.size) {
                indices.zip(again).forEach { (index, line) -> result[index] = line }
            }
        }
        return result
    }
}
