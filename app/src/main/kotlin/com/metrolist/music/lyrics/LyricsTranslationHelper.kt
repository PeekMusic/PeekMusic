/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.api.GoogleTranslateService
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides lyric translation on demand via [GoogleTranslateService] (free, no API key).
 */
object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private var translationJob: kotlinx.coroutines.Job? = null

    // Ref-counted: every visible lyrics surface (full views + player peek) registers itself;
    // translation results are only applied while at least one surface is visible.
    private var activeCompositions = 0
    private val isCompositionActive: Boolean
        get() = activeCompositions > 0

    fun setCompositionActive(active: Boolean) {
        activeCompositions = (activeCompositions + if (active) 1 else -1).coerceAtLeast(0)
    }

    // Cache translations in memory to avoid redundant API calls during a session
    private val translationCache = ConcurrentHashMap<String, List<String?>>()

    fun triggerManualTranslation() {
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _clearTranslationsTrigger.tryEmit(Unit)
        _hasActiveTranslations.value = false
    }

    fun clearTranslations(lyrics: LyricsEntity): LyricsEntity {
        return lyrics.copy(
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = ""
        )
    }

    /**
     * Clears the whole translation cache: the in-memory map and all persisted
     * translations in the database (per-song lyrics entities are kept).
     */
    suspend fun clearTranslationCache(database: MusicDatabase) {
        translationCache.clear()
        _hasActiveTranslations.value = false
        database.query {
            clearAllLyricsTranslations()
        }
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        if (_status.value is TranslationStatus.Translating) {
            _status.value = TranslationStatus.Idle
        }
    }

    private fun getCacheKey(text: String, mode: String, targetLanguage: String): String {
        return "${text.hashCode()}_${mode}_${targetLanguage}"
    }

    /**
     * Second pass for mixed-language songs: with sl=auto Google picks the dominant
     * language of the whole blob (e.g. English for a mostly-English song — or Korean
     * for a mostly-Korean one), so lines in the other language come back untranslated
     * while the status still reports success. Re-translate the unchanged lines,
     * grouped by script, so each request's dominant language matches the lines in it.
     * ponytail: best-effort — if a group comes back with a different line count, the
     * first-pass result is kept for that group. Songs already in the target language
     * cost one redundant request per script group that returns identical text (which
     * the UI filters out anyway).
     */
    private suspend fun backfillUntranslatedLines(
        originalLines: List<String>,
        translations: List<String?>,
        targetLanguage: String,
    ): List<String?> {
        val unchangedIndices = originalLines.mapIndexedNotNull { idx, original ->
            val unchanged = translations.getOrNull(idx)?.trim()
                ?.equals(original.trim(), ignoreCase = true) != false
            if (unchanged) idx else null
        }
        if (unchangedIndices.isEmpty()) return translations

        val backfillByIndex = mutableMapOf<Int, String>()
        val groups = unchangedIndices.groupBy { idx -> scriptOf(originalLines[idx]) }
        for ((script, indices) in groups) {
            val result = GoogleTranslateService.translate(
                text = indices.joinToString("\n") { originalLines[it] },
                targetLanguage = targetLanguage,
            ).onFailure {
                Timber.d("[TRANSLATE] backfill request failed for $script: ${it.message}")
            }.getOrNull() ?: continue
            Timber.d("[TRANSLATE] backfill: ${indices.size} unchanged $script lines, got ${result.size}")
            if (result.size != indices.size) continue
            indices.zip(result).forEach { (i, translated) -> backfillByIndex[i] = translated }
        }

        return translations.mapIndexed { idx, line -> backfillByIndex[idx] ?: line }
    }

    /** Script of the first letter in the line; punctuation-only lines map to COMMON. */
    private fun scriptOf(line: String): Character.UnicodeScript {
        val ch = line.firstOrNull { Character.isLetter(it) } ?: return Character.UnicodeScript.COMMON
        return Character.UnicodeScript.of(ch.code)
    }

    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String
    ) {
        if (lyricsEntity == null || lyricsEntity.translatedLyrics.isNullOrBlank()) {
            _hasActiveTranslations.value = false
            lyrics.forEach { it.translatedTextFlow.value = null }
            return
        }

        // Only load if language and mode match
        if (lyricsEntity.translationLanguage != targetLanguage || lyricsEntity.translationMode != mode) {
            _hasActiveTranslations.value = false
            lyrics.forEach { it.translatedTextFlow.value = null }
            return
        }

        val translatedLines = lyricsEntity.translatedLyrics.split("\n")
        val nonEmptyEntries = lyrics.filter { it.text.isNotBlank() }

        if (translatedLines.size >= nonEmptyEntries.size) {
            var transIndex = 0
            lyrics.forEach { entry ->
                if (entry.text.isNotBlank() && transIndex < translatedLines.size) {
                    entry.translatedTextFlow.value = translatedLines[transIndex]
                    transIndex++
                }
            }

            // Also cache them
            val fullText = nonEmptyEntries.joinToString("\n") { it.text }
            val cacheKey = getCacheKey(fullText, mode, targetLanguage)
            translationCache[cacheKey] = translatedLines
            _hasActiveTranslations.value = true
        }
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        songId: String = "",
        database: MusicDatabase? = null,
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating

        // Clear existing translations to indicate re-translation
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob =
            scope.launch(Dispatchers.IO) {
                try {
                    if (lyrics.isEmpty()) {
                        _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_no_lyrics))
                        return@launch
                    }

                    // Filter out empty lines and keep track of their indices
                    val nonEmptyEntries =
                        lyrics.mapIndexedNotNull { index, entry ->
                            if (entry.text.isNotBlank()) index to entry else null
                        }

                    if (nonEmptyEntries.isEmpty()) {
                        _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_lyrics_empty))
                        return@launch
                    }

                    if (targetLanguage.isBlank()) {
                        _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_language_required))
                        return@launch
                    }

                    // Create text from non-empty lines only
                    val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }

                    // Check cache first
                    val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                    val cachedTranslations = translationCache[cacheKey]
                    if (cachedTranslations != null && cachedTranslations.size >= nonEmptyEntries.size) {
                        val alignedCache = List(nonEmptyEntries.size) { idx -> cachedTranslations.getOrNull(idx) }
                        val finalCache = backfillUntranslatedLines(
                            nonEmptyEntries.map { it.second.text },
                            alignedCache,
                            targetLanguage,
                        )
                        if (finalCache != alignedCache) {
                            translationCache[cacheKey] = finalCache
                        }
                        nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                            finalCache.getOrNull(idx)?.let {
                                lyrics[originalIndex].translatedTextFlow.value = it
                            }
                        }
                        _hasActiveTranslations.value = true
                        _status.value = TranslationStatus.Success

                        // Persist cached translations to DB so loadTranslationsFromDatabase can't
                        // overwrite them with a stale empty entity (e.g. after an untranslate race).
                        if (songId.isNotBlank() && database != null) {
                            try {
                                val currentLyrics = database.lyrics(songId).first()
                                if (currentLyrics != null && currentLyrics.translatedLyrics.isNullOrBlank()) {
                                    database.query {
                                        upsert(
                                            currentLyrics.copy(
                                                translatedLyrics = finalCache.joinToString("\n") { it.orEmpty() },
                                                translationLanguage = targetLanguage,
                                                translationMode = mode,
                                            ),
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to persist cached translations to database")
                            }
                        }

                        delay(3000)
                        if (_status.value is TranslationStatus.Success ) {
                            _status.value = TranslationStatus.Idle
                        }
                        return@launch
                    }

                    GoogleTranslateService
                        .translate(
                            text = fullText,
                            targetLanguage = targetLanguage,
                        ).onSuccess { translatedLines ->

                            // Map translations back to original non-empty entries (null when
                            // Google returned fewer lines than sent)
                            val expectedCount = nonEmptyEntries.size
                            Timber.d("[TRANSLATE] first request: expected $expectedCount lines, got ${translatedLines.size}")
                            val aligned: List<String?> =
                                List(expectedCount) { idx -> translatedLines.getOrNull(idx) }
                            val finalTranslations = backfillUntranslatedLines(
                                nonEmptyEntries.map { it.second.text },
                                aligned,
                                targetLanguage,
                            )

                            // Cache the translations
                            translationCache[cacheKey] = finalTranslations

                            // Save to database if songId is provided
                            if (songId.isNotBlank() && database != null) {
                                try {
                                    val currentLyrics = database.lyrics(songId).first()
                                    if (currentLyrics != null) {
                                        database.query {
                                            upsert(
                                                currentLyrics.copy(
                                                    translatedLyrics = finalTranslations.joinToString("\n") { it.orEmpty() },
                                                    translationLanguage = targetLanguage,
                                                    translationMode = mode,
                                                ),
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to save translated lyrics to database")
                                }
                            }

                            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                finalTranslations.getOrNull(idx)?.let {
                                    lyrics[originalIndex].translatedTextFlow.value = it
                                }
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success

                            // Auto-hide success message after 3 seconds
                            delay(3000)
                            if (_status.value is TranslationStatus.Success ) {
                                _status.value = TranslationStatus.Idle
                            }
                        }
                        .onFailure { error ->

                            val httpError = error as? GoogleTranslateService.TranslationHttpException
                            val errorMessage = when {
                                httpError?.code == 429 && httpError.retryAfterSeconds != null ->
                                    context.getString(
                                        com.metrolist.music.R.string.ai_error_rate_limited_retry,
                                        httpError.retryAfterSeconds,
                                    )

                                httpError?.code == 429 ->
                                    context.getString(com.metrolist.music.R.string.ai_error_rate_limited)

                                else -> error.message
                                    ?: context.getString(com.metrolist.music.R.string.ai_error_unknown)
                            }

                            // Show error in UI, auto-hide after a few seconds
                            _status.value = TranslationStatus.Error(errorMessage)
                            delay(5000)
                            if ((_status.value as? TranslationStatus.Error)?.message == errorMessage) {
                                _status.value = TranslationStatus.Idle
                            }
                        }
                } catch (e: Exception) {
                    // Ignore cancellation exceptions or if composition is no longer active
                    if (e !is kotlinx.coroutines.CancellationException ) {
                        val errorMessage = e.message ?: context.getString(com.metrolist.music.R.string.ai_error_translation_failed)
                        _status.value = TranslationStatus.Error(errorMessage)
                    }
                }
            }
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}
