/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.peek

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.peek_download_cancel_confirm
import com.metrolist.shared.generated.resources.peek_download_cancel_keep
import com.metrolist.shared.generated.resources.peek_download_cancel_message
import com.metrolist.shared.generated.resources.peek_download_cancel_title
import com.metrolist.shared.generated.resources.remove
import com.metrolist.shared.generated.resources.stored_remove_selected_message
import com.metrolist.shared.generated.resources.stored_remove_selected_title
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.ui.components.AlertDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * PeekMusic: queued and running downloads (song id to share done, 0..1) and how to stop them. iOS provides it from its
 * download queue; without it the mobile header button shows no progress ring.
 */
class PeekActiveDownloads(
    val progress: Map<String, Float>,
    val cancel: (List<String>) -> Unit,
)

val LocalPeekActiveDownloads = staticCompositionLocalOf<PeekActiveDownloads?> { null }

/**
 * The album/playlist header download button for the shared mobile UI (iOS). While songs of the collection are queued
 * or downloading it shows a progress ring like Android, and a tap asks to stop them (finished songs stay). Otherwise a
 * tap downloads the missing songs, or asks before removing the downloads once every song is stored.
 */
@Composable
internal fun rememberPeekMobileHeaderDownload(
    songs: List<SongItem>,
    downloadedSongIds: Set<String>,
    onDownload: (List<SongItem>) -> Unit,
    onRemove: (List<String>) -> Unit,
): PeekHeaderDownload? {
    val storable = songs.filterNot { it.isLocal }
    var pendingRemoval by remember { mutableStateOf<List<String>?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    val activeDownloads = LocalPeekActiveDownloads.current
    val active =
        storable.mapNotNull { song ->
            song.id.takeIf { it !in downloadedSongIds }?.let { id -> activeDownloads?.progress?.get(id)?.let { id to it } }
        }
    val progress =
        if (active.isEmpty()) {
            null
        } else {
            ((storable.count { it.id in downloadedSongIds } + active.sumOf { it.second.toDouble() }) / storable.size)
                .toFloat()
                .coerceIn(0f, 1f)
        }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(Res.string.peek_download_cancel_title)) },
            text = { Text(stringResource(Res.string.peek_download_cancel_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancel = false
                        activeDownloads?.cancel(active.map { it.first })
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.peek_download_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.peek_download_cancel_keep))
                }
            },
        )
    }
    pendingRemoval?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(pluralStringResource(Res.plurals.stored_remove_selected_title, ids.size, ids.size)) },
            text = { Text(stringResource(Res.string.stored_remove_selected_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemove(ids)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
    if (storable.isEmpty()) return null
    val isDownloaded = storable.all { it.id in downloadedSongIds }
    return PeekHeaderDownload(
        isDownloaded = isDownloaded,
        progress = progress,
        onClick = {
            if (progress != null) {
                confirmCancel = true
            } else if (isDownloaded) {
                pendingRemoval = storable.map { it.id }
            } else {
                onDownload(storable.filter { it.id !in downloadedSongIds })
            }
        },
    )
}
