/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.api.lyrics.LyricsUtils
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.lyrics_not_found
import com.metrolist.shared.generated.resources.no_provider_results_found
import com.metrolist.shared.generated.resources.peek_plain_lyrics_hint
import com.metrolist.shared.generated.resources.retry
import com.metrolist.shared.generated.resources.search_lyrics
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.WordTimestamp
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.lyrics.ActiveLyricsLineContent
import com.metrolist.shared.ui.components.lyrics.InlineLyricsLine
import com.metrolist.shared.ui.components.lyrics.IntervalIndicator
import com.metrolist.shared.ui.components.lyrics.LYRICS_GAP_START_LAG_MS
import com.metrolist.shared.ui.components.lyrics.LyricsListItem
import com.metrolist.shared.ui.components.lyrics.buildLyricsListItems
import com.metrolist.shared.ui.components.lyrics.rememberCaptionLyricsPositionState
import com.metrolist.shared.ui.components.lyrics.rememberSmoothLyricsPositionState
import com.metrolist.shared.ui.components.lyrics.resolveLyricsTextColor
import com.metrolist.shared.ui.menu.LyricsProviderDialog
import com.metrolist.shared.ui.menu.LyricsSearchDialog
import com.metrolist.shared.ui.player.LocalPlayerPositionActive
import com.metrolist.shared.viewmodels.PlayerViewModel
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/** Text sizes of the Peek line: as in the legacy PeekMusic player, smaller where vertical space is tight. */
private const val PEEK_TEXT_SIZE_SP = 24f
private const val PEEK_COMPACT_TEXT_SIZE_SP = 20f
private const val PEEK_ADLIB_TEXT_SIZE_SP = 15f
private const val UNTIMED_ADLIB_MS = 3_500L
private const val ADLIB_HOLD_MS = 1_200L
private const val ADLIB_MIN_VISIBLE_MS = 2_500L
private const val ADLIB_PREPARE_MS = 2_000L
private const val ADLIB_ANIMATION_MS = 300L

/** Fixed minimum heights keep the artwork and controls still while the Peek switches between lines, gaps and states. */
private val PeekMinHeight = 80.dp
private val PeekCompactMinHeight = 64.dp

/** Adlibs sit near the line, not at the far edges of a wide player. */
private val PeekAdlibAreaWidth = 560.dp

private sealed interface PeekContent {
    data object Loading : PeekContent

    data object NotFound : PeekContent

    data object PlainOnly : PeekContent

    data class Gap(
        val item: LyricsListItem.Indicator,
    ) : PeekContent

    /** Carries its own line: AnimatedContent keeps drawing the outgoing state after the lyrics have changed. */
    data class Line(
        val index: Int,
        val entry: LyricsEntry,
        val endTimeMs: Long,
    ) : PeekContent
}

/**
 * The PeekMusic lyrics Peek: the current line between artwork and player controls, with background vocals as tilted
 * call-outs above it,
 * a ring through instrumental gaps, and actions when lyrics are missing. Tapping it opens the full lyrics.
 */
@Composable
internal fun PeekLyricsLine(
    viewModel: PlayerViewModel,
    lyrics: List<LyricsEntry>,
    songId: String?,
    timingOffsetMs: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    textColor: Color,
    isCasting: Boolean,
    onShowLyrics: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    matchesPlayback: Boolean = true,
) {
    var searching by remember { mutableStateOf(false) }
    if (searching) PeekLyricsSearch(viewModel, onDismiss = { searching = false })
    PeekLyricsLine(
        positionFlow = viewModel.currentPosition,
        positionReader = viewModel::currentPositionMs,
        playbackSpeed = viewModel.playbackSpeed.collectAsState().value,
        lyrics = lyrics,
        songId = songId,
        timingOffsetMs = timingOffsetMs,
        isPlaying = isPlaying,
        isLoading = isLoading,
        textColor = textColor,
        isCasting = isCasting,
        onShowLyrics = onShowLyrics,
        onReload = onReload,
        onSearchLyrics = { searching = true },
        modifier = modifier,
        compact = compact,
        matchesPlayback = matchesPlayback,
    )
}

/**
 * The Peek for players without a [PlayerViewModel] (desktop): the same line, driven by the player's own position clock.
 * [onSearchLyrics] opens the platform's lyrics search when no lyrics were found.
 */
@Composable
fun PeekLyricsLine(
    positionFlow: StateFlow<Long>,
    positionReader: () -> Long,
    playbackSpeed: Float,
    lyrics: List<LyricsEntry>,
    songId: String?,
    timingOffsetMs: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    textColor: Color,
    isCasting: Boolean,
    onShowLyrics: () -> Unit,
    onReload: () -> Unit,
    onSearchLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    matchesPlayback: Boolean = true,
) {
    AnimatedVisibility(
        visible = Settings.shared.showLyricsOnPlayer && matchesPlayback,
        enter = expandVertically(clip = false) + fadeIn(),
        exit = shrinkVertically(clip = false) + fadeOut(),
        modifier = modifier,
    ) {
        // Background-vocal lines belong to the line before them; on their own they would replace it in the Peek.
        val peekLyrics = remember(lyrics) { mergeBackgroundLines(lyrics) }
        val hasTiming = remember(peekLyrics) { LyricsUtils.hasPlaybackTiming(peekLyrics) }
        val positionState =
            rememberCaptionLyricsPositionState(
                lyrics = peekLyrics,
                currentPositionFlow = positionFlow,
                positionReader = positionReader,
                playbackSpeed = playbackSpeed,
                isPlaying = isPlaying,
                enabled = LocalPlayerPositionActive.current && hasTiming,
                songId = songId,
                timingOffsetMs = timingOffsetMs,
                isCasting = isCasting,
                finalLineDurationMs = LyricsUtils.FINAL_DYNAMIC_LINE_DURATION_MS,
            )
        // The line clock above only ticks at line boundaries when lyrics have no word timing, which froze the gap ring
        // and let it overshoot its end. Like the full lyrics view, gaps get their own smooth clock with the same offset.
        val smoothPosition =
            rememberSmoothLyricsPositionState(
                positionFlow = positionFlow,
                playbackSpeed = playbackSpeed,
                positionReader = positionReader,
                isPlaying = isPlaying,
                songId = songId,
                isCasting = isCasting,
                enabled = LocalPlayerPositionActive.current && hasTiming,
            )
        val offsetMs = LyricsUtils.combinedLyricsOffsetMs(timingOffsetMs, Settings.shared.globalLyricsOffsetMs)
        val gapPosition =
            remember(smoothPosition, offsetMs) {
                derivedStateOf { LyricsUtils.lyricsPositionForOffset(smoothPosition.longValue, offsetMs) }
            }
        val gaps = remember(peekLyrics) { buildLyricsListItems(peekLyrics).filterIsInstance<LyricsListItem.Indicator>() }
        val content by remember(peekLyrics, isLoading, hasTiming, gaps, positionState, gapPosition) {
            derivedStateOf(structuralEqualityPolicy()) {
                when {
                    peekLyrics.isEmpty() && isLoading -> {
                        PeekContent.Loading
                    }

                    peekLyrics.isEmpty() -> {
                        PeekContent.NotFound
                    }

                    !hasTiming -> {
                        PeekContent.PlainOnly
                    }

                    else -> {
                        val gapNow = gapPosition.value
                        // Start the ring as soon as the line before the gap ends, so the next line never shows first.
                        gaps
                            .firstOrNull { gapNow in (it.gapStartMs - LYRICS_GAP_START_LAG_MS) until it.gapEndMs }
                            ?.let(PeekContent::Gap)
                            ?: peekLine(peekLyrics, peekLineIndex(peekLyrics, positionState.value))
                    }
                }
            }
        }
        val color = resolveLyricsTextColor(textColor)
        val adlibs = remember(peekLyrics) { peekAdlibs(peekLyrics) }
        val textSizeSp = if (compact) PEEK_COMPACT_TEXT_SIZE_SP else PEEK_TEXT_SIZE_SP
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) PeekCompactMinHeight else PeekMinHeight),
            contentAlignment = Alignment.Center,
        ) {
            PeekAdlibLayer(
                adlibs = adlibs,
                position = { positionState.value },
                isPlaying = isPlaying,
                color = color,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            AnimatedContent(
                targetState = content,
                // No clipping: adlib call-outs stand out of the line's bounds.
                transitionSpec = { (fadeIn(tween(400)) togetherWith fadeOut(tween(400))).using(SizeTransform(clip = false)) },
                contentAlignment = Alignment.Center,
                label = "peekLyricsLine",
            ) { shown ->
                when (shown) {
                    PeekContent.Loading -> {
                        CircularProgressIndicator(color = color, modifier = Modifier.size(28.dp))
                    }

                    PeekContent.NotFound -> {
                        PeekMissingLyrics(color = color, onReload = onReload, onSearchLyrics = onSearchLyrics)
                    }

                    PeekContent.PlainOnly -> {
                        Text(
                            text = stringResource(Res.string.peek_plain_lyrics_hint),
                            color = color,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable(onClick = onShowLyrics).padding(12.dp),
                        )
                    }

                    is PeekContent.Gap -> {
                        IntervalIndicator(
                            gapStartMs = shown.item.gapStartMs,
                            gapEndMs = shown.item.gapEndMs,
                            currentPositionMs = { gapPosition.value.coerceIn(shown.item.gapStartMs, shown.item.gapEndMs) },
                            visible = true,
                            isPlaying = isPlaying,
                            color = color,
                            modifier = Modifier.clickable(onClick = onShowLyrics),
                        )
                    }

                    is PeekContent.Line -> {
                        PeekLine(
                            line = shown,
                            position = { positionState.value },
                            isPlaying = isPlaying,
                            color = color,
                            textSizeSp = textSizeSp,
                            onShowLyrics = onShowLyrics,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Peek for previews without a player (onboarding): the same line, adlibs and gap ring as [PeekLyricsLine], driven by
 * [position]. Shown only while the Peek setting is on, so switching it shows or hides the line like in the player.
 */
@Composable
internal fun PeekPreviewLine(
    lyrics: List<LyricsEntry>,
    position: () -> Long,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = Settings.shared.showLyricsOnPlayer,
        enter = expandVertically(clip = false) + fadeIn(),
        exit = shrinkVertically(clip = false) + fadeOut(),
        modifier = modifier,
    ) {
        // Background-vocal lines belong to the line before them; on their own they would replace it in the Peek.
        val peekLyrics = remember(lyrics) { mergeBackgroundLines(lyrics) }
        val gaps = remember(peekLyrics) { buildLyricsListItems(peekLyrics).filterIsInstance<LyricsListItem.Indicator>() }
        val content by remember(peekLyrics, gaps) {
            derivedStateOf(structuralEqualityPolicy()) {
                if (peekLyrics.isEmpty()) {
                    PeekContent.Loading
                } else {
                    val now = position()
                    gaps
                        .firstOrNull { now in (it.gapStartMs - LYRICS_GAP_START_LAG_MS) until it.gapEndMs }
                        ?.let(PeekContent::Gap)
                        ?: peekLine(peekLyrics, peekLineIndex(peekLyrics, now))
                }
            }
        }
        val color = resolveLyricsTextColor(textColor)
        val adlibs = remember(peekLyrics) { peekAdlibs(peekLyrics) }
        Box(Modifier.fillMaxWidth().heightIn(min = PeekMinHeight), contentAlignment = Alignment.Center) {
            PeekAdlibLayer(
                adlibs = adlibs,
                position = position,
                isPlaying = true,
                color = color,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            AnimatedContent(
                targetState = content,
                // No clipping: adlib call-outs stand out of the line's bounds.
                transitionSpec = { (fadeIn(tween(400)) togetherWith fadeOut(tween(400))).using(SizeTransform(clip = false)) },
                contentAlignment = Alignment.Center,
                label = "peekPreviewLine",
            ) { shown ->
                when (shown) {
                    is PeekContent.Gap -> {
                        IntervalIndicator(
                            gapStartMs = shown.item.gapStartMs,
                            gapEndMs = shown.item.gapEndMs,
                            currentPositionMs = { position().coerceIn(shown.item.gapStartMs, shown.item.gapEndMs) },
                            visible = true,
                            isPlaying = true,
                            color = color,
                        )
                    }

                    is PeekContent.Line -> {
                        PeekLine(
                            line = shown,
                            position = position,
                            isPlaying = true,
                            color = color,
                            textSizeSp = PEEK_TEXT_SIZE_SP,
                            onShowLyrics = {},
                        )
                    }

                    else -> {
                        CircularProgressIndicator(color = color, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekLine(
    line: PeekContent.Line,
    position: () -> Long,
    isPlaying: Boolean,
    color: Color,
    textSizeSp: Float,
    onShowLyrics: () -> Unit,
) {
    // Adlibs are drawn by PeekAdlibLayer, so they can outlast this line.
    val mainEntry = remember(line.entry) { splitAdlib(line.entry).first }
    val isActive by remember(line) { derivedStateOf { position() in line.entry.time..line.endTimeMs } }
    val textMeasurer = rememberTextMeasurer()
    Box(contentAlignment = Alignment.Center) {
        InlineLyricsLine(
            entry = mainEntry,
            isActive = isActive,
            distance = 0,
            currentPosition = position,
            adjustedEndTimeMs = line.endTimeMs,
            isAutoScrollEnabled = false,
            isLrcSynced = true,
            onSeek = onShowLyrics,
            alignment = TextAlign.Center,
            textSizeMultiplier = textSizeSp / Settings.shared.lyricsTextSize,
            textColor = color,
            textMeasurer = textMeasurer,
            isPlaying = isPlaying,
        )
    }
}

/** A background-vocal call-out and the Peek line it belongs to; it stays up past that line until it has been read. */
private class PeekAdlib(
    val lineIndex: Int,
    val entry: LyricsEntry,
    val endTimeMs: Long,
    val hideAtMs: Long,
)

private fun peekAdlibs(lyrics: List<LyricsEntry>): List<PeekAdlib> =
    lyrics.mapIndexedNotNull { index, line ->
        val adlib = splitAdlib(line).second ?: return@mapIndexedNotNull null
        val lineEnd = LyricsUtils.getLineEndTime(line, lyrics.getOrNull(index + 1), LyricsUtils.FINAL_DYNAMIC_LINE_DURATION_MS)
        // Without word timing an adlib shows briefly from its start, like the legacy Peek did.
        val end = adlib.endTime ?: minOf(adlib.time + UNTIMED_ADLIB_MS, lineEnd)
        // Sung adlibs are often under a second; keep them long enough to read, even after the next line starts.
        PeekAdlib(index, adlib, end, maxOf(end + ADLIB_HOLD_MS, adlib.time + ADLIB_MIN_VISIBLE_MS))
    }

/**
 * The adlib layer above the Peek line, as in the legacy Peek: a tilted, italic call-out that appears while it is sung,
 * alternating sides line by line. It is separate from the line so the next line can start without cutting it off.
 */
@Composable
private fun PeekAdlibLayer(
    adlibs: List<PeekAdlib>,
    position: () -> Long,
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (adlibs.isEmpty()) return
    // Adlibs are composed a little before they are sung: the word animation prepares its glyph masks on first use, and
    // until then only the sung part was drawn, so the call-out popped in a few hundred milliseconds late.
    val composed by remember(adlibs) {
        derivedStateOf(structuralEqualityPolicy()) {
            val now = position()
            adlibs.filter { now in (it.entry.time - ADLIB_PREPARE_MS)..(it.hideAtMs + ADLIB_ANIMATION_MS) }
        }
    }
    val textMeasurer = rememberTextMeasurer()
    Box(modifier.widthIn(max = PeekAdlibAreaWidth).fillMaxWidth()) {
        composed.forEach { adlib ->
            key(adlib.lineIndex) {
                val shown by remember(adlib) { derivedStateOf { position() in adlib.entry.time..adlib.hideAtMs } }
                val progress by animateFloatAsState(
                    targetValue = if (shown) 1f else 0f,
                    animationSpec = tween(ADLIB_ANIMATION_MS.toInt()),
                    label = "peekAdlib",
                )
                val isLeft = adlib.lineIndex % 2 == 0
                Box(
                    Modifier
                        .align(if (isLeft) Alignment.TopStart else Alignment.TopEnd)
                        .offset(x = if (isLeft) 24.dp else (-24).dp, y = (-20).dp)
                        .graphicsLayer {
                            // As in the legacy Peek: grows in from half size, rising slightly, tilted to its side.
                            alpha = progress
                            scaleX = 0.5f + 0.5f * progress
                            scaleY = scaleX
                            translationY = (1f - progress) * size.height / 2f
                            rotationZ = if (isLeft) -6f else 6f
                        },
                ) {
                    ActiveLyricsLineContent(
                        entry = adlib.entry,
                        isActive = true,
                        positionProvider = position,
                        lineEndTimeMs = adlib.endTimeMs,
                        isPlaying = isPlaying,
                        textColor = color,
                        lyricStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = PEEK_ADLIB_TEXT_SIZE_SP.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                        textMeasurer = textMeasurer,
                        alignment = if (isLeft) TextAlign.Start else TextAlign.End,
                        maxLines = 2,
                        fillWidth = false,
                        useFixedTone = false,
                    )
                }
            }
        }
    }
}

/**
 * Folds background-vocal lines (whole entries marked [LyricsEntry.isBackground]) into the line before them, so the Peek
 * keeps showing the main line and [splitAdlib] turns them into call-outs. With word timing on the main line they become
 * background words; otherwise they become its background text.
 */
internal fun mergeBackgroundLines(lyrics: List<LyricsEntry>): List<LyricsEntry> {
    if (lyrics.none { it.isBackground }) return lyrics
    val merged = ArrayList<LyricsEntry>(lyrics.size)
    for (entry in lyrics) {
        val main = merged.lastOrNull()
        if (!entry.isBackground || main == null || main.isBackground) {
            merged += entry
            continue
        }
        val mainWords = main.words?.takeIf { it.isNotEmpty() }
        val adlibWords =
            entry.words?.takeIf { it.isNotEmpty() }?.map { it.copy(isBackground = true) }
                ?: listOf(
                    WordTimestamp(
                        text = entry.text,
                        startTime = entry.time / 1000.0,
                        endTime = (entry.endTime ?: (entry.time + UNTIMED_ADLIB_MS)) / 1000.0,
                        isBackground = true,
                    ),
                )
        merged[merged.lastIndex] =
            main.copy(
                words = mainWords?.plus(adlibWords),
                backgroundText = listOfNotNull(main.backgroundText, entry.text.takeIf { it.isNotBlank() }).joinToString(" "),
                backgroundTranslatedText = main.backgroundTranslatedText ?: entry.translatedText,
            )
    }
    return merged
}

/** Splits background vocals out of a line so the Peek can show them as call-outs instead of below the line. */
internal fun splitAdlib(entry: LyricsEntry): Pair<LyricsEntry, LyricsEntry?> {
    val backgroundWords = entry.words?.filter { it.isBackground }.orEmpty()
    val backgroundText =
        entry.backgroundText?.takeIf { it.isNotBlank() }
            ?: backgroundWords.joinToString("") { it.text + if (it.hasTrailingSpace) " " else "" }.trim().takeIf { it.isNotBlank() }
            ?: return entry to null
    val main =
        entry.copy(
            words = entry.words?.filterNot { it.isBackground },
            romanizedWords = entry.romanizedWords?.filterNot { it.isBackground },
            backgroundText = null,
            backgroundTranslatedText = null,
        )
    val adlib =
        LyricsEntry(
            time = backgroundWords.firstOrNull()?.let { (it.startTime * 1000).toLong() } ?: entry.time,
            text = backgroundText,
            words = backgroundWords.map { it.copy(isBackground = false) }.takeIf { it.isNotEmpty() },
            endTime = backgroundWords.lastOrNull()?.let { (it.endTime * 1000).toLong() },
        )
    return main to adlib
}

/**
 * Whether the displayed lyrics follow what is playing. Music videos often add intros, pauses or scenes, so studio
 * timings only fit a video once upstream's retimer has aligned them; otherwise the Peek stays hidden.
 */
internal fun peekLyricsMatchPlayback(uiState: PlayerViewModel.PlayerUiState): Boolean =
    uiState.mvLyricsRetimed || !(uiState.isVideoModeActive || uiState.currentSong?.isVideoSong == true)

/** Opens the lyrics search straight away: title and artist, then the provider results. */
@Composable
private fun PeekLyricsSearch(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val song = uiState.currentSong ?: return onDismiss()
    val songArtist = song.artists.joinToString { it.name }
    var showingResults by remember { mutableStateOf(false) }
    if (!showingResults) {
        LyricsSearchDialog(
            initialTitle = uiState.lyricsQueryTitle?.takeIf { it.isNotBlank() } ?: song.title,
            initialArtist = uiState.lyricsQueryArtist?.takeIf { it.isNotBlank() } ?: songArtist,
            onDismiss = onDismiss,
            onSearch = { title, artist ->
                viewModel.loadLyricsProviderResults(
                    songId = song.id,
                    title = title,
                    artist = artist,
                    duration = song.duration,
                    album = song.album,
                    isCustomQuery = LyricsUtils.isCustomLyricsQuery(title, artist, song.title, songArtist),
                )
                showingResults = true
            },
        )
    } else {
        LyricsProviderDialog(
            search = uiState.lyricsProviderSearch,
            noResultsText = stringResource(Res.string.no_provider_results_found),
            onDismiss = onDismiss,
            onSelect = { result ->
                viewModel.selectLyricsProviderResult(song.id, result)
                onDismiss()
            },
        )
    }
}

private fun peekLine(
    lyrics: List<LyricsEntry>,
    index: Int,
): PeekContent.Line =
    PeekContent.Line(
        index = index,
        entry = lyrics[index],
        endTimeMs = LyricsUtils.getLineEndTime(lyrics[index], lyrics.getOrNull(index + 1), LyricsUtils.FINAL_DYNAMIC_LINE_DURATION_MS),
    )

/** The current line, or the upcoming one once the current line has ended, so a finished line never flashes back. */
internal fun peekLineIndex(
    lyrics: List<LyricsEntry>,
    position: Long,
): Int {
    val current = LyricsUtils.findCurrentLineIndex(lyrics, position)
    if (current < 0) return 0
    val next = lyrics.getOrNull(current + 1) ?: return current
    val ended = position > LyricsUtils.getLineEndTime(lyrics[current], next, LyricsUtils.FINAL_DYNAMIC_LINE_DURATION_MS)
    return if (ended) current + 1 else current
}

@Composable
private fun PeekMissingLyrics(
    color: Color,
    onReload: () -> Unit,
    onSearchLyrics: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.lyrics_not_found),
            color = color,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onReload) { Text(stringResource(Res.string.retry), color = color) }
            TextButton(onClick = onSearchLyrics) { Text(stringResource(Res.string.search_lyrics), color = color) }
        }
    }
}
