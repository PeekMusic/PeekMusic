/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.peek.PeekTranslationBackfill.Script

/**
 * Decides on the device which lyric lines are not in the target language yet, so Google Translate only receives those.
 * Google Translate has no free language check, and every song sent counts towards the limit that gets a network
 * blocked. A line in another writing system than the target always needs translation. Within the same writing system,
 * frequent words tell the common Latin-script languages apart; lines without a clear signal follow the song's majority.
 * When in doubt a line is translated, so nothing is ever left out by mistake.
 */
internal object PeekLanguageDetector {
    /** One decision per line, in order: true when the line should be translated into [targetLanguage]. */
    fun linesNeedingTranslation(
        lines: List<String>,
        targetLanguage: String,
    ): List<Boolean> {
        val target = targetLanguage.replace('_', '-').substringBefore('-').lowercase()
        val targetScripts = SCRIPTS_BY_LANGUAGE[target] ?: return lines.map(String::isNotBlank)
        val decisions = lines.map { decide(it, target, targetScripts) }
        // Lines without a clear signal follow the lines that have one; a song mostly in the target stays untranslated.
        val clear = decisions.filterIsInstance<Decision.Clear>()
        val songNeedsTranslation = clear.isEmpty() || clear.count { it.needsTranslation } * 2 > clear.size
        return decisions.map { decision ->
            when (decision) {
                Decision.Nothing -> false
                Decision.Unclear -> songNeedsTranslation
                is Decision.Clear -> decision.needsTranslation
            }
        }
    }

    private sealed interface Decision {
        data object Nothing : Decision

        data object Unclear : Decision

        data class Clear(
            val needsTranslation: Boolean,
        ) : Decision
    }

    private fun decide(
        line: String,
        target: String,
        targetScripts: Set<Script>,
    ): Decision {
        val script = dominantScript(line) ?: return Decision.Nothing
        if (script !in targetScripts) return Decision.Clear(needsTranslation = true)
        // Japanese and Chinese share Han characters: kana marks Japanese.
        if (target == "zh" && hasKana(line)) return Decision.Clear(needsTranslation = true)
        if (target == "ja" && script == Script.HAN && !hasKana(line)) return Decision.Unclear
        if (script != Script.LATIN) return Decision.Clear(needsTranslation = false)
        val words = words(line)
        val scores = STOPWORDS.mapValues { (_, list) -> words.count { it in list } }
        val best = scores.values.maxOrNull() ?: 0
        val targetScore = scores[target] ?: 0
        // One shared word ("me", "no", "a") is no evidence: such lines follow the song.
        if (best < 2) return Decision.Unclear
        return Decision.Clear(needsTranslation = targetScore < best)
    }

    private fun dominantScript(line: String): Script? =
        line
            .filter(Char::isLetter)
            .mapNotNull { PeekTranslationBackfill.scriptOf(it.toString()) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    private fun hasKana(line: String): Boolean = line.any { it.code in 0x3040..0x30FF || it.code in 0x31F0..0x31FF }

    private fun words(line: String): List<String> =
        line
            .lowercase()
            .replace('’', '\'')
            .split(Regex("[^\\p{L}']+"))
            .map { it.trim('\'') }
            .filter(String::isNotEmpty)

    private val LATIN = setOf(Script.LATIN)

    private val SCRIPTS_BY_LANGUAGE: Map<String, Set<Script>> =
        codes(
            "en de es fr pt it nl pl tr sv da nb no nn fi cs sk sl hr bs ro hu et lt lv id ms vi fil ca " +
                "sq az",
        ).associateWith { LATIN } +
            codes("ru uk be bg sr mk kk").associateWith { setOf(Script.CYRILLIC) } +
            mapOf(
                "ko" to setOf(Script.HANGUL),
                "ja" to setOf(Script.KANA, Script.HAN),
                "zh" to setOf(Script.HAN),
                "el" to setOf(Script.GREEK),
                "ar" to setOf(Script.ARABIC),
                "fa" to setOf(Script.ARABIC),
                "ur" to setOf(Script.ARABIC),
                "he" to setOf(Script.HEBREW),
                "iw" to setOf(Script.HEBREW),
                "hi" to setOf(Script.DEVANAGARI),
                "ne" to setOf(Script.DEVANAGARI),
                "mr" to setOf(Script.DEVANAGARI),
                "th" to setOf(Script.THAI),
            )

    /** Frequent words that are typical for one language and rare in the others listed here. */
    private val STOPWORDS: Map<String, Set<String>> =
        mapOf(
            "en" to
                codes(
                    "the you i i'm and my your it's is are to of that this what with don't can't we be just " +
                        "know all love baby when it was for never can let got gonna wanna they she he she's he's " +
                        "you're i'll i've oh yeah like on have do will now want feel night me make won't from get go up out " +
                        "time down say way come need there one back more give take tonight heart girl boy",
                ),
            "de" to
                codes(
                    "ich und nicht das ist du die der mit mich mir dich dir wir ein eine auf für sich auch noch " +
                        "nur wie wenn was kein nie immer doch bin bist hab habe zu es sie uns alles liebe mein",
                ),
            "es" to
                codes(
                    "que el la los las y de en yo tú tu mi me te es por con para una un no lo pero si más " +
                        "cuando como quiero amor nada todo se le del está estoy eres soy ya ti",
                ),
            "fr" to
                codes(
                    "je tu et le la les de des est pas que qui un une dans pour sur mon ma mes moi toi nous " +
                        "vous c'est j'ai suis avec ne plus tout comme mais quand il elle on ça au du",
                ),
            "pt" to
                codes(
                    "que eu você voce não nao é o a os as um uma de do da em no na meu minha te me com para pra " +
                        "mais mas quando amor tudo nada sou estou seu sua isso vou",
                ),
            "it" to
                codes(
                    "che io tu il la le di e è non un una per con mi ti ci si sono sei mio mia del della nel " +
                        "nella ma più come quando amore tutto niente questo anche ho hai",
                ),
            "nl" to
                codes(
                    "ik je jij en het de een niet is van dat die met op voor mijn maar wat als zijn ben bent we " +
                        "wij ons nog wel geen naar mij jou heb hebt zo ook",
                ),
            "pl" to
                codes(
                    "nie i w na to jest się że ja ty mnie ci z do jak co tak mi ale czy mój moja tylko już " +
                        "jestem jesteś kocham bo o",
                ),
            "tr" to
                codes(
                    "ve bir bu ben sen çok ne da de ki mi için gibi var yok beni seni benim senin aşk ama daha " +
                        "her olmaz değil",
                ),
        )

    private fun codes(list: String): Set<String> = list.split(' ').toSet()
}
