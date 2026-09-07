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
    private var isCompositionActive = true

    // Cache translations in memory to avoid redundant API calls during a session
    private val translationCache = ConcurrentHashMap<String, List<String>>()

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

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
                        nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                            if (idx < cachedTranslations.size) {
                                lyrics[originalIndex].translatedTextFlow.value = cachedTranslations[idx]
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
                                                translatedLyrics = cachedTranslations.joinToString("\n"),
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
                        if (_status.value is TranslationStatus.Success && isCompositionActive) {
                            _status.value = TranslationStatus.Idle
                        }
                        return@launch
                    }

                    GoogleTranslateService
                        .translate(
                            text = fullText,
                            targetLanguage = targetLanguage,
                        ).onSuccess { translatedLines ->
                            if (!isCompositionActive) {
                                return@onSuccess
                            }

                            // Cache the translations
                            translationCache[cacheKey] = translatedLines

                            // Save to database if songId is provided
                            if (songId.isNotBlank() && database != null) {
                                try {
                                    val currentLyrics = database.lyrics(songId).first()
                                    if (currentLyrics != null) {
                                        database.query {
                                            upsert(
                                                currentLyrics.copy(
                                                    translatedLyrics = translatedLines.joinToString("\n"),
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

                            // Map translations back to original non-empty entries only
                            val expectedCount = nonEmptyEntries.size

                            when {
                                translatedLines.size >= expectedCount -> {
                                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                        lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
                                    }
                                    _hasActiveTranslations.value = true
                                    _status.value = TranslationStatus.Success
                                }

                                translatedLines.size < expectedCount -> {
                                    // Fewer translations than expected - map what we have
                                    translatedLines.forEachIndexed { idx, translation ->
                                        if (idx < nonEmptyEntries.size) {
                                            val originalIndex = nonEmptyEntries[idx].first
                                            lyrics[originalIndex].translatedTextFlow.value = translation
                                        }
                                    }
                                    _hasActiveTranslations.value = true
                                    _status.value = TranslationStatus.Success
                                }
                            }

                            // Auto-hide success message after 3 seconds
                            delay(3000)
                            if (_status.value is TranslationStatus.Success && isCompositionActive) {
                                _status.value = TranslationStatus.Idle
                            }
                        }
                        .onFailure { error ->
                            if (!isCompositionActive) {
                                return@onFailure
                            }

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
                            if ((_status.value as? TranslationStatus.Error)?.message == errorMessage &&
                                isCompositionActive
                            ) {
                                _status.value = TranslationStatus.Idle
                            }
                        }
                } catch (e: Exception) {
                    // Ignore cancellation exceptions or if composition is no longer active
                    if (e !is kotlinx.coroutines.CancellationException && isCompositionActive) {
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
