package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import java.util.concurrent.TimeUnit

@Composable
fun BlockArtistDialog(
    artistName: String,
    isBlocked: Boolean = false,
    onDismiss: () -> Unit,
    onUnblock: (() -> Unit)? = null,
    onBlock: (Long) -> Unit // returns expiry timestamp, 0 for forever
) {
    val currentTime = System.currentTimeMillis()
    
    if (isBlocked) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = {
                Text(text = "Künstler entblocken")
            },
            text = {
                Text(text = "Der Künstler '$artistName' ist aktuell blockiert. Möchtest du die Blockierung aufheben?", style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = {
                    onUnblock?.invoke()
                    onDismiss()
                }) {
                    Text(text = "Entblocken")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
        return
    }

    android.util.Log.e("BlockArtistDialog", "Dialog is rendering! artistName=$artistName")
    val options = listOf(
        stringResource(R.string.block_1_hour) to currentTime + TimeUnit.HOURS.toMillis(1),
        stringResource(R.string.block_8_hours) to currentTime + TimeUnit.HOURS.toMillis(8),
        stringResource(R.string.block_1_day) to currentTime + TimeUnit.DAYS.toMillis(1),
        stringResource(R.string.block_forever) to 0L
    )

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.block_artist))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.block_artist_duration, artistName),
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                options.forEach { (label, expiry) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBlock(expiry) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}
