@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.peek

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_download
import com.metrolist.shared.generated.resources.remove_download
import com.metrolist.shared.ui.components.withStaticCorners
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.tv.tvFocusHighlight
import org.jetbrains.compose.resources.stringResource

/**
 * Download button in the album/playlist header, next to the like button.
 * PeekMusic moves the collection download out of the ⋮ menu into the header.
 * [onClick] downloads everything, or asks to remove the downloads when [isDownloaded].
 */
data class PeekHeaderDownload(
    val isDownloaded: Boolean,
    val onClick: () -> Unit,
    /** 0..1 while songs of this collection are queued or downloading, else null. */
    val progress: Float? = null,
)

@Composable
internal fun PeekHeaderDownloadButton(
    download: PeekHeaderDownload,
    buttonHeight: Dp,
) {
    ToggleButton(
        checked = download.isDownloaded,
        onCheckedChange = { download.onClick() },
        modifier = Modifier.height(buttonHeight).width(48.dp).tvFocusHighlight(CircleShape),
        shapes = ToggleButtonDefaults.shapesFor(buttonHeight).withStaticCorners(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val progress = download.progress
            if (progress != null && !download.isDownloaded) {
                val animated by animateFloatAsState(progress, label = "peekHeaderDownloadProgress")
                CircularProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.5.dp,
                    color = LocalContentColor.current,
                    trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                    gapSize = 0.dp,
                )
            }
            Icon(
                if (download.isDownloaded) MetrolistIcons.Outlined.OfflinePin else MetrolistIcons.Outlined.Download,
                stringResource(if (download.isDownloaded) Res.string.remove_download else Res.string.action_download),
                Modifier.size(if (progress != null && !download.isDownloaded) 16.dp else 20.dp),
            )
        }
    }
}
