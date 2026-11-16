package com.metrolist.android.ui.download

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metrolist.android.playback.DownloadQueueState
import com.metrolist.android.playback.DownloadRequest
import com.metrolist.android.playback.SongDownloadManager
import com.metrolist.android.playback.isRunnable
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.peek_download_cancel_confirm
import com.metrolist.shared.generated.resources.peek_download_cancel_keep
import com.metrolist.shared.generated.resources.peek_download_cancel_message
import com.metrolist.shared.generated.resources.peek_download_cancel_title
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.peek.PeekHeaderDownload
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * PeekMusic: state for the album/playlist header download button. While songs of the collection are queued or
 * running it shows a progress ring, and a tap asks to stop those downloads (finished songs stay). Otherwise a tap
 * runs [onClick] (download all, or remove the downloads when [isDownloaded]).
 */
@Composable
internal fun rememberPeekHeaderDownload(
    isDownloaded: Boolean,
    onClick: () -> Unit,
    songs: List<SongItem>,
    downloadedIds: Set<String>,
): PeekHeaderDownload {
    val downloadManager: SongDownloadManager = koinInject()
    val requests by downloadManager.requests.collectAsState()
    val songIds = remember(songs) { songs.mapTo(mutableSetOf()) { it.id } }
    val active =
        remember(songIds, downloadedIds, requests) {
            requests.filter { it.state.isRunnable && it.song.id in songIds && it.song.id !in downloadedIds }
        }
    val progress = remember(songIds, downloadedIds, active) { peekCollectionDownloadProgress(songIds, downloadedIds, active) }
    var confirmCancel by remember { mutableStateOf(false) }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(Res.string.peek_download_cancel_title)) },
            text = { Text(stringResource(Res.string.peek_download_cancel_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancel = false
                        active.forEach { downloadManager.cancel(it.song) }
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

    return PeekHeaderDownload(
        isDownloaded = isDownloaded,
        onClick = { if (progress != null) confirmCancel = true else onClick() },
        progress = progress,
    )
}

/** Share of the collection that is downloaded, counting the running song partially; null when nothing is active. */
internal fun peekCollectionDownloadProgress(
    songIds: Set<String>,
    downloadedIds: Set<String>,
    active: List<DownloadRequest>,
): Float? {
    if (songIds.isEmpty() || active.isEmpty()) return null
    val done = songIds.count { it in downloadedIds }
    val running =
        active.sumOf { request ->
            val total = request.totalBytes ?: 0L
            if (request.state == DownloadQueueState.RUNNING && total > 0L) {
                (request.downloadedBytes.toDouble() / total).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
        }
    return ((done + running) / (done + active.size)).toFloat().coerceIn(0f, 1f)
}
