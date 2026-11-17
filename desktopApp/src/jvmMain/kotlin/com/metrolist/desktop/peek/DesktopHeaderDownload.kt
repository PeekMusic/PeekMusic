/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.desktop.peek

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metrolist.desktop.state.content.LibraryState
import com.metrolist.desktop.storage.DownloadManager
import com.metrolist.desktop.storage.DownloadState
import com.metrolist.desktop.storage.playlistDownloadProgress
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
import com.metrolist.shared.peek.PeekHeaderDownload
import com.metrolist.shared.ui.components.AlertDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The album/playlist header download button on desktop, the counterpart of Android's PeekCollectionDownloadProgress.
 * While songs of [songs] are queued or downloading it shows a progress ring and a tap asks to stop them (finished
 * songs stay). Otherwise a tap runs [onDownload], or asks before [onRemove] once every song is stored.
 * Returns null when nothing in [songs] can be downloaded (only local files).
 */
@Composable
internal fun rememberDesktopHeaderDownload(
    songs: List<SongItem>,
    onDownload: () -> Unit,
    onRemove: (List<SongItem>) -> Unit,
): PeekHeaderDownload? {
    val storable = songs.filterNot { it.isLocal }
    val states = LibraryState.downloadStates
    val active =
        storable.filter { states[it.id] == DownloadState.QUEUED || states[it.id] == DownloadState.DOWNLOADING }
    val isDownloaded = storable.isNotEmpty() && storable.all { states[it.id] == DownloadState.DOWNLOADED }
    val progress =
        if (active.isEmpty()) null else playlistDownloadProgress(storable, states, LibraryState.downloadProgress) / 100f

    var confirmCancel by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<List<SongItem>?>(null) }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(Res.string.peek_download_cancel_title)) },
            text = { Text(stringResource(Res.string.peek_download_cancel_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancel = false
                        DownloadManager.stopDownloads(active.map(SongItem::id))
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
    confirmRemove?.let { removal ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(pluralStringResource(Res.plurals.stored_remove_selected_title, removal.size, removal.size)) },
            text = { Text(stringResource(Res.string.stored_remove_selected_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        onRemove(removal)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (storable.isEmpty()) return null
    return PeekHeaderDownload(
        isDownloaded = isDownloaded,
        onClick = {
            when {
                progress != null -> confirmCancel = true
                isDownloaded -> confirmRemove = storable
                else -> onDownload()
            }
        },
        progress = progress,
    )
}
