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
import com.metrolist.music.constants.PeekShowRomanizationKey
import com.metrolist.music.constants.PeekShowTranslationKey
import com.metrolist.music.constants.ShowPlayerLyricsPeekKey
import com.metrolist.music.constants.UseNewMiniPlayerDesignKey
import com.metrolist.music.constants.UseNewPlayerDesignKey
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.screens.onboarding.components.PlayerDesignMock
import com.metrolist.music.utils.rememberPreference

@Composable
fun PlayerPage(modifier: Modifier = Modifier) {
    val (useNewPlayerDesign, onUseNewPlayerDesignChangeRaw) = rememberPreference(UseNewPlayerDesignKey, true)
    val (_, onUseNewMiniPlayerDesignChange) = rememberPreference(UseNewMiniPlayerDesignKey, true)
    
    // One toggle for both designs
    val onUseNewDesignChange: (Boolean) -> Unit = { enabled ->
        onUseNewPlayerDesignChangeRaw(enabled)
        onUseNewMiniPlayerDesignChange(enabled)
    }
    
    val (peekEnabled, onPeekEnabledChange) = rememberPreference(ShowPlayerLyricsPeekKey, true)
    val (showRomanization, onShowRomanizationChange) = rememberPreference(PeekShowRomanizationKey, true)
    val (showTranslation, onShowTranslationChange) = rememberPreference(PeekShowTranslationKey, true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.onboarding_player_design_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        PlayerDesignMock(
            useNewDesign = useNewPlayerDesign,
            showPeekLyrics = peekEnabled,
            showRomanization = showRomanization,
            showTranslation = showTranslation,
            modifier = Modifier.fillMaxWidth(),
        )

        Material3SettingsGroup(
            items = buildList {
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.onboarding_new_player_design)) },
                        trailingContent = {
                            Switch(
                                checked = useNewPlayerDesign,
                                onCheckedChange = onUseNewDesignChange,
                            )
                        },
                        onClick = { onUseNewDesignChange(!useNewPlayerDesign) },
                    )
                )
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.onboarding_peek_toggle)) },
                        description = { Text(stringResource(R.string.onboarding_peek_toggle_desc)) },
                        trailingContent = {
                            Switch(
                                checked = peekEnabled,
                                onCheckedChange = onPeekEnabledChange,
                            )
                        },
                        onClick = { onPeekEnabledChange(!peekEnabled) },
                    )
                )
                if (peekEnabled) {
                    add(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.onboarding_romanization_toggle)) },
                            trailingContent = {
                                Switch(
                                    checked = showRomanization,
                                    onCheckedChange = onShowRomanizationChange,
                                )
                            },
                            onClick = { onShowRomanizationChange(!showRomanization) },
                        )
                    )
                    add(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.onboarding_translation_toggle)) },
                            trailingContent = {
                                Switch(
                                    checked = showTranslation,
                                    onCheckedChange = onShowTranslationChange,
                                )
                            },
                            onClick = { onShowTranslationChange(!showTranslation) },
                        )
                    )
                }
            },
        )
    }
}
