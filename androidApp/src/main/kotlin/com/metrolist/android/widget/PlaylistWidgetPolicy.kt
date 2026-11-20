/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.widget

internal const val PLAYLIST_WIDGET_COMPACT_MAX_WIDTH_DP = 340

internal fun playlistWidgetQuickPickCapacity(
    minWidth: Int,
    minHeight: Int,
): Int =
    when {
        // PeekMusic: narrow widgets show a 2x2 grid of playlists.
        minWidth < PLAYLIST_WIDGET_COMPACT_MAX_WIDTH_DP -> 4

        minHeight < 330 -> 4

        minWidth >= 360 -> 8

        else -> 4
    }

internal fun playlistWidgetDisplayCount(
    available: Int,
    capacity: Int,
): Int {
    val capped = minOf(available, capacity, 8)
    return when {
        capped >= 8 -> 8
        capped >= 4 -> 4
        else -> capped
    }
}

internal fun needsTopWidgetSong(
    precedingPicks: Int,
    capacity: Int,
): Boolean = precedingPicks < playlistWidgetDisplayCount(precedingPicks + 1, capacity)

// A Speed Dial pin may use YouTube's VL browse wrapper while a saved playlist uses its unwrapped ID.
internal fun playlistWidgetPickKey(
    key: String,
    remoteId: String?,
): String = remoteId?.let { "online:${it.removePrefix("VL")}" } ?: key
