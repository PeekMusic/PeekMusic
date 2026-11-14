/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.lyrics.ActiveLyricsLineContent
import com.metrolist.shared.ui.components.lyrics.InlineLyricsLine
import com.metrolist.shared.ui.components.lyrics.IntervalIndicator
import com.metrolist.shared.ui.components.lyrics.LYRICS_GAP_START_LAG_MS
import com.metrolist.shared.ui.components.lyrics.LyricsListItem
import com.metrolist.shared.ui.components.lyrics.buildLyricsListItems
import com.metrolist.shared.ui.components.lyrics.rememberCaptionLyricsPositionState
import com.metrolist.shared.ui.components.lyrics.resolveLyricsTextColor
import com.metrolist.shared.ui.menu.LyricsProviderDialog
import com.metrolist.shared.ui.menu.LyricsSearchDialog
import com.metrolist.shared.ui.player.LocalPlayerPositionActive
import com.metrolist.shared.viewmodels.PlayerViewModel
import org.jetbrains.compose.resources.stringResource

/** Text sizes of the Peek line: as in the legacy PeekMusic player, smaller where vertical space is tight. */
private const val PEEK_TEXT_SIZE_SP = 24f
private const val PEEK_COMPACT_TEXT_SIZE_SP = 20f
private const val PEEK_ADLIB_TEXT_SIZE_SP = 15f
private const val UNTIMED_ADLIB_MS = 3_500L

/** Fixed minimum heights keep the artwork and controls still while the Peek switches between lines, gaps and states. */
private val PeekMinHeight = 80.dp
private val PeekCompactMinHeight = 64.dp

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

    AnimatedVisibility(
        visible = Settings.shared.showLyricsOnPlayer && matchesPlayback,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val hasTiming = remember(lyrics) { LyricsUtils.hasPlaybackTiming(lyrics) }
        val positionState =
            rememberCaptionLyricsPositionState(
                lyrics = lyrics,
                currentPositionFlow = viewModel.currentPosition,
                positionReader = viewModel::currentPositionMs,
                playbackSpeed = viewModel.playbackSpeed.collectAsState().value,
                isPlaying = isPlaying,
                enabled = LocalPlayerPositionActive.current && hasTiming,
                songId = songId,
                timingOffsetMs = timingOffsetMs,
                isCasting = isCasting,
                finalLineDurationMs = LyricsUtils.FINAL_DYNAMIC_LINE_DURATION_MS,
            )
        val gaps = remember(lyrics) { buildLyricsListItems(lyrics).filterIsInstance<LyricsListItem.Indicator>() }
        val content by remember(lyrics, isLoading, hasTiming, gaps, positionState) {
            derivedStateOf(structuralEqualityPolicy()) {
                when {
                    lyrics.isEmpty() && isLoading -> {
                        PeekContent.Loading
                    }

                    lyrics.isEmpty() -> {
                        PeekContent.NotFound
                    }

                    !hasTiming -> {
                        PeekContent.PlainOnly
                    }

                    else -> {
                        val position = positionState.value
                        // Start the ring as soon as the line before the gap ends, so the next line never shows first.
                        gaps
                            .firstOrNull { position in (it.gapStartMs - LYRICS_GAP_START_LAG_MS) until it.gapEndMs }
                            ?.let(PeekContent::Gap)
                            ?: peekLine(lyrics, peekLineIndex(lyrics, position))
                    }
                }
            }
        }
        val color = resolveLyricsTextColor(textColor)
        val textSizeSp = if (compact) PEEK_COMPACT_TEXT_SIZE_SP else PEEK_TEXT_SIZE_SP
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) PeekCompactMinHeight else PeekMinHeight),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = content,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                contentAlignment = Alignment.Center,
                label = "peekLyricsLine",
            ) { shown ->
                when (shown) {
                    PeekContent.Loading -> {
                        CircularProgressIndicator(color = color, modifier = Modifier.size(28.dp))
                    }

                    PeekContent.NotFound -> {
                        PeekMissingLyrics(color = color, onReload = onReload, onSearchLyrics = { searching = true })
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
                            currentPositionMs = { positionState.value.coerceIn(shown.item.gapStartMs, shown.item.gapEndMs) },
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

@Composable
private fun PeekLine(
    line: PeekContent.Line,
    position: () -> Long,
    isPlaying: Boolean,
    color: Color,
    textSizeSp: Float,
    onShowLyrics: () -> Unit,
) {
    val (mainEntry, adlib) = remember(line.entry) { splitAdlib(line.entry) }
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
        if (adlib != null) {
            // Without word timing an adlib shows briefly from its start, like the legacy Peek did.
            val adlibEnd = adlib.endTime ?: minOf(adlib.time + UNTIMED_ADLIB_MS, line.endTimeMs)
            val adlibSung by remember(adlib, adlibEnd) { derivedStateOf { position() in adlib.time..adlibEnd } }
            val isLeft = line.index % 2 == 0
            // As in the legacy Peek: a tilted, italic call-out above the line that only appears while it is sung.
            AnimatedVisibility(
                visible = adlibSung,
                enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f) + slideInVertically { it / 2 },
                exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.8f) + slideOutVertically { it / 2 },
                modifier =
                    Modifier
                        .align(if (isLeft) Alignment.TopStart else Alignment.TopEnd)
                        .offset(x = if (isLeft) 24.dp else (-24).dp, y = (-20).dp)
                        .graphicsLayer { rotationZ = if (isLeft) -6f else 6f },
            ) {
                ActiveLyricsLineContent(
                    entry = adlib,
                    isActive = true,
                    positionProvider = position,
                    lineEndTimeMs = adlibEnd,
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
