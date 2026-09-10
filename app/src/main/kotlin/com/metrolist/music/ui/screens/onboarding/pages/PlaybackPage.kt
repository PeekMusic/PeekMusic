/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.onboarding.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.AutoDownloadOnLikeKey
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.CrossfadeEnabledKey
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.PlayingIndicator
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

@Composable
fun PlaybackPage(modifier: Modifier = Modifier) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(AudioQualityKey, AudioQuality.AUTO)
    val showAudioQualityDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val (crossfade, onCrossfadeChange) = rememberPreference(CrossfadeEnabledKey, false)
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        com.metrolist.music.constants.CrossfadeDurationKey,
        5f
    )
    val (autoRadioQueue, onAutoRadioQueueChange) = rememberPreference(AutoRadioQueueKey, true)
    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(AutoDownloadOnLikeKey, false)

    if (showAudioQualityDialog.value) {
        com.metrolist.music.ui.component.EnumDialog(
            onDismiss = { showAudioQualityDialog.value = false },
            onSelect = {
                onAudioQualityChange(it)
                showAudioQualityDialog.value = false
            },
            title = stringResource(R.string.onboarding_audio_quality),
            current = audioQuality,
            values = AudioQuality.entries.toList(),
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.onboarding_playback_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        PlayingIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(48.dp),
        )

        Material3SettingsGroup(
            items = buildList {
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.onboarding_audio_quality)) },
                        description = { Text(stringResource(R.string.onboarding_audio_quality_desc)) },
                        trailingContent = {
                            Text(
                                text = when (audioQuality) {
                                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { showAudioQualityDialog.value = true },
                    )
                )
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.onboarding_crossfade)) },
                        description = { Text(stringResource(R.string.onboarding_crossfade_desc)) },
                        trailingContent = {
                            Switch(
                                checked = crossfade,
                                onCheckedChange = onCrossfadeChange,
                            )
                        },
                        onClick = { onCrossfadeChange(!crossfade) },
                    )
                )
                
                if (crossfade) {
                    add(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.crossfade_duration)) },
                            description = {
                                Column {
                                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt()))
                                    androidx.compose.material3.Slider(
                                        value = crossfadeDuration,
                                        onValueChange = onCrossfadeDurationChange,
                                        valueRange = 1f..15f,
                                        steps = 14,
                                    )
                                }
                            },
                        )
                    )
                }

                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.onboarding_auto_radio_queue)) },
                        description = { Text(stringResource(R.string.onboarding_auto_radio_queue_desc)) },
                        trailingContent = {
                            Switch(
                                checked = autoRadioQueue,
                                onCheckedChange = onAutoRadioQueueChange,
                            )
                        },
                        onClick = { onAutoRadioQueueChange(!autoRadioQueue) },
                    )
                )
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.onboarding_auto_download_like)) },
                        description = { Text(stringResource(R.string.onboarding_auto_download_like_desc)) },
                        trailingContent = {
                            Switch(
                                checked = autoDownloadOnLike,
                                onCheckedChange = onAutoDownloadOnLikeChange,
                            )
                        },
                        onClick = { onAutoDownloadOnLikeChange(!autoDownloadOnLike) },
                    )
                )
            }
        )
    }
}
