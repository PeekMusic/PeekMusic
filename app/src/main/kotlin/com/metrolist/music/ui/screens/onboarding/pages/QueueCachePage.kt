/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.onboarding.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.ExcludeRecentlyPlayedFromQueueKey
import com.metrolist.music.constants.PreloadQueueCountKey
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.screens.onboarding.components.PreloadQueueIllustration
import com.metrolist.music.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun QueueCachePage(modifier: Modifier = Modifier) {
    val (preloadCount, onPreloadCountChange) = rememberPreference(PreloadQueueCountKey, 1)
    val (excludeRecentlyPlayed, onExcludeRecentlyPlayedChange) = rememberPreference(
        ExcludeRecentlyPlayedFromQueueKey,
        true,
    )
    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(
        com.metrolist.music.constants.MaxSongCacheSizeKey,
        1024
    )
    val songCacheValues = androidx.compose.runtime.remember { listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1) }


    val (enableSongCache) = rememberPreference(
        com.metrolist.music.constants.EnableSongCacheKey,
        true
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.onboarding_queue_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (enableSongCache && maxSongCacheSize != 0) {
            PreloadQueueIllustration(
                preloadCount = preloadCount,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_queue_preload_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = preloadCount > 0,
                        onCheckedChange = { checked ->
                            onPreloadCountChange(if (checked) 1 else 0)
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.onboarding_queue_preload_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                if (preloadCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = preloadCount.toFloat(),
                        onValueChange = { onPreloadCountChange(it.roundToInt()) },
                        valueRange = 1f..8f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.onboarding_queue_preload_count,
                            preloadCount,
                            preloadCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.onboarding_queue_cache_size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.onboarding_queue_cache_size_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = songCacheValues.indexOf(maxSongCacheSize).toFloat(),
                onValueChange = {
                    val newValue = songCacheValues[it.roundToInt()]
                    onMaxSongCacheSizeChange(newValue)
                },
                valueRange = 0f..(songCacheValues.size - 1).toFloat(),
                steps = songCacheValues.size - 2,
                modifier = Modifier.fillMaxWidth(),
            )
            val cacheText = when (maxSongCacheSize) {
                0 -> stringResource(R.string.disable)
                -1 -> stringResource(R.string.unlimited)
                else -> if (maxSongCacheSize >= 1024) "${maxSongCacheSize / 1024} GB" else "$maxSongCacheSize MB"
            }
            Text(
                text = cacheText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.onboarding_queue_exclude_recent)) },
                    description = { Text(stringResource(R.string.onboarding_queue_exclude_recent_desc)) },
                    trailingContent = {
                        Switch(
                            checked = excludeRecentlyPlayed,
                            onCheckedChange = onExcludeRecentlyPlayedChange,
                        )
                    },
                    onClick = { onExcludeRecentlyPlayedChange(!excludeRecentlyPlayed) },
                ),
            ),
        )
    }
}
